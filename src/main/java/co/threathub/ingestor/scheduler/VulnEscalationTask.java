package co.threathub.ingestor.scheduler;

import co.threathub.ingestor.Ingestor;
import co.threathub.ingestor.config.ConfigEntry;
import co.threathub.ingestor.config.ConfigKey;
import co.threathub.ingestor.halo.HaloClient;
import co.threathub.ingestor.js.enums.ScriptType;
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
            ingestor.getScriptManager().executeScript(ScriptType.SOFTWARE_ESCALATION);
        } catch (Exception ex) {
            Logger.error("Failed to execute software execution script", ex);
        }

        long end = System.currentTimeMillis();
        long duration = end - start;
        Logger.info(String.format("Finished task (Took %dms)", duration));
    }
}
