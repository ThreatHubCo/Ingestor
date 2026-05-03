package co.threathub.ingestor.model;

import lombok.Data;

@Data
public class VulnMeta {
    private final String severity;
    private final double epss;
    private final boolean publicExploit;
    private final boolean exploitVerified;
    private final boolean exploitInKit;
}
