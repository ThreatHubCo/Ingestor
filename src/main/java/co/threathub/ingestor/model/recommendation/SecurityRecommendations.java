package co.threathub.ingestor.model.recommendation;

import co.threathub.ingestor.defender.model.individual.DefenderRecommendation;
import lombok.Data;
import java.util.List;

@Data
public class SecurityRecommendations {
    private final String lastSyncAt;
    private final List<DefenderRecommendation> data;
}