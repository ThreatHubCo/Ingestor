package co.threathub.ingestor.defender.model.individual;

import co.threathub.ingestor.defender.model.IDefenderModel;
import lombok.Data;

@Data
public class DefenderSoftware implements IDefenderModel {
    private final String id;
    private final String name;
    private final String vendor;
    private final boolean publicExploit;
    private final boolean activeAlert;
}