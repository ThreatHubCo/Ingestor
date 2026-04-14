package co.threathub.ingestor.scheduler;

import co.threathub.ingestor.Ingestor;
import co.threathub.ingestor.config.ConfigEntry;
import co.threathub.ingestor.config.ConfigKey;
import co.threathub.ingestor.defender.model.individual.DefenderMachine;
import co.threathub.ingestor.job.JobHelper;
import co.threathub.ingestor.job.model.NoOpScanJob;
import co.threathub.ingestor.job.model.ScanJob;
import co.threathub.ingestor.log.Logger;
import co.threathub.ingestor.model.Customer;
import co.threathub.ingestor.model.Device;
import co.threathub.ingestor.util.Utils;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically checks for devices that are not entra joined and removes them from the database.
 * <br>
 * TODO: This may not be the best way. We may want to keep non entra joined devices, but for now this fixes an issue
 * for MSPs where a client device being built that was discovered on the MSPs Defender is added to the database but
 * then when it connects to the client's domain and is discovered in that Defender, there will then be a duplicate in the database.
 * At least I think.
 */
@Slf4j
public class DeviceCleanupTask implements ITask {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Ingestor ingestor;

    public DeviceCleanupTask(Ingestor ingestor) {
        this.ingestor = ingestor;
        this.schedule();
    }

    public void schedule() {
        long intialDelay = Utils.computeInitialDelay(6, 0); // 6am
        long period = TimeUnit.DAYS.toMillis(1); // 24 hours
        scheduler.scheduleAtFixedRate(this::execute, intialDelay, period, TimeUnit.MILLISECONDS);
    }

    @Override
    public void execute() {
        this.actuallyRun(new NoOpScanJob());
    }

    public void actuallyRun(ScanJob job) {
        Logger.info("Starting device cleanup task");
        long start = System.currentTimeMillis();

        Map<ConfigKey, ConfigEntry> config = ingestor.getConfigRepository().getAll();
        ConfigEntry skipNonEntraJoinedDevicesEntry = config.get(ConfigKey.SKIP_NON_ENTRA_JOINED_DEVICES);
        boolean skipNonEntraJoinedDevices = skipNonEntraJoinedDevicesEntry == null || skipNonEntraJoinedDevicesEntry.getValue().equalsIgnoreCase("true");

        if (!skipNonEntraJoinedDevices) {
            Logger.info("Skipping task as it is disabled in the config");
            return;
        }

        try (Connection conn = ingestor.getDataSource().getConnection()) {
            List<Device> dbDevices = ingestor.getDeviceRepository().getAllDevices();
            Map<String, DefenderMachine> defenderLookup = new HashMap<>();

            for (Customer customer : ingestor.getCustomerRepository().getAllCustomers()) {
                List<DefenderMachine> machines = ingestor.getDeviceService().fetchAllMachines(customer.getTenantId());

                for (DefenderMachine machine : machines) {
                    if (machine.getComputerDnsName() != null) {
                        defenderLookup.put(
                                machine.getComputerDnsName().toLowerCase(),
                                machine
                        );
                    }
                }
            }

            int total = dbDevices.size();
            int current = 0;
            int lastReported = 0;

            for (Device device : dbDevices) {
                current++;

                if (device.getDnsName() == null) {
                    continue;
                }

                DefenderMachine machine = defenderLookup.get(device.getDnsName().toLowerCase());
                boolean shouldDelete = false;

                if (machine == null) {
                    shouldDelete = true;
                } else {
                    boolean isAadJoined = machine.isAadJoined();
                    boolean hasVmMetadata = machine.getVmMetadata() != null;

                    if (!isAadJoined && skipNonEntraJoinedDevices && !hasVmMetadata) {
                        shouldDelete = true;
                    }
                }

                if (shouldDelete) {
                    ingestor.getDeviceRepository().removeDevice(device.getId());
                    log.info("Removing device: {}", device.getDnsName());
                }

                int percent = (int) ((current / (double) total) * 100);

                if (percent >= lastReported + 20) {
                    lastReported = (percent / 20) * 20;
                    job.updateProgress(lastReported, "Cleanup " + lastReported + "% complete");
                }
            }
        } catch (Exception ex) {
            Logger.error("Device cleanup task failed", ex);
        }

        long end = System.currentTimeMillis();
        long duration = end - start;
        Logger.info(String.format("Finished device cleanup task (Took %dms)", duration));
    }
}
