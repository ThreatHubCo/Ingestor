package co.threathub.ingestor.reporting;

import co.threathub.ingestor.Ingestor;
import co.threathub.ingestor.config.ConfigEntry;
import co.threathub.ingestor.config.ConfigKey;
import co.threathub.ingestor.config.ConfigValueType;
import co.threathub.ingestor.log.BackendLog;
import co.threathub.ingestor.log.Logger;
import co.threathub.ingestor.util.Utils;
import io.sentry.Sentry;
import lombok.Getter;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public class ReportingService {
    private final Ingestor ingestor;

    private static final String DEFAULT_BASE_URL = "https://api.threathub.co/v1/reporting";
    private static final String HEARTBEAT_ENDPOINT = "/heartbeat";

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Getter
    private String instanceId;

    public ReportingService(Ingestor ingestor) {
        this.ingestor = ingestor;

        try {
            this.instanceId = initInstanceId();

            scheduler.scheduleAtFixedRate(this::sendHeartbeat, 30, 30, TimeUnit.SECONDS);

            Logger.info("Reporting service initialized");
        } catch (Exception ex) {
            Logger.error("Failed to initialize reporting service", ex);
            this.instanceId = null;
        }
    }

    private String initInstanceId() {
        Map<ConfigKey, ConfigEntry> config = ingestor.getConfigRepository().getAll();
        ConfigEntry instanceIdEntry = config.get(ConfigKey.INSTANCE_ID);

        if (instanceIdEntry == null || instanceIdEntry.getValue().isEmpty()) {
            String uuid = UUID.randomUUID().toString();
            ingestor.getConfigRepository().upsert(ConfigKey.INSTANCE_ID, uuid, ConfigValueType.STRING);
            return uuid;
        }
        return instanceIdEntry.getValue();
    }

    private void sendHeartbeat() {
        try {
            if (instanceId == null) {
                return;
            }
            if (!isHeartbeatSendingEnabled()) {
                return;
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("instanceId", instanceId);
            payload.put("timestamp", Instant.now().toString());

            String body = Utils.GSON.toJson(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(DEFAULT_BASE_URL + HEARTBEAT_ENDPOINT))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            Utils.HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 300) {
                            Logger.warn("Heartbeat failed: " + response.body());
                        }
                    });
        } catch (Exception ex) {
            Logger.error("Failed to send heartbeat", ex);
        }
    }

    public boolean isHeartbeatSendingEnabled() {
        Map<ConfigKey, ConfigEntry> config = ingestor.getConfigRepository().getAll();
        ConfigEntry entry = config.get(ConfigKey.SEND_EXTERNAL_HEARTBEAT);
        return entry != null && entry.getValue().equalsIgnoreCase("true");
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}