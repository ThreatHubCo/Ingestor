package co.threathub.ingestor.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import co.threathub.ingestor.Ingestor;
import co.threathub.ingestor.config.ConfigEntry;
import co.threathub.ingestor.config.ConfigKey;
import co.threathub.ingestor.log.Logger;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

public class Utils {
    public static final HttpClient HTTP = HttpClient.newHttpClient();
    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    public static String VERSION = "1.0.0";

    public static String getHomeTenantId() {
        Map<ConfigKey, ConfigEntry> config = Ingestor.getInstance().getConfigRepository().getAll();
        ConfigEntry entry = config.get(ConfigKey.HOME_TENANT_ID);

        if (entry == null) {
            throw new RuntimeException("Invalid home tenant ID in config");
        }
        return entry.getValue();
    }

    public static String buildSoftwareId(String name, String vendor) {
        return vendor + "-_-" + name;
    }

    public static String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Checks whether a config key exists and has a non-blank value.
     */
    public static boolean hasNonBlankConfig(Map<ConfigKey, ConfigEntry> config, ConfigKey key) {
        if (config == null || key == null) {
            return false;
        }

        ConfigEntry entry = config.get(key);
        if (entry == null) {
            return false;
        }

        String value = entry.getValue();
        return value != null && !value.isBlank();
    }

    /**
     * Validates whether a string is a well-formed URL.
     */
    public static boolean isValidUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            new URL(value);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    /**
     * Checks whether all required ticket system config values exist and are valid.
     */
    public static boolean isTicketSystemConfigured(Map<ConfigKey, ConfigEntry> config) {
        if (!hasNonBlankConfig(config, ConfigKey.TICKET_SYSTEM_CLIENT_ID)) {
            return false;
        }
        if (!hasNonBlankConfig(config, ConfigKey.TICKET_SYSTEM_CLIENT_SECRET)) {
            return false;
        }
        if (!hasNonBlankConfig(config, ConfigKey.TICKET_SYSTEM_URL)) {
            return false;
        }
        return isValidUrl(config.get(ConfigKey.TICKET_SYSTEM_URL).getValue());
    }

    public static long computeInitialDelay(int targetHour, int targetMinute) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.withHour(targetHour).withMinute(targetMinute).withSecond(0).withNano(0);

        if (!now.isBefore(nextRun)) {
            // If current time is past target, schedule for tomorrow
            nextRun = nextRun.plusDays(1);
        }
        Duration duration = Duration.between(now, nextRun);
        return duration.toMillis();
    }

    public static boolean isSeverityAtLeast(String severity, String targetSeverity) {
        return getLevel(severity) >= getLevel(targetSeverity);
    }

    private static int getLevel(String s) {
        switch (s.toLowerCase()) {
            case "low": return 1;
            case "medium": return 2;
            case "high": return 3;
            case "critical": return 4;
        }
        return 0;
    }

    public static Timestamp parseSqlTimestamp(String isoDate) {
        if (isoDate == null) {
            return null;
        }
        try {
            return Timestamp.from(Instant.parse(isoDate));
        } catch (DateTimeParseException ex) {
            Logger.error("Failed to parse timestamp: {}" + isoDate, ex);
            return null;
        }
    }
}
