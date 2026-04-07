package co.threathub.ingestor.log;

import co.threathub.ingestor.reporting.ReportingService;
import org.slf4j.LoggerFactory;

public final class Logger {
    private static BackendLogRepository repository;
    private static ReportingService reportingService;

    public static void init(BackendLogRepository repo, ReportingService reportingService) {
        Logger.repository = repo;
        Logger.reportingService = reportingService;
    }

    // Automatically resolve calling class
    private static String resolveSource() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
            .walk(stream ->
                stream
                    .filter(f -> !f.getClassName().equals(Logger.class.getName()))
                    .findFirst()
                    .map(f -> f.getDeclaringClass().getSimpleName())
                    .orElse("Unknown")
            );
    }

    private static void log(String level, String message, Throwable t, Integer customerId) {
        String source = resolveSource();

        org.slf4j.Logger logger = LoggerFactory.getLogger(source);
        switch (level) {
            case "INFO" -> logger.info(message);
            case "WARN" -> logger.warn(message);
            case "ERROR" -> logger.error(message, t);
            case "DEBUG" -> logger.info(message); // TODO: Debug
        }

        BackendLog log = BackendLog.builder()
                .level(level)
                .source(source)
                .text(t != null ? message + " | " + t.getMessage() : message)
                .customerId(customerId)
                .build();

        if (repository != null) {
            repository.insert(log);
        }
        if (reportingService != null) {
            reportingService.queueLog(log);
        }
    }

    public static void info(String message) {
        log("INFO", message, null, null);
    }

    public static void warn(String message) {
        log("WARN", message, null, null);
    }

    public static void error(String message, Throwable t) {
        log("ERROR", message, t, null);
    }

    public static void error(String message, Throwable t, Integer customerId) {
        log("ERROR", message, t, customerId);
    }

    public static void info(String message, Integer customerId) {
        log("INFO", message, null, customerId);
    }

    public static void debug(String message) {
        log("DEBUG", message, null, null);
    }

    public static void debug(String message, Integer customerId) {
        log("DEBUG", message, null, customerId);
    }
}