package co.threathub.ingestor.util;

import co.threathub.ingestor.Ingestor;

/**
 * This is probably temporary, I will probably implement some sort of interactive
 * command system in the near future.
 */
public class StartupTaskRunner {
    private final Ingestor ingestor;

    public StartupTaskRunner(Ingestor ingestor) {
        this.ingestor = ingestor;
    }

    /**
     * Parses command line arguments and runs the requested task if specified.
     * @param args command-line arguments
     * @return true if a task was executed, otherwise false
     */
    public boolean handleArgsAndRun(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--run-task=")) {
                String schedulerName = arg.substring("--run-task=".length());
                return run(schedulerName);
            }
        }
        return false;
    }

    /**
     * Runs a task by its name.
     *
     * @param name the task name (case-insensitive)
     * @return true if task was found and executed, false otherwise
     */
    public boolean run(String name) {
        switch (name.toLowerCase()) {
            case "vulncatalog":
                ingestor.getVulnCatalogSyncTask().execute();
                return true;
            case "vulnexposure":
                ingestor.getVulnExposureSyncTask().execute();
                return true;
            case "vulnescalation":
                ingestor.getVulnEscalationTask().execute();
                return true;
            case "devicecatalog":
                ingestor.getDeviceCatalogSyncTask().execute();
                return true;
            case "securityrecs":
                ingestor.getSecurityRecSyncTask().execute();
                return true;
            case "halosync":
                ingestor.getHaloSyncTask().execute();
                return true;
            case "devicecleanup":
                ingestor.getDeviceCleanupTask().execute();
                return true;
            default:
                return false;
        }
    }
}