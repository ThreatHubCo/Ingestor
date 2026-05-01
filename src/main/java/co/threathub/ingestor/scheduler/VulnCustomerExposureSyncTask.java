package co.threathub.ingestor.scheduler;

import co.threathub.ingestor.Ingestor;
import co.threathub.ingestor.config.ConfigEntry;
import co.threathub.ingestor.config.ConfigKey;
import co.threathub.ingestor.defender.model.individual.DefenderMachine;
import co.threathub.ingestor.defender.model.individual.DefenderMachineVulnerability;
import co.threathub.ingestor.defender.service.DeviceService;
import co.threathub.ingestor.defender.service.MachineVulnerabilityService;
import co.threathub.ingestor.job.model.NoOpScanJob;
import co.threathub.ingestor.job.model.ScanJob;
import co.threathub.ingestor.log.Logger;
import co.threathub.ingestor.model.Customer;
import co.threathub.ingestor.model.DeviceVulnKey;
import co.threathub.ingestor.repository.*;
import co.threathub.ingestor.util.Utils;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
public class VulnCustomerExposureSyncTask implements ITask {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Ingestor ingestor;

    public VulnCustomerExposureSyncTask(Ingestor ingestor) {
        this.ingestor = ingestor;
        this.schedule();
    }

    private void schedule() {
        long intialDelay = Utils.computeInitialDelay(3, 0); // 3am
        long period = TimeUnit.DAYS.toMillis(1); // 24 hours
        scheduler.scheduleAtFixedRate(this::execute, intialDelay, period, TimeUnit.MILLISECONDS);
    }

    @Override
    public void execute() {
        this.syncAllTenants(new NoOpScanJob());
    }

    public void syncAllTenants(ScanJob job) {
        Logger.info("Starting task");
        long start = System.currentTimeMillis();
        int i = 1;

        CustomerRepository repository = ingestor.getCustomerRepository();
        List<Customer> customers = repository.getAllCustomers();

        for (Customer customer : customers) {
            try {
                Logger.info(String.format("Processing customer: %s (%d/%d)", customer.getName(), i++, customers.size()));
                long startCust = System.currentTimeMillis();

                syncTenant(customer, job);

                long endCust = System.currentTimeMillis();
                long durationCust = endCust - startCust;

                Logger.info(String.format("Finished sync for customer %s (Took %dms)", customer.getName(), durationCust));
            } catch (Exception e) {
                Logger.error(String.format("Sync failed for %s", customer.getName()), e, customer.getId());
            }
        }

        long end = System.currentTimeMillis();
        long duration = end - start;

        Logger.info(String.format("Finished task (Took %dms)", duration));
    }

    public void syncTenant(ScanJob job) throws Exception {
        Customer customer = ingestor.getCustomerRepository().getCustomerById(job.getTargetId());

        if (customer == null) {
            throw new IllegalStateException("Customer not found: " + job.getTargetId());
        }
        syncTenant(customer, job);
    }

    public void syncTenant(Customer customer, ScanJob job) throws Exception {
        String tenantId = customer.getTenantId();

        DeviceRepository deviceRepository = ingestor.getDeviceRepository();
        DeviceService deviceService = ingestor.getDeviceService();
        Map<ConfigKey, ConfigEntry> config = ingestor.getConfigRepository().getAll();

        List<DefenderMachine> machines = deviceService.fetchAllMachines(tenantId);

        ConfigEntry skipNonEntraJoinedDevicesEntry = config.get(ConfigKey.SKIP_NON_ENTRA_JOINED_DEVICES);
        boolean skipNonEntraJoinedDevices = skipNonEntraJoinedDevicesEntry == null || skipNonEntraJoinedDevicesEntry.getValue().equalsIgnoreCase("true");

        Logger.info("Processing " + machines.size() + " devices", customer.getId());

        List<DefenderMachine> filtered = new ArrayList<>();

        for (DefenderMachine machine : machines) {
            // Ignore machines with blank names
            if (machine.getComputerDnsName() == null || machine.getComputerDnsName().isEmpty()) {
                continue;
            }
            // Ignore machines that aren't entra joined
            if (skipNonEntraJoinedDevices && !machine.isAadJoined() && machine.getVmMetadata() == null) {
                continue;
            }
            //Logger.debug("Processing device " + machine.getComputerDnsName(), customer.getId());
            deviceRepository.insertDefenderDevice(customer.getId(), machine);
            filtered.add(machine);
        }

        Logger.info("Processing exposures", customer.getId());
        processExposures(customer, ingestor.getMachineVulnerabilityService(), job);
    }

    private void processExposures(Customer customer, MachineVulnerabilityService mvService, ScanJob job) throws Exception {
        int customerId = customer.getId();

        VulnerabilityRepository vulnRepo = ingestor.getVulnerabilityRepository();
        SoftwareRepository softwareRepo = ingestor.getSoftwareRepository();
        DeviceVulnerabilityRepository dvRepo = ingestor.getDeviceVulnerabilityRepository();

        Set<DeviceVulnKey> currentSnapshot = ConcurrentHashMap.newKeySet();
        AtomicInteger page = new AtomicInteger();

        try (Connection conn = ingestor.getDataSource().getConnection()) {
            mvService.streamAllForTenant(customer.getTenantId(), batch -> {
                try {
                    int currentPage = page.incrementAndGet();

                    Logger.info("Processing page " + currentPage);
                    job.updateProgress(0, "Processing page " + currentPage);

                    for (DefenderMachineVulnerability mv : batch) {
                        if (mv.getCveId() == null) {
                            Logger.debug("CVE is null", customerId);
                            continue;
                        }
                        if (mv.getProductName() == null) {
                            Logger.debug("Product name is null", customerId);
                            continue;
                        }

                        int vulnId = vulnRepo.getVulnerabilityIdByCve(mv.getCveId());

                        if (vulnId == -1) {
                            Logger.debug("vulnId is null", customerId);
                            continue;
                        }

                        int softwareId = softwareRepo.resolveSoftwareId(conn, mv.getProductName(), mv.getProductVendor());

                        vulnRepo.insertVulnerabilitySoftware(conn, vulnId, softwareId); // Global
                        vulnRepo.insertCustomerVulnerabilitySoftware(conn, customerId, vulnId, softwareId); // Tenant specific

                        currentSnapshot.add(new DeviceVulnKey(mv.getMachineId(), vulnId, softwareId));
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Logger.error("Error occurred while syncing tenant", ex);
                }
            });

            // Load previous device vulnerabilities state from the database
            Set<DeviceVulnKey> previousState = dvRepo.loadActiveState(conn, customerId);

            // Diff the current and previous state
            Set<DeviceVulnKey> toInsert = new HashSet<>(currentSnapshot);
            toInsert.removeAll(previousState);

            Set<DeviceVulnKey> toResolve = new HashSet<>(previousState);
            toResolve.removeAll(currentSnapshot);

            // Insert or resolve vulnerabilities
            dvRepo.insertNewVulnerabilities(conn, customerId, toInsert);
            dvRepo.resolveVulnerabilities(conn, customerId, toResolve);
        }
    }
}