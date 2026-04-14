package co.threathub.ingestor.defender.model.individual;

import co.threathub.ingestor.defender.model.IDefenderModel;
import lombok.Data;

@Data
public class DefenderMachine implements IDefenderModel {
    private final String id;
    private final String computerDnsName;
    private final String firstSeen;
    private final String lastSeen;
    private final String osPlatform;
    private final String osProcessor;
    private final String version;
    private final boolean isAadJoined;
    private final String aadDeviceId;
    private final VmMetadata vmMetadata;

    @Data
    public static class VmMetadata {
        private final String vmId;
        private final String cloudProvider;
        private final String resourceId;
        private final String subscriptionId;
    }
}