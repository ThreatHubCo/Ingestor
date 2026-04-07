package co.threathub.ingestor.job.model;

public class NoOpScanJob extends ScanJob {

    @Override
    public void updateProgress(int percent, String message) {
        // Do nothing
    }
}