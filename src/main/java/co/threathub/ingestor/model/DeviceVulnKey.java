package co.threathub.ingestor.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
public class DeviceVulnKey {
    public final int deviceId;
    public final int vulnerabilityId;
    public final int softwareId;
}