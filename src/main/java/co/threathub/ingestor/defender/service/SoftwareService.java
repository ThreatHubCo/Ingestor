package co.threathub.ingestor.defender.service;

import co.threathub.ingestor.defender.DefenderClient;
import co.threathub.ingestor.defender.model.individual.DefenderMachine;
import co.threathub.ingestor.defender.model.individual.DefenderSoftware;
import co.threathub.ingestor.util.Utils;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class SoftwareService {
    private final DefenderClient defenderClient;

    public List<DefenderSoftware> fetchAllSoftware() throws IOException, InterruptedException {
        String accessToken = defenderClient.clientTenantAuth(Utils.getHomeTenantId());
        return defenderClient.getSoftware(accessToken);
    }
}
