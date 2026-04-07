package co.threathub.ingestor.model.recommendation;

import lombok.Data;

@Data
public class CustomerRecommendationMetrics {
    private int customerId;
    private int recommendationId;
    private int exposedMachinesCount;
    private int totalMachinesCount;
    private int exposedCriticalDevices;
    private boolean publicExploit;
    private boolean activeAlert;
    private boolean hasUnpatchableCve;
    private String status;
    private int weaknesses;
}