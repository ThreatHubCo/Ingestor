package co.threathub.ingestor.defender.model;

import com.google.gson.annotations.SerializedName;
import co.threathub.ingestor.defender.model.individual.DefenderSoftware;
import lombok.Data;

import java.util.List;

@Data
public class DefenderSoftwares implements IDefenderModel {
    @SerializedName("value")
    private final List<DefenderSoftware> softwares;
}
