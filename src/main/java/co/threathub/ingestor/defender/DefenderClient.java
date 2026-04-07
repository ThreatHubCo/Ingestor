package co.threathub.ingestor.defender;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import co.threathub.ingestor.Ingestor;
import co.threathub.ingestor.config.ConfigEntry;
import co.threathub.ingestor.config.ConfigKey;
import co.threathub.ingestor.config.ConfigRepository;
import co.threathub.ingestor.defender.model.*;
import co.threathub.ingestor.defender.model.individual.*;
import co.threathub.ingestor.log.Logger;
import co.threathub.ingestor.util.ConfigFile;
import co.threathub.ingestor.util.ODataQueryBuilder;
import co.threathub.ingestor.util.Utils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Handles requests to the Microsoft Defender API.
 */
public class DefenderClient {
    private final String BASE_URL;
    private final ConfigRepository configRepository;

    public DefenderClient(ConfigFile configFile, ConfigRepository configRepository) {
        this.BASE_URL = configFile.getDefenderBaseApiUrl();
        this.configRepository = configRepository;
    }

    public String clientTenantAuth(String tenantId) throws IOException, InterruptedException {
        Map<ConfigKey, ConfigEntry> config = configRepository.getAll();

        ConfigEntry entraAppId = config.get(ConfigKey.ENTRA_BACKEND_CLIENT_ID);
        ConfigEntry entraAppSecret = config.get(ConfigKey.ENTRA_BACKEND_CLIENT_SECRET);

        if (entraAppId == null || entraAppId.getValue().isEmpty() || entraAppSecret == null || entraAppSecret.getValue().isEmpty()) {
            throw new RuntimeException(String.format("Failed to authenticate to tenant %s because the config values are incorrect", tenantId));
        }

        String form = "client_id=" + Utils.url(entraAppId.getValue())
                + "&client_secret=" + Utils.url(entraAppSecret.getValue())
                + "&scope=" + Utils.url("https://api.securitycenter.microsoft.com/.default")
                + "&grant_type=client_credentials";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token"
                ))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        HttpResponse<String> response = Utils.HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            System.out.println(response.body());
            throw new IllegalStateException("Auth API returned " + response.statusCode());
        }

        JsonObject object = JsonParser.parseString(response.body()).getAsJsonObject();
        return object.get("access_token").getAsString();
    }

    /**
     * Returns ALL vulnerabilities that the Defender API tracks.
     * This can easily be 280,000+.
     *
     * @param token The access token for the home tenant
     * @param pageConsumer A callback for each page of vulnerabilities
     */
    public void streamAllVulnerabilities(String token, Consumer<List<DefenderVulnerability>> pageConsumer) throws IOException, InterruptedException {
        int skip = 0;

        while (true) {
            String url = BASE_URL + "/api/vulnerabilities?$skip=" + skip;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> response = Utils.HTTP.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200)
                throw new IllegalStateException(response.body());

            DefenderVulnerabilities page = Utils.GSON.fromJson(response.body(), DefenderVulnerabilities.class);
            List<DefenderVulnerability> vulns = page.getVulnerabilities();

            if (vulns.isEmpty()) {
                break;
            }
            pageConsumer.accept(vulns);
            skip += page.getCount();
        }
    }

    /**
     * Returns all vulnerabilities that the Defender API tracks that have been updated
     * since the specified date.
     *
     * After initial setup, {@link #streamAllVulnerabilities} should be called on first run
     * to populate the database with ALL vulnerabilities, then subsequent requests can call
     * this method to only return what's changed.
     *
     * @param token The access token for the home tenant
     * @param since The time to check since last check
     * @param pageConsumer A callback for each page of vulnerabilities
     */
    public void streamAllVulnerabilitiesSince(String token, Instant since, Consumer<List<DefenderVulnerability>> pageConsumer) throws IOException, InterruptedException {
        int skip = 0;

        String isoSince = java.time.format.DateTimeFormatter.ISO_INSTANT.format(since);

        while (true) {
            String url = BASE_URL + "/api/vulnerabilities?$filter=updatedAt gt " + isoSince + "&$skip=" + skip;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> response = Utils.HTTP.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200)
                throw new IllegalStateException(response.body());

            DefenderVulnerabilities page = Utils.GSON.fromJson(response.body(), DefenderVulnerabilities.class);
            List<DefenderVulnerability> vulns = page.getVulnerabilities();

            if (vulns.isEmpty()) {
                break;
            }
            pageConsumer.accept(vulns);
            skip += page.getCount();
        }
    }

    /**
     * Returns all software + machine + cve pairs. This is called for each customer
     * to return the devices / software / CVEs that are actually affecting them, unlike
     * other methods above which return the entire catalog of CVEs regardless of tenant.
     *
     * @param token The access token for the customer tenant
     * @param pageConsumer A callback for each page of vulnerabilities
     */
    public void streamMachineVulnerabilities(String token, Consumer<List<DefenderMachineVulnerability>> pageConsumer) throws IOException, InterruptedException {
        int skip = 0;

        while (true) {
            String url = BASE_URL + "/api/vulnerabilities/machinesVulnerabilities?$skip=" + skip;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = Utils.HTTP.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IllegalStateException("Defender API returned " + response.statusCode() + ": " + response.body());
            }

            DefenderMachinesVulnerabilities apiResponse = Utils.GSON.fromJson(response.body(), DefenderMachinesVulnerabilities.class);
            List<DefenderMachineVulnerability> page = apiResponse.getVulnerabilities();

            if (page == null || page.isEmpty()) {
                break;
            }

            pageConsumer.accept(page);
            skip += apiResponse.getCount();
        }
    }

    /**
     * Retrieves a list of all machines from Defender.
     *
     * @param token The Microsoft OAuth access token for the customer tenant
     * @return A list of machines
     */
    public List<DefenderMachine> getMachines(String token) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/machines"))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> response = Utils.HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IllegalStateException("Defender API returned " + response.statusCode());
        }
        DefenderMachines apiResponse = Utils.GSON.fromJson(response.body(), DefenderMachines.class);
        return apiResponse.getMachines();
    }

    /**
     * Retrieves a single machine from Defender.
     *
     * @param token The Microsoft OAuth access token
     * @param machineId The machine ID to look up
     * @return Machine info
     */
    public DefenderMachine getMachine(String token, String machineId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/machines/" + machineId))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> response = Utils.HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IllegalStateException("Defender API returned " + response.statusCode());
        }
        return Utils.GSON.fromJson(response.body(), DefenderMachine.class);
    }

    /**
     * Retrieves a list of active security recommendations for
     * the customer tenant.
     *
     * @param token The Microsoft OAuth access token
     * @return A list of security recommendations
     */
    public List<DefenderRecommendation> getSecurityRecommendations(String token) throws IOException, InterruptedException {
        String oDataQuery = new ODataQueryBuilder()
                .or("status eq 'Active'")
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/recommendations?" + oDataQuery))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> response = Utils.HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            System.out.println(response.body());
            throw new IllegalStateException("Defender API returned " + response.statusCode());
        }
        DefenderSecurityRecommendations apiResponse = Utils.GSON.fromJson(response.body(), DefenderSecurityRecommendations.class);
        return apiResponse.getRecommendations();
    }

    /**
     * Retrieves a list of all software from Defender for the
     * customer tenant.
     *
     * @param token The Microsoft OAuth access token
     * @return A list of software
     */
    public List<DefenderSoftware> getSoftware(String token) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/software"))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> response = Utils.HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IllegalStateException("Defender API returned " + response.statusCode());
        }
        DefenderSoftwares apiResponse = Utils.GSON.fromJson(response.body(), DefenderSoftwares.class);
        return apiResponse.getSoftwares();
    }
}
