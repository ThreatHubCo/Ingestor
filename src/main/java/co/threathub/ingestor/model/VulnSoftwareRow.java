package co.threathub.ingestor.model;

import lombok.Data;

@Data
public class VulnSoftwareRow {
    private final int customerId;
    private final int vulnId;
    private final int softwareId;
}
