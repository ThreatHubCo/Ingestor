package co.threathub.ingestor.model;

import lombok.Data;

@Data
public class DeviceVulnKey {
    public final String machineId;
    public final int vulnerabilityId;
    public final int softwareId;
}