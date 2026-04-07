package co.threathub.ingestor.log;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BackendLog {
    private String level;
    private String source;
    private String text;
    private Integer customerId;
}