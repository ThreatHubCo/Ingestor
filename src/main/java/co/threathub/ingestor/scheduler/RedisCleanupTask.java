package co.threathub.ingestor.scheduler;

import co.threathub.ingestor.Ingestor;
import co.threathub.ingestor.job.JobHelper;
import co.threathub.ingestor.log.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically checks for stale scan jobs in Redis and clears them.
 *
 * TODO: In the future, the way jobs are stored in Redis should be changed so that
 * they can auto-expire after a certain amount of time, making this class obselete.
 *
 */
public class RedisCleanupTask implements ITask {
    private final Ingestor ingestor;

    public RedisCleanupTask(Ingestor ingestor) {
        this.ingestor = ingestor;
    }

    public void schedule() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::execute, 0, 5, TimeUnit.MINUTES);
    }

    @Override
    public void execute() {
        this.run();
    }

    private void run() {
        Logger.info("Starting task");

        long start = System.currentTimeMillis();

        JobHelper.cleanupExpiredJobs(5);

        long end = System.currentTimeMillis();
        long duration = end - start;

        Logger.info(String.format("Finished task (Took %dms / %.2fs)", duration, duration / 1000f));
    }

}
