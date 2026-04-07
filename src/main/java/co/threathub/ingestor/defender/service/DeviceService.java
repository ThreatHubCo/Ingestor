package co.threathub.ingestor.defender.service;

import co.threathub.ingestor.defender.DefenderClient;
import co.threathub.ingestor.defender.model.individual.DefenderMachine;
import co.threathub.ingestor.util.Utils;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class DeviceService {
    private final DefenderClient defenderClient;

    public List<DefenderMachine> fetchAllMachines(String tenantId) throws IOException, InterruptedException {
        String accessToken = defenderClient.clientTenantAuth(tenantId);
        return defenderClient.getMachines(accessToken);
    }
}
