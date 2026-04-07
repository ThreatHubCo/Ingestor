package co.threathub.ingestor.model.recommendation;

import lombok.Data;

@Data
public class CustomerRecommendationEvent {
    private int customerId;
    private int recommendationId;
    private String fieldName;
    private String oldValue;
    private String newValue;
}