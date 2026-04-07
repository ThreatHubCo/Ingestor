package co.threathub.ingestor.defender.model;

import com.google.gson.annotations.SerializedName;
import co.threathub.ingestor.defender.model.individual.DefenderVulnerability;
import lombok.Data;

import java.util.List;

@Data
public class DefenderVulnerabilities implements IDefenderModel {
    @SerializedName("@odata.count")
    private final int count;

    @SerializedName("value")
    private List<DefenderVulnerability> vulnerabilities;
}
