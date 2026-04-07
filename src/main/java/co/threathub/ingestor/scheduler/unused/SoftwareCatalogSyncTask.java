package co.threathub.ingestor.scheduler.unused;

import co.threathub.ingestor.Ingestor;
import co.threathub.ingestor.defender.model.individual.DefenderSoftware;
import co.threathub.ingestor.defender.service.SoftwareService;
import co.threathub.ingestor.log.Logger;
import co.threathub.ingestor.repository.SoftwareRepository;
import co.threathub.ingestor.scheduler.ITask;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically fetches every single software from the Defender API
 * and adds or updates it in the database.
 *
 * Currently unused.
 */
@Slf4j
public class SoftwareCatalogSyncTask implements ITask {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Ingestor ingestor;

    public SoftwareCatalogSyncTask(Ingestor ingestor) {
        this.ingestor = ingestor;
        this.schedule();
    }

    private void schedule() {
        scheduler.scheduleAtFixedRate(this::run, 0, 24, TimeUnit.HOURS);
    }

    @Override
    public void execute() {
        this.run();
    }

    private void run() {
        Logger.info("Starting task");
        long start = System.currentTimeMillis();

        SoftwareService service = ingestor.getSoftwareService();
        SoftwareRepository repo = ingestor.getSoftwareRepository();

        try {
            List<DefenderSoftware> allSoftware = service.fetchAllSoftware();

            try (Connection conn = ingestor.getDataSource().getConnection()) {
                for (DefenderSoftware software : allSoftware) {
                   repo.insertOrGetSoftwareOld(conn, software);
                }

                long end = System.currentTimeMillis();
                long duration = end - start;
                Logger.info(String.format("Finished task (Took %dms)", duration));
            } catch (Exception e) {
                Logger.error("Software catalog refresh failed", e);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}