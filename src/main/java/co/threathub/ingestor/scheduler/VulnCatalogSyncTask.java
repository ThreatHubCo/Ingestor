package co.threathub.ingestor.scheduler;

import co.threathub.ingestor.Ingestor;
import co.threathub.ingestor.config.ConfigEntry;
import co.threathub.ingestor.config.ConfigKey;
import co.threathub.ingestor.config.ConfigRepository;
import co.threathub.ingestor.config.ConfigValueType;
import co.threathub.ingestor.defender.model.individual.DefenderVulnerability;
import co.threathub.ingestor.defender.service.VulnerabilityService;
import co.threathub.ingestor.job.model.NoOpScanJob;
import co.threathub.ingestor.job.model.ScanJob;
import co.threathub.ingestor.log.Logger;
import co.threathub.ingestor.model.VulnMeta;
import co.threathub.ingestor.repository.VulnerabilityRepository;
import co.threathub.ingestor.util.Utils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Periodically fetches every single vulnerability from the Defender API
 * and adds or updates it in the database.
 */
@Slf4j
public class VulnCatalogSyncTask implements ITask {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Ingestor ingestor;

    public VulnCatalogSyncTask(Ingestor ingestor) {
        this.ingestor = ingestor;
        this.schedule();
    }

    private void schedule() {
        long intialDelay = Utils.computeInitialDelay(2, 0); // 2am
        long period = TimeUnit.DAYS.toMillis(1); // 24 hours
        scheduler.scheduleAtFixedRate(this::execute, intialDelay, period, TimeUnit.MILLISECONDS);
    }

    @Override
    public void execute() {
        this.actuallyRun(new NoOpScanJob());
    }

    public void actuallyRun(ScanJob job) {
        Logger.info("Starting task");

        VulnerabilityService service = ingestor.getVulnerabilityService();
        VulnerabilityRepository repo = ingestor.getVulnerabilityRepository();

        try (Connection conn = ingestor.getDataSource().getConnection()) {
            conn.setAutoCommit(false);

            Object2IntMap<String> vulnCache = repo.loadAllCveIds(conn);
            Int2ObjectMap<VulnMeta> vulnMetaCache = repo.loadAllVulnMeta(conn);
            Int2ObjectMap<Set<String>> tagCache = repo.loadAllTags(conn);
            Int2ObjectMap<Set<String>> referenceCache = repo.loadAllReferences(conn);
            Int2ObjectMap<Set<String>> exploitTypeCache = repo.loadAllExploitTypes(conn);

            long start = System.currentTimeMillis();
            AtomicInteger pageCounter = new AtomicInteger();

            service.streamCatalogFromHomeTenant((page) -> {
                int currentPage = pageCounter.incrementAndGet();
                long pageStart = System.currentTimeMillis();
                long totalInsertOrUpdate = 0;
                long insertOrUpdateCount = 0;

                job.updateProgress(0, String.format("Processing page %d (%d items)", currentPage, page.size()));
                Logger.debug(String.format("Page: %d (%d items)", currentPage, page.size()));

                try {
                    for (DefenderVulnerability v : page) {
                        long vStart = System.currentTimeMillis();

                        repo.insertOrUpdateDefenderVulnerability(conn, v, vulnCache, vulnMetaCache, tagCache, referenceCache, exploitTypeCache);

                        totalInsertOrUpdate += (System.currentTimeMillis() - vStart);
                        insertOrUpdateCount++;
                    }
                    conn.commit();
                    job.updateProgress(0, "Processed page " + currentPage);
                } catch (SQLException e) {
                    try {
                        conn.rollback();
                    } catch (SQLException ex) {
                        Logger.error("Rollback failed", ex);
                    }
                    throw new RuntimeException(e);
                }

                long pageEnd = System.currentTimeMillis();
                long pageDuration = pageEnd - pageStart;

                double avgInsertOrUpdate = insertOrUpdateCount == 0 ? 0 : (double) totalInsertOrUpdate / insertOrUpdateCount;

                Logger.info(String.format(
                        "Page processed (Took %dms / %.2fs) (Avg: %.2fms over %d calls)",
                        pageDuration,
                        pageDuration / 1000f,
                        avgInsertOrUpdate,
                        insertOrUpdateCount
                ));
            });

            long end = System.currentTimeMillis();
            long duration = end - start;

            job.updateProgress(100, "Vulnerability catalog sync complete");

            Logger.info(String.format("Finished task (Took %dms / %.2fs)", duration, duration / 1000f));

        } catch (Exception e) {
            Logger.error("Catalog refresh failed", e);
            job.updateProgress(100, "Failed: " + e.getMessage());
        }
    }
}