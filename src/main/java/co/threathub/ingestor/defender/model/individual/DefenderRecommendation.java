package co.threathub.ingestor.defender.model.individual;

import lombok.Data;

import java.util.List;

@Data
public class DefenderRecommendation {
    private final String id;
    private final String productName;
    private final String recommendationName;
    private final int weaknesses;
    private final String vendor;
    private final boolean publicExploit;
    private final boolean activeAlert;
    private final String remediationType;
    private final double configScoreImpact;
    private final double exposureImpact;
    private final String status;
    private final int totalMachineCount;
    private final int exposedMachinesCount;
    private final String relatedComponent;
    private final boolean hasUnpatchableCve;
    private final List<String> assosciatedThreats;
    private final int exposedCriticalDevices;
}