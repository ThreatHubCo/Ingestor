package co.threathub.ingestor.defender.model;

import com.google.gson.annotations.SerializedName;
import co.threathub.ingestor.defender.model.individual.DefenderMachine;
import lombok.Data;

import java.util.List;

@Data
public class DefenderMachines implements IDefenderModel {
    @SerializedName("value")
    private final List<DefenderMachine> machines;
}
