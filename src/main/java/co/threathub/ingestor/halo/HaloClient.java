package co.threathub.ingestor.halo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import co.threathub.ingestor.config.ConfigEntry;
import co.threathub.ingestor.config.ConfigKey;
import co.threathub.ingestor.config.ConfigRepository;
import co.threathub.ingestor.halo.model.HaloTicket;
import co.threathub.ingestor.job.model.ScanJob;
import co.threathub.ingestor.log.Logger;
import co.threathub.ingestor.model.Customer;
import co.threathub.ingestor.model.RemediationTicket;
import co.threathub.ingestor.model.Software;
import co.threathub.ingestor.repository.RemediationRepository;
import co.threathub.ingestor.util.TemplateManager;
import co.threathub.ingestor.util.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class HaloClient {
    private final ConfigRepository configRepository;
    private final RemediationRepository remediationRepository;
    private final TemplateManager templateManager;

    public String authenticate(Map<ConfigKey, ConfigEntry> config) throws IOException, InterruptedException {
        String form = "client_id=" + Utils.url(config.get(ConfigKey.TICKET_SYSTEM_CLIENT_ID).getValue())
                + "&client_secret=" + Utils.url(config.get(ConfigKey.TICKET_SYSTEM_CLIENT_SECRET).getValue())
                + "&scope=all"
                + "&grant_type=client_credentials";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        config.get(ConfigKey.TICKET_SYSTEM_URL).getValue() + "/auth/token"
                ))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> response = Utils.HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IllegalStateException("Auth API returned " + response.statusCode());
        }

        JsonObject object = JsonParser.parseString(response.body()).getAsJsonObject();
        return object.get("access_token").getAsString();
    }

    public void checkTicketStatuses(ScanJob job) throws IOException, InterruptedException {
        checkTicketStatuses(job, null);
    }

    public void checkTicketStatuses(ScanJob job, Customer customer) throws IOException, InterruptedException {
        List<RemediationTicket> remediationTickets;

        if (customer == null) {
            remediationTickets = remediationRepository.getAllTickets();
        } else {
            remediationTickets = remediationRepository.getAllTicketsForCustomer(customer);
        }

        int total = remediationTickets.size();
        int current = 0;

        if (total == 0) {
            job.updateProgress(100, "No tickets to process");
            return;
        }

        job.updateProgress(0, "Processing " + total + " tickets");

        for (RemediationTicket ticket : remediationTickets) {
            current++;

            Logger.debug("Lookup up ticket " + ticket.getExternalTicketId(), ticket.getCustomerId());

            try {
                HaloTicket haloTicket = lookupHaloTicket(ticket.getExternalTicketId());

                if (haloTicket == null) {
                    continue;
                }

                // If halo ticket is open, make sure remediation ticket is open
                if (!haloTicket.isClosed()) {
                    Logger.debug("Ticket " + ticket.getExternalTicketId() + " is open, changing status to open", ticket.getCustomerId());
                    remediationRepository.updateStatus(ticket.getId(), RemediationTicket.RemediationTicketStatus.OPEN);
                    continue;
                }
                // If halo ticket is closed and remediation ticket is open, start grace period for close
                if (ticket.getStatus() == RemediationTicket.RemediationTicketStatus.OPEN) {
                    Logger.debug("Ticket " + ticket.getExternalTicketId() + " is closed, changing status to closed grace period", ticket.getCustomerId());
                    remediationRepository.updateStatus(
                            ticket.getId(),
                            RemediationTicket.RemediationTicketStatus.CLOSED_GRACE_PERIOD
                    );
                    continue;
                }

                // If halo ticket is closed and remediation ticket is in grace period, close permanently
                if (ticket.getStatus() == RemediationTicket.RemediationTicketStatus.CLOSED_GRACE_PERIOD) {
                    if (isLastUpdateOlderThanDays(ticket.getLastTicketUpdateAt(), 5)) {
                        System.out.println("Ticket " + ticket.getExternalTicketId() + " is closed and in grace period, changing status to closed permanently");
                        remediationRepository.updateStatus(ticket.getId(), RemediationTicket.RemediationTicketStatus.CLOSED);
                    } else {
                        System.out.println("Ticket " + ticket.getExternalTicketId() + " is closed and in grace period, but still have time left in grace period");
                        remediationRepository.updateStatus(ticket.getId(), RemediationTicket.RemediationTicketStatus.CLOSED_GRACE_PERIOD);
                    }
                    continue;
                }
            } catch (Exception ex) {
                Logger.error("Failed to check ticket statuses", ex);
            }

            int percent = (int) ((current / (double) total) * 100);
            job.updateProgress(percent, "Processed " + current + "/" + total + " tickets");
        }
    }

    public HaloTicket lookupHaloTicket(String ticketId) throws IOException, InterruptedException {
        Map<ConfigKey, ConfigEntry> config = configRepository.getAll();
        String accessToken = authenticate(config);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        config.get(ConfigKey.TICKET_SYSTEM_URL).getValue() + "/api/Tickets/" + ticketId
                ))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> response = Utils.HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Auth API returned " + response.statusCode());
        }
        return Utils.GSON.fromJson(response.body(), HaloTicket.class);
    }

    public void createTicket(Customer customer, Software software, boolean publicExploit, int cveCount, int deviceCount, String highestCveSeverity) throws Exception {
        Map<ConfigKey, ConfigEntry> config = configRepository.getAll();
        String accessToken = authenticate(config);

        ConfigEntry siteUrlEntry = config.get(ConfigKey.SITE_URL);

        Map<String, Object> model = new HashMap<>();
        model.put("software", software);
        model.put("customer", customer);
        model.put("publicExploit", publicExploit);
        model.put("cveCount", cveCount);
        model.put("deviceCount", deviceCount);
        model.put("softwareLink", siteUrlEntry != null ? siteUrlEntry.getValue() + "/software/" + software.getId() + "?customer=" + customer.getId() : null);
        model.put("highestCveSeverity", highestCveSeverity);

        String htmlBody = templateManager.renderVulnerabilityTicket(model);

        htmlBody = htmlBody
                .replaceAll("[\\n\\r\\t]", "")
                .replaceAll(">\\s+<", "><")
                .replaceAll("\\s{2,}", " ")
                .trim();

        // Build JSON payload
        JsonObject ticketData = new JsonObject();
        ticketData.addProperty("summary", "Vulnerability - " + software.getName());
        ticketData.addProperty("details", htmlBody);

        JsonObject softwareData = new JsonObject();
        softwareData.addProperty("id", software.getId());

        JsonObject dataWrapper = new JsonObject();
        dataWrapper.addProperty("clientId", customer.getExternalCustomerId());
        dataWrapper.addProperty("tenantId", customer.getTenantId());
//        dataWrapper.add("software", softwareData);
        dataWrapper.add("ticket", ticketData);

        JsonObject payload = new JsonObject();
        payload.addProperty("type", "VulnerabilityNotification");
        payload.add("data", dataWrapper);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.get(ConfigKey.TICKET_SYSTEM_URL).getValue() + "/api/IncomingEvent/Process"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> response = Utils.HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new IllegalStateException("Failed to create ticket. Status: " + response.statusCode() + ", Body: " + response.body());
        }

        // Parse Halo response
        JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
        String haloTicketId = responseJson.get("entity_id").getAsString();

        // Save to local DB
        remediationRepository.insertTicket(new RemediationTicket(
                0,
                Instant.now().toString(),
                RemediationTicket.RemediationTicketStatus.OPEN,
                Instant.now().toString(),
                Instant.now().toString(),
                haloTicketId,
                customer.getId(),
                software.getId(),
                null
        ));
    }

    private boolean isLastUpdateOlderThanDays(String lastUpdateAt, int days) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime lastUpdate = LocalDateTime.parse(lastUpdateAt, formatter);
        Instant lastUpdateInstant = lastUpdate.toInstant(ZoneOffset.UTC);

        Instant now = Instant.now();
        return Duration.between(lastUpdateInstant, now).toDays() >= days;
    }
}
