package co.threathub.ingestor.model.recommendation;

import lombok.Data;

@Data
public class SecurityRecommendation {
    private int id;
    private String defenderRecommendationId;
    private String productName;
    private String recommendationName;
    private String vendor;
    private String remediationType;
    private String relatedComponent;
}