package co.threathub.ingestor.job.model;

import co.threathub.ingestor.job.JobHelper;
import co.threathub.ingestor.job.model.enums.ScanJobStatus;
import co.threathub.ingestor.job.model.enums.ScanTargetType;
import co.threathub.ingestor.job.model.enums.ScanType;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScanJob {
    private String id;
    private ScanType type;
    private ScanTargetType targetType;
    private Integer targetId;
    private String requestedBy;
    private ScanJobStatus status;
    private int progress;
    private String message;
    private String createdAt;

    public void updateProgress(int percent, String message) {
        this.progress = percent;
        this.message = message;

        JobHelper.updateJob(this);
    }
}