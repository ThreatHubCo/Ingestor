package co.threathub.ingestor.job;

import co.threathub.ingestor.Ingestor;
import co.threathub.ingestor.job.model.ScanJob;
import co.threathub.ingestor.job.model.enums.ScanJobStatus;
import co.threathub.ingestor.log.Logger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class JobWorker implements Runnable {
    private final Ingestor ingestor;

    @Override
    public void run() {
        log.info("Job worker started");

        while (!Thread.currentThread().isInterrupted()) {
            ScanJob job = null;

            try {
                String jobId = JobHelper.popNextJobId();

                if (jobId == null) {
                    Thread.sleep(5 * 1000);
                    continue;
                }

                job = JobHelper.getJobs().stream()
                        .filter(j -> j.getId().equals(jobId))
                        .findFirst()
                        .orElse(null);

                if (job == null) {
                    log.warn("Job {} not found in Redis, skipping", jobId);
                    continue;
                }
                if (job.getStatus() != ScanJobStatus.PENDING) {
                    log.warn("Skipping job {} with status {}", job.getId(), job.getStatus());
                    continue;
                }

                Logger.info(String.format("Processing job %s (Type %s) (Target %s, %s)", job.getId(), job.getType(), job.getTargetType(), job.getTargetId()));

                job.setStatus(ScanJobStatus.RUNNING);
                JobHelper.updateJob(job);

                process(job);

                job.setStatus(ScanJobStatus.COMPLETE);
                JobHelper.updateJob(job);

                Logger.info("Completed job " + job.getId());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.info("Job worker interrupted, shutting down");
                break;
            } catch (Exception ex) {
                if (job != null) {
                    job.setStatus(ScanJobStatus.FAILED);
                    JobHelper.updateJob(job);
                    Logger.error(String.format("Job %s failed", job.getId()), ex);
                } else {
                    Logger.error("Error processing job", ex);
                }
            }
        }

        log.info("Job worker stopped");
    }

    private void process(ScanJob job) throws Exception {
        switch (job.getType()) {
            case GLOBAL_VULN_CATALOG:
                ingestor.getVulnCatalogSyncTask().actuallyRun(job);
                break;
            case ALL_DEVICES:
                ingestor.getDeviceCatalogSyncTask().syncAllTenants(job);
                break;
            case ALL_TICKETS_GLOBAL:
                ingestor.getHaloSyncTask().syncAllTickets(job);
                break;
            case ALL_CUSTOMERS:
                ingestor.getDeviceCatalogSyncTask().syncAllTenants(job);
                ingestor.getSecurityRecSyncTask().syncAllTenants(job);
                ingestor.getHaloSyncTask().syncAllTickets(job);
                ingestor.getVulnExposureSyncTask().syncAllTenants(job);
                break;
            case SINGLE_CUSTOMER:
                ingestor.getDeviceCatalogSyncTask().syncTenant(job);
                ingestor.getSecurityRecSyncTask().syncTenant(job);
                ingestor.getHaloSyncTask().syncSingleCustomer(job);
                ingestor.getVulnExposureSyncTask().syncTenant(job);
                break;
            case ALL_TICKETS_CUSTOMER:
                ingestor.getHaloSyncTask().syncSingleCustomer(job);
                break;
            case DEVICE_CLEANUP:
                ingestor.getDeviceCleanupTask().actuallyRun(job);
                break;
            default:
                throw new IllegalStateException("Unhandled job type: " + job.getType());
        }
    }
}