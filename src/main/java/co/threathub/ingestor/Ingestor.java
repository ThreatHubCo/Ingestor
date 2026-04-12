package co.threathub.ingestor;

import co.threathub.ingestor.js.JSManager;
import com.zaxxer.hikari.HikariDataSource;
import co.threathub.ingestor.defender.DefenderClient;
import co.threathub.ingestor.defender.service.SoftwareService;
import co.threathub.ingestor.halo.HaloClient;
import co.threathub.ingestor.config.ConfigRepository;
import co.threathub.ingestor.job.JobWorker;
import co.threathub.ingestor.log.BackendLogRepository;
import co.threathub.ingestor.log.Logger;
import co.threathub.ingestor.reporting.ReportingService;
import co.threathub.ingestor.repository.*;
import co.threathub.ingestor.scheduler.*;
import co.threathub.ingestor.defender.service.DeviceService;
import co.threathub.ingestor.defender.service.MachineVulnerabilityService;
import co.threathub.ingestor.defender.service.VulnerabilityService;
import co.threathub.ingestor.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Getter
@Slf4j
public class Ingestor {
    private static Ingestor INSTANCE;

    private final HikariDataSource dataSource = new HikariDataSource();
    private final HikariDataSource reportDataSource = new HikariDataSource();
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();

    private CustomerRepository customerRepository;
    private DeviceRepository deviceRepository;
    private DeviceVulnerabilityRepository deviceVulnerabilityRepository;
    private SoftwareRepository softwareRepository;
    private VulnerabilityRepository vulnerabilityRepository;
    private ConfigRepository configRepository;
    private RemediationRepository remediationRepository;
    private RecommendationRepository recommendationRepository;
    private BackendLogRepository backendLogRepository;

    private DefenderClient defenderClient;
    private HaloClient haloClient;

    private VulnerabilityService vulnerabilityService;
    private MachineVulnerabilityService machineVulnerabilityService;
    private DeviceService deviceService;
    private SoftwareService softwareService;
    private ReportingService reportingService;

    private TemplateManager templateManager;
    private JSManager scriptManager;

    private VulnCatalogSyncTask vulnCatalogSyncTask;
    private VulnCustomerExposureSyncTask vulnExposureSyncTask;
    private SecurityRecSyncTask securityRecSyncTask;
    private HaloSyncTask haloSyncTask;
    private VulnEscalationTask vulnEscalationTask;
    private DeviceCatalogSyncTask deviceCatalogSyncTask;
    private DeviceCleanupTask deviceCleanupTask;

    private ConfigFile configFile;

    public static void main(String[] args) {
        try {
            System.out.println("  _______ _                    _   _    _       _       _____                       _               ");
            System.out.println(" |__   __| |                  | | | |  | |     | |     |_   _|                     | |              ");
            System.out.println("    | |  | |__  _ __ ___  __ _| |_| |__| |_   _| |__     | |  _ __   __ _  ___  ___| |_ ___  _ __  ");
            System.out.println("    | |  | '_ \\| '__/ _ \\/ _` | __|  __  | | | | '_ \\    | | | '_ \\ / _` |/ _ \\/ __| __/ _ \\| '__| ");
            System.out.println("    | |  | | | | | |  __/ (_| | |_| |  | | |_| | |_) |  _| |_| | | | (_| |  __/\\__ \\ || (_) | |    ");
            System.out.println("    |_|  |_| |_|_|  \\___|\\__,_|\\__|_|  |_|\\__,_|_.__/  |_____|_| |_|\\__, |\\___||___/\\__\\___/|_|    ");
            System.out.println("                                                                     __/ |                             ");
            System.out.println("                                                                    |___/                              ");
            System.out.println("ThreatHub Ingestor v" + Utils.VERSION + " by Luke (luke@glitch.je)\n");

            Ingestor ingestor = new Ingestor();
            ingestor.run();

            StartupTaskRunner runner = new StartupTaskRunner(ingestor);
            runner.handleArgsAndRun(args);
        } catch (IOException ex) {
            System.err.println("Failed to start up");
            ex.printStackTrace();
        }
    }

    public void run() throws IOException {
        INSTANCE = this;

        // Load and validate config.properties
        this.configFile = new ConfigFile();
        validateConfig(configFile);

        // Ensure scripts and templates directories exist
        ensureDirectoriesExist();

        // Connect to database
        connectToDatabase(configFile);
        connectToDatabaseReportUser(configFile);

        // Init logging
        this.backendLogRepository = new BackendLogRepository(dataSource);
        Logger.init(backendLogRepository, reportingService);

        Logger.info("Initializing...");

        this.configRepository = new ConfigRepository(dataSource);

        // Init reporting
        this.reportingService = new ReportingService(this);

        this.customerRepository = new CustomerRepository(dataSource);
        this.deviceRepository = new DeviceRepository(dataSource);
        this.deviceVulnerabilityRepository = new DeviceVulnerabilityRepository(dataSource);
        this.softwareRepository = new SoftwareRepository(dataSource);
        this.vulnerabilityRepository = new VulnerabilityRepository(dataSource);
        this.remediationRepository = new RemediationRepository(dataSource);
        this.recommendationRepository = new RecommendationRepository(dataSource);

        this.templateManager = new TemplateManager();

        this.defenderClient = new DefenderClient(configFile, configRepository);
        this.haloClient = new HaloClient(configRepository, remediationRepository, templateManager);

        this.vulnerabilityService = new VulnerabilityService(defenderClient, customerRepository);
        this.machineVulnerabilityService = new MachineVulnerabilityService(defenderClient, customerRepository);
        this.deviceService = new DeviceService(defenderClient);
        this.softwareService = new SoftwareService(defenderClient);

        this.vulnCatalogSyncTask = new VulnCatalogSyncTask(this);
        this.vulnExposureSyncTask = new VulnCustomerExposureSyncTask(this);
        this.vulnEscalationTask = new VulnEscalationTask(this);
        this.deviceCatalogSyncTask = new DeviceCatalogSyncTask(this);
        this.securityRecSyncTask = new SecurityRecSyncTask(this);
        this.haloSyncTask = new HaloSyncTask(this);
        this.deviceCleanupTask = new DeviceCleanupTask(this);

        this.scriptManager = new JSManager(this);

        JobWorker worker = new JobWorker(this);

        // Run in a separate thread so main thread is free
        Thread workerThread = new Thread(worker, "JobWorker-Thread");
        workerThread.start();

        Logger.info("JobWorker started and polling for jobs...");

        // Schedule heartbeat insert every 30 seconds
        heartbeatScheduler.scheduleAtFixedRate(this::insertHeartbeatRedis, 0, 30, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("Shutting down...");
            JedisManager.shutdown();
        }));
    }

    private void connectToDatabase(ConfigFile configFile) {
        dataSource.setJdbcUrl(configFile.getUrl());
        dataSource.setUsername(configFile.getUsername());
        dataSource.setPassword(configFile.getPassword());
        dataSource.setMaxLifetime(configFile.getMaxLifetime());
    }

    private void connectToDatabaseReportUser(ConfigFile configFile) {
        reportDataSource.setJdbcUrl(configFile.getUrl());
        reportDataSource.setUsername(configFile.getReportUserUsername());
        reportDataSource.setPassword(configFile.getReportUserPassword());
        reportDataSource.setMaxLifetime(configFile.getMaxLifetime());
    }

    private void validateConfig(ConfigFile configFile) {
        if (configFile.getUrl() == null || configFile.getUrl().isEmpty() ||
                configFile.getUsername() == null || configFile.getUsername().isEmpty() ||
                configFile.getPassword() == null || configFile.getPassword().isEmpty()
        ) {
            throw new RuntimeException("Failed to load config. Please ensure all values are filled out correctly.");
        }
    }

    public static void ensureDirectoriesExist() {
        try {
            Files.createDirectories(Path.of("scripts"));
            Files.createDirectories(Path.of("templates"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create required directories", e);
        }
    }

    private void insertHeartbeatRedis() {
        try (Jedis jedis = JedisManager.getConnection()) {
            jedis.set("threathub:health:ingestor-last-check-in", Instant.now().toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Ingestor getInstance() {
        return INSTANCE;
    }
}
