package co.threathub.ingestor.scheduler;

import co.threathub.ingestor.Ingestor;
import co.threathub.ingestor.config.ConfigEntry;
import co.threathub.ingestor.config.ConfigKey;
import co.threathub.ingestor.defender.model.individual.DefenderMachine;
import co.threathub.ingestor.defender.service.DeviceService;
import co.threathub.ingestor.job.JobHelper;
import co.threathub.ingestor.job.model.NoOpScanJob;
import co.threathub.ingestor.job.model.ScanJob;
import co.threathub.ingestor.log.Logger;
import co.threathub.ingestor.model.Customer;
import co.threathub.ingestor.repository.CustomerRepository;
import co.threathub.ingestor.repository.DeviceRepository;
import co.threathub.ingestor.util.Utils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Periodically fetches every single device from the Defender API
 * and adds or updates it in the database.
 */
@Slf4j
public class DeviceCatalogSyncTask implements ITask {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Ingestor ingestor;

    public DeviceCatalogSyncTask(Ingestor ingestor) {
        this.ingestor = ingestor;
        this.schedule();
    }

    private void schedule() {
        long delay930 = Utils.computeInitialDelay(9, 30); // 9:30am
        scheduler.scheduleAtFixedRate(this::execute, delay930, TimeUnit.DAYS.toMillis(1), TimeUnit.MILLISECONDS);

        long delay1400 = Utils.computeInitialDelay(14, 0); // 2pm
        scheduler.scheduleAtFixedRate(this::execute, delay1400, TimeUnit.DAYS.toMillis(1), TimeUnit.MILLISECONDS);
    }

    @Override
    public void execute() {
        this.syncAllTenants(new NoOpScanJob());
    }

    public void syncAllTenants(ScanJob job) {
        Logger.info("Starting device sync task");
        long start = System.currentTimeMillis();

        List<Customer> allCustomers = ingestor.getCustomerRepository().getAllCustomers();

        int total = allCustomers.size();
        int current = 0;

        for (Customer customer : allCustomers) {
            current++;

            int percent = (int) ((current / (double) total) * 100);
            job.updateProgress(percent, "Processing " + customer.getName());

            try {
                Logger.info(String.format("Device sync started for %s", customer.getName()), customer.getId());

                syncTenant(customer, job);

                Logger.info(String.format("Device sync complete for %s", customer.getName()), customer.getId());
            } catch (Exception e) {
                Logger.error(String.format("Device sync failed for %s", customer.getName()), e, customer.getId());

                job.setMessage("Failed for " + customer.getName() + ": " + e.getMessage());
                JobHelper.updateJob(job);
            }
        }

        long duration = System.currentTimeMillis() - start;
        Logger.info(String.format("Finished device sync task (Took %dms)", duration));
    }

    public void syncTenant(ScanJob job) throws Exception {
        Customer customer = ingestor.getCustomerRepository().getCustomerById(job.getTargetId());

        if (customer == null) {
            throw new IllegalStateException("Customer not found: " + job.getTargetId());
        }
        syncTenant(customer, job);
    }

    public void syncTenant(Customer customer, ScanJob job) throws IOException, InterruptedException {
        DeviceService deviceService = ingestor.getDeviceService();
        DeviceRepository deviceRepository = ingestor.getDeviceRepository();
        Map<ConfigKey, ConfigEntry> config = ingestor.getConfigRepository().getAll();

        List<DefenderMachine> machines = deviceService.fetchAllMachines(customer.getTenantId());

        ConfigEntry skipEntry = config.get(ConfigKey.SKIP_NON_ENTRA_JOINED_DEVICES);
        boolean skipNonEntraJoinedDevices = skipEntry == null || skipEntry.getValue().equalsIgnoreCase("true");

        int total = machines.size();
        int current = 0;

        for (DefenderMachine machine : machines) {
            current++;

            if (current % 10 == 0) {
                int percent = (int) ((current / (double) total) * 100);
                job.updateProgress(percent, "Processing devices for " + customer.getName());
            }

            if (machine.getComputerDnsName() == null || machine.getComputerDnsName().isEmpty()) {
                continue;
            }
            if (!machine.isAadJoined() && skipNonEntraJoinedDevices) {
                continue;
            }
            deviceRepository.insertDefenderDevice(customer.getId(), machine);
        }
    }
}