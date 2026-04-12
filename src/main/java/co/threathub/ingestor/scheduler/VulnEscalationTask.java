package co.threathub.ingestor.scheduler;

import co.threathub.ingestor.Ingestor;
import co.threathub.ingestor.config.ConfigEntry;
import co.threathub.ingestor.config.ConfigKey;
import co.threathub.ingestor.halo.HaloClient;
import co.threathub.ingestor.log.Logger;
import co.threathub.ingestor.repository.RemediationRepository;
import co.threathub.ingestor.repository.VulnerabilityRepository;
import co.threathub.ingestor.util.Utils;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically checks open Software / Vulnerabilities to see if
 * <p>
 *   a) There is a public exploit available
 *   b) The first time we found the Vulnerability was more than X days ago
 * <p>
 * If either of the above is true, it checks if there is currently any open tickets.
 * If there is, it updates the ticket if any details change otherwise it creates a ticket.
 */
@Slf4j
public class VulnEscalationTask implements ITask {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Ingestor ingestor;

    public VulnEscalationTask(Ingestor ingestor) {
        this.ingestor = ingestor;
        this.schedule();
    }

    public void schedule() {
        scheduler.scheduleAtFixedRate(this::execute, 5, 60 * 6, TimeUnit.MINUTES);
    }

    @Override
    public void execute() {
        this.run();
    }

    public void run() {
        Logger.info("Starting task");
        long start = System.currentTimeMillis();

        try {
            Map<ConfigKey, ConfigEntry> config = ingestor.getConfigRepository().getAll();

            if (!Utils.isTicketSystemConfigured(config)) {
                Logger.warn("Failed to run Halo ticket sync as the config values are not correctly set");
                return;
            }

            VulnerabilityRepository vulnRepo = ingestor.getVulnerabilityRepository();
            RemediationRepository remediationRepository = ingestor.getRemediationRepository();
            HaloClient haloClient = ingestor.getHaloClient();

            int i = 0;

            try (Connection conn = ingestor.getDataSource().getConnection()) {
                List<VulnerabilityRepository.SoftwareCustomerPair> candidates = vulnRepo.findEscalationCandidates(conn);

                for (VulnerabilityRepository.SoftwareCustomerPair pair : candidates) {
                    int softwareId = pair.getSoftware().getId();
                    int customerId = pair.getCustomer().getId();

                    ConfigEntry publicExploitEntry = config.get(ConfigKey.ESCALATE_PUBLIC_EXPLOIT_IMMEDIATELY);
                    boolean escalatePublicExploitImmediately = publicExploitEntry != null && publicExploitEntry.getValue().equalsIgnoreCase("true");

                    log.debug("escalatePublicExploitImmediately: {}", escalatePublicExploitImmediately);

                    if (!vulnRepo.hasEscalatableCve(conn, softwareId, customerId, escalatePublicExploitImmediately, 7)) {
                        log.debug("escalatable cve: false");
                        continue;
                    }
                    log.debug("escalatable cve: true");

                    if (vulnRepo.hasActiveTicket(conn, softwareId, customerId)) {
                        log.debug("active ticket: true");
                        continue;
                    }
                    log.debug("active ticket: false");

                    boolean publicExploit = vulnRepo.hasPublicExploit(conn, softwareId, customerId);

                    int cveCount = remediationRepository.getEscalatableVulnerabilityCountForSoftware(customerId, softwareId);
                    int deviceCount = remediationRepository.getAffectedDeviceCountForSoftware(customerId, softwareId);
                    String highestCveSeverity = remediationRepository.getHighestCveSeverityForSoftware(customerId, softwareId);

                    ConfigEntry configSeverityEntry = config.get(ConfigKey.MIN_CVE_SEVERITY_FOR_ESCALATION);
                    log.debug("config severity: " + configSeverityEntry);
                    log.debug("highest severity: " + highestCveSeverity);

                    // Check the highest cve severity is at least what we specified in the config
                    // otherwise return
                    if (!Utils.isSeverityAtLeast(highestCveSeverity, configSeverityEntry != null ? configSeverityEntry.getValue() : "High")) {
                        return;
                    }

                    Logger.debug(String.format("Escalating %s (%s)", pair.getSoftware().getName(), ++i), customerId);

//                    haloClient.createTicket(pair.getCustomer(), pair.getSoftware(), publicExploit, cveCount, deviceCount, highestCveSeverity);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        long end = System.currentTimeMillis();
        long duration = end - start;
        Logger.info(String.format("Finished task (Took %dms)", duration));
    }
}
