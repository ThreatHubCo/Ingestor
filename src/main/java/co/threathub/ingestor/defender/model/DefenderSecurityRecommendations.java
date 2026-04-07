package co.threathub.ingestor.defender.model;

import com.google.gson.annotations.SerializedName;
import co.threathub.ingestor.defender.model.individual.DefenderRecommendation;
import lombok.Data;

import java.util.List;

@Data
public class DefenderSecurityRecommendations implements IDefenderModel {
    @SerializedName("@odata.count")
    private final String count;

    @SerializedName("value")
    private List<DefenderRecommendation> recommendations;
}
