package co.threathub.ingestor.scheduler;

import co.threathub.ingestor.Ingestor;
import co.threathub.ingestor.config.ConfigEntry;
import co.threathub.ingestor.config.ConfigKey;
import co.threathub.ingestor.job.model.NoOpScanJob;
import co.threathub.ingestor.job.model.ScanJob;
import co.threathub.ingestor.log.Logger;
import co.threathub.ingestor.model.Customer;
import co.threathub.ingestor.util.Utils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically checks all tickets in the database that are in state OPEN or
 * CLOSED_GRACE_PERIOD and looks up the information in Halo to determine if
 * anything has changed.
 * <br>
 * The logic is as follows:
 * <br>
 * If marked as OPEN in database and Open in Halo, update details only
 * If marked as OPEN in database and Closed in Halo, update details & mark as CLOSED_GRACE_PERIOD
 * If marked as CLOSED_GRACE_PERIOD in database and Open in Halo, update details & mark as OPEN
 * If marked as CLOSED_GRACE_PERIOD in database and Closed in Halo, after X days, update details & mark as CLOSED (permanently)
 */
@Slf4j
public class HaloSyncTask implements ITask {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Ingestor ingestor;

    public HaloSyncTask(Ingestor ingestor) {
        this.ingestor = ingestor;
        this.schedule();
    }

    public void schedule() {
        scheduler.scheduleAtFixedRate(this::execute, 5, 60 * 2, TimeUnit.MINUTES);
    }

    @Override
    public void execute() {
        this.syncAllTickets(new NoOpScanJob());
    }

    public void syncAllTickets(ScanJob job) {
        Logger.info("Starting task");

        job.updateProgress(0, "Starting Halo ticket sync");

        try {
            Map<ConfigKey, ConfigEntry> config = ingestor.getConfigRepository().getAll();

            if (!Utils.isTicketSystemConfigured(config)) {
                Logger.warn("Failed to run Halo ticket sync as the config values are not correctly set");
                job.updateProgress(100, "Skipped (Halo not configured)");
                return;
            }

            ingestor.getHaloClient().checkTicketStatuses(job);

            job.updateProgress(100, "Halo sync complete");

        } catch (IOException | InterruptedException ex) {
            Logger.error("Halo sync failed", ex);
            job.updateProgress(100, "Failed: " + ex.getMessage());
            throw new RuntimeException(ex);
        }

        Logger.info("Finished task");
    }

    public void syncSingleCustomer(ScanJob job) throws Exception {
        Customer customer = ingestor.getCustomerRepository().getCustomerById(job.getTargetId());

        if (customer == null) {
            throw new IllegalStateException("Customer not found: " + job.getTargetId());
        }
        syncSingleCustomer(customer, job);
    }

    public void syncSingleCustomer(Customer customer, ScanJob job) {
        Logger.info("Starting Halo sync for customer: " + customer.getName());

        job.updateProgress(0, "Starting Halo sync for " + customer.getName());

        try {
            Map<ConfigKey, ConfigEntry> config = ingestor.getConfigRepository().getAll();

            if (!Utils.isTicketSystemConfigured(config)) {
                Logger.warn("Failed to run Halo ticket sync as the config values are not correctly set");
                job.updateProgress(100, "Skipped (Halo not configured)");
                return;
            }

            ingestor.getHaloClient().checkTicketStatuses(job, customer);

            job.updateProgress(100, "Halo sync complete for " + customer.getName());

        } catch (IOException | InterruptedException ex) {
            Logger.error("Halo sync failed for customer " + customer.getName(), ex);
            job.updateProgress(100, "Failed: " + ex.getMessage());
            throw new RuntimeException(ex);
        }

        Logger.info("Finished Halo sync for customer: " + customer.getName());
    }
}
