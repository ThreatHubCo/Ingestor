package co.threathub.ingestor.job;

import co.threathub.ingestor.job.model.NoOpScanJob;
import co.threathub.ingestor.job.model.ScanJob;
import co.threathub.ingestor.job.model.enums.ScanJobStatus;
import co.threathub.ingestor.util.JedisManager;
import co.threathub.ingestor.util.Utils;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
public class JobHelper {

    public static void addJob(ScanJob job) {
        String jobId = UUID.randomUUID().toString();
        job.setId(jobId);
        job.setCreatedAt(Instant.now().toString());
        String json = Utils.GSON.toJson(job);

        try (Jedis jedis = JedisManager.getConnection()) {
            jedis.hset("threathub:jobs:all", jobId, json);
            jedis.lpush("threathub:jobs:queue", jobId);
        }
        log.info("Created scan job {} ({})", jobId, job.getTargetType().name());
    }

    public static void updateJob(ScanJob job) {
        if (job instanceof NoOpScanJob) {
            return;
        }
        if (job.getId() == null) {
            log.warn("Cannot update job without ID");
            return;
        }

        // TODO: Save results to database
        if (job.getStatus() == ScanJobStatus.COMPLETE || job.getStatus() == ScanJobStatus.FAILED) {
            try (Jedis jedis = JedisManager.getConnection()) {
                jedis.hdel("threathub:jobs:all", job.getId());
                jedis.lrem("threathub:jobs:queue", 0, job.getId());
            }
            log.info("Removed job {} from Redis ({})", job.getId(), job.getType());
            return;
        }

        String json = Utils.GSON.toJson(job);

        try (Jedis jedis = JedisManager.getConnection()) {
            jedis.hset("threathub:jobs:all", job.getId(), json);
        }
    }

    public static Set<ScanJob> getJobs() {
        Set<ScanJob> jobs = new HashSet<>();

        try (Jedis jedis = JedisManager.getConnection()) {
            Map<String, String> allJobs = jedis.hgetAll("threathub:jobs:all");

            for (String jobJson : allJobs.values()) {
                ScanJob job = Utils.GSON.fromJson(jobJson, ScanJob.class);
                jobs.add(job);
            }
        } catch (Exception ex) {
            log.error("Failed to fetch jobs from Redis", ex);
        }

        return jobs;
    }

    public static String peekNextJobId() {
        try (Jedis jedis = JedisManager.getConnection()) {
            return jedis.lindex("threathub:jobs:queue", -1);
        }
    }

    public static String popNextJobId() {
        try (Jedis jedis = JedisManager.getConnection()) {
            return jedis.rpop("threathub:jobs:queue");
        }
    }

    public static void cleanupExpiredJobs(long maxAgeMinutes) {
        Instant cutoff = Instant.now().minusSeconds(maxAgeMinutes * 60);

        try (Jedis jedis = JedisManager.getConnection()) {
            Map<String, String> allJobs = jedis.hgetAll("threathub:jobs:all");

            for (Map.Entry<String, String> entry : allJobs.entrySet()) {
                String jobId = entry.getKey();
                ScanJob job = Utils.GSON.fromJson(entry.getValue(), ScanJob.class);

                if (job.getCreatedAt() == null) {
                    continue;
                }
                Instant createdAt = Instant.parse(job.getCreatedAt());

                if (createdAt.isBefore(cutoff)) {
                    jedis.hdel("threathub:jobs:all", jobId);
                    jedis.lrem("threathub:jobs:queue", 0, jobId);

                    log.warn("Removed expired scan job {} (created at {})", jobId, job.getCreatedAt());
                }
            }
        } catch (Exception ex) {
            log.error("Failed to cleanup expired jobs", ex);
        }
    }
}
