package co.threathub.ingestor.defender.model;

import com.google.gson.annotations.SerializedName;
import co.threathub.ingestor.defender.model.individual.DefenderMachineVulnerability;
import lombok.Data;

import java.util.List;

@Data
public class DefenderMachinesVulnerabilities implements IDefenderModel {
    @SerializedName("@odata.count")
    private final int count;

    @SerializedName("value")
    private final List<DefenderMachineVulnerability> vulnerabilities;
}
