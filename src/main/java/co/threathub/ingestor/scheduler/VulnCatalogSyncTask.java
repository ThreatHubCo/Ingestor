package co.threathub.ingestor.scheduler;

import co.threathub.ingestor.Ingestor;
import co.threathub.ingestor.defender.model.individual.DefenderVulnerability;
import co.threathub.ingestor.defender.service.VulnerabilityService;
import co.threathub.ingestor.job.model.NoOpScanJob;
import co.threathub.ingestor.job.model.ScanJob;
import co.threathub.ingestor.log.Logger;
import co.threathub.ingestor.repository.VulnerabilityRepository;
import co.threathub.ingestor.util.Utils;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

            long start = System.currentTimeMillis();

            AtomicInteger pageCounter = new AtomicInteger();

            service.streamCatalogFromHomeTenant(page -> {
                int currentPage = pageCounter.incrementAndGet();

                try {
                    for (DefenderVulnerability v : page) {
                        log.debug(String.format("%s: (%d/%d)", v.getId(), currentPage, page.size()));
                        repo.insertOrUpdateDefenderVulnerability(conn, v);
                    }
                    conn.commit();

                    job.updateProgress(0, "Processed page " + currentPage);
                } catch (SQLException e) {
                    try {
                        conn.rollback();
                    } catch (SQLException ex) {
                        log.error("Rollback failed", ex);
                    }
                    throw new RuntimeException(e);
                }
            });

            conn.setAutoCommit(true);

            long end = System.currentTimeMillis();
            long duration = end - start;

            job.updateProgress(100, "Vulnerability catalog sync complete");

            Logger.info(String.format("Finished task (Took %dms)", duration));

        } catch (Exception e) {
            Logger.error("Catalog refresh failed", e);
            job.updateProgress(100, "Failed: " + e.getMessage());
        }
    }
}