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
import co.threathub.ingestor.repository.*;
import co.threathub.ingestor.util.Utils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
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

    public void syncTenant(Customer customer, ScanJob job) throws IOException, InterruptedException, SQLException {
        String tenantId = customer.getTenantId();

        DeviceRepository deviceRepository = ingestor.getDeviceRepository();
        DeviceService deviceService = ingestor.getDeviceService();
        Map<ConfigKey, ConfigEntry> config = ingestor.getConfigRepository().getAll();

        // Fetch all devices from Defender and map
        List<DefenderMachine> machines = deviceService.fetchAllMachines(tenantId);

        Map<String, DefenderMachine> machineMap =
                machines.stream().collect(Collectors.toMap(
                        DefenderMachine::getId, m -> m
                ));

        ConfigEntry skipNonEntraJoinedDevicesEntry = config.get(ConfigKey.SKIP_NON_ENTRA_JOINED_DEVICES);
        boolean skipNonEntraJoinedDevices = skipNonEntraJoinedDevicesEntry == null || skipNonEntraJoinedDevicesEntry.getValue().equalsIgnoreCase("true");

        // Insert or update all devices in the database
        Logger.info("Processing " + machines.size() + " devices", customer.getId());

        for (DefenderMachine machine : machines) {
            // Ignore machines with blank names
            if (machine.getComputerDnsName() == null || machine.getComputerDnsName().isEmpty()) {
                continue;
            }
            // Ignore machines that aren't entra joined
            // TODO: How to handle machines that were once entra joined but no longer are?
            if (!machine.isAadJoined() && skipNonEntraJoinedDevices) {
//                Logger.debug("Skipping non entra joined device: " + machine.getComputerDnsName(), customer.getId());
                continue;
            }
            //Logger.debug("Processing device " + machine.getComputerDnsName(), customer.getId());
            deviceRepository.insertDefenderDevice(customer.getId(), machine);
        }

        processExposures(customer, machineMap, job);
    }

    private void processExposures(Customer customer, Map<String, DefenderMachine> machineMap, ScanJob job) throws IOException, InterruptedException, SQLException {
        MachineVulnerabilityService mvService = ingestor.getMachineVulnerabilityService();

        VulnerabilityRepository vulnRepo = ingestor.getVulnerabilityRepository();
        DeviceVulnerabilityRepository deviceVulnRepo = ingestor.getDeviceVulnerabilityRepository();
        SoftwareRepository softwareRepo = ingestor.getSoftwareRepository();

        int customerId = customer.getId();

        Map<Integer, Set<Integer>> vulnIdToActiveDevices = new HashMap<>();
        Map<Integer, List<DefenderMachineVulnerability>> vulnIdToAffectedSoftware = new HashMap<>();

        AtomicInteger pageNumber = new AtomicInteger();

        try (Connection conn = ingestor.getDataSource().getConnection()) {
            mvService.streamAllForTenant(customer.getTenantId(), page -> {
                try {
                    Map<String, List<DefenderMachineVulnerability>> cveToDevices = page.stream()
                            .filter(mv -> mv.getCveId() != null)
                            .collect(Collectors.groupingBy(DefenderMachineVulnerability::getCveId));

                    int currentPage = pageNumber.incrementAndGet();
                    job.updateProgress(0, "Processing page " + currentPage);

                    Logger.info(String.format("Looping over CVEs (page %s)", currentPage), customerId);

                    for (Map.Entry<String, List<DefenderMachineVulnerability>> entry : cveToDevices.entrySet()) {
                        String cveId = entry.getKey();
                        List<DefenderMachineVulnerability> affectedDevices = entry.getValue();

                        // Check if the CVE is in the local catalog. If it's not, then it should be
                        // the next time we check so we can skip it
                        Integer vulnId = vulnRepo.getVulnerabilityIdByCve(cveId);

                        if (vulnId == null) {
                            Logger.warn("CVE not in catalog: " + cveId);
                            continue;
                        }

                        log.debug("Fetching CVE: {}", cveId);

                        // Link the vulnerability to the customer
                        vulnRepo.insertCustomerVulnerability(conn, customerId, vulnId);

                        // Find device database IDs from Defender IDs
                        Set<Integer> deviceIds = deviceVulnRepo.resolveDeviceIds(conn, affectedDevices, machineMap, customerId);

                        // Insert or update device vulnerability status
                        for (Integer deviceId : deviceIds) {
                            deviceVulnRepo.insertDeviceVulnerability(deviceId, vulnId, customerId);
                        }

                        // Track active devices per vulnerability
                        vulnIdToActiveDevices.computeIfAbsent(vulnId, k -> new HashSet<>()).addAll(deviceIds);

                        // Track affected software per vulnerability
                        vulnIdToAffectedSoftware.computeIfAbsent(vulnId, k -> new ArrayList<>()).addAll(affectedDevices);

                        // Update global vulnerability_affected_software (if needed)
                        softwareRepo.insertVulnerabilityAffectedSoftwareBatch(conn, vulnId, affectedDevices);
                        for (DefenderMachineVulnerability mv : affectedDevices) {
                            long start = System.currentTimeMillis();

                           // softwareRepo.insertVulnerabilityAffectedSoftware(conn, vulnId, mv.getProductName(), mv.getProductVendor(), mv.getProductVersion());

                            long end = System.currentTimeMillis();
                            long time = end - start;

                            //log.info("software {}, version {} (TIME {}ms)", mv.getProductName(), mv.getProductVersion(), time);
                        }
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });

            // After all pages are processed, mark cleared devices as resolved
//            for (Map.Entry<Integer, Set<Integer>> entry : vulnIdToActiveDevices.entrySet()) {
//                int vulnId = entry.getKey();
//                Set<Integer> activeDeviceIds = entry.getValue();
//                deviceVulnRepo.markClearedDevicesAsResolved(customerId, vulnId, activeDeviceIds);
//                //Logger.debug("Processing " + activeDeviceIds.size() + " active device ids");
//            }

            Set<Integer> allCustomerVulns = deviceVulnRepo.getCustomerActiveVulnerabilities(conn, customerId);

            for (Integer vulnId : allCustomerVulns) {
                Set<Integer> activeDeviceIds =
                        vulnIdToActiveDevices.getOrDefault(vulnId, Collections.emptySet());

                deviceVulnRepo.markClearedDevicesAsResolved(customerId, vulnId, activeDeviceIds);
            }

            // Sync customer affected software
//            for (Map.Entry<Integer, List<DefenderMachineVulnerability>> entry : vulnIdToAffectedSoftware.entrySet()) {
//                int vulnId = entry.getKey();
//                List<DefenderMachineVulnerability> affectedSoftwareList = entry.getValue();
//                softwareRepo.syncCustomerAffectedSoftware(conn, vulnId, customerId, affectedSoftwareList);
//                Logger.debug("Affected software list size: " + affectedSoftwareList.size());
//            }

//          conn.commit();
        }
    }
}