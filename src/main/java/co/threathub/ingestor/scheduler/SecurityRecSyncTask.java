package co.threathub.ingestor.scheduler;

import co.threathub.ingestor.Ingestor;
import co.threathub.ingestor.defender.DefenderClient;
import co.threathub.ingestor.defender.model.individual.DefenderRecommendation;
import co.threathub.ingestor.job.JobHelper;
import co.threathub.ingestor.job.model.ScanJob;
import co.threathub.ingestor.job.model.enums.ScanJobStatus;
import co.threathub.ingestor.job.model.enums.ScanTargetType;
import co.threathub.ingestor.job.model.enums.ScanType;
import co.threathub.ingestor.log.Logger;
import co.threathub.ingestor.model.Customer;
import co.threathub.ingestor.model.recommendation.CustomerRecommendationMetrics;
import co.threathub.ingestor.model.recommendation.SecurityRecommendations;
import co.threathub.ingestor.repository.CustomerRepository;
import co.threathub.ingestor.repository.RecommendationRepository;
import co.threathub.ingestor.util.JedisManager;
import co.threathub.ingestor.util.Utils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SecurityRecSyncTask implements ITask {
    private static final long PROGRESS_UPDATE_INTERVAL = 500;
    private static final int MIN_PERCENT_STEP = 1;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Ingestor ingestor;

    public SecurityRecSyncTask(Ingestor ingestor) {
        this.ingestor = ingestor;
        this.schedule();
    }

    public void schedule() {
        long intialDelay = Utils.computeInitialDelay(5, 0); // 5am
        long period = TimeUnit.DAYS.toMillis(1); // 24 hours

        // TODO: Remove
        ScanJob job = new ScanJob();
        job.setType(ScanType.ALL_RECOMMENDATIONS);
        job.setTargetType(ScanTargetType.SYSTEM);
        job.setTargetId(null);
        job.setStatus(ScanJobStatus.PENDING);
        job.setProgress(0);
        job.setMessage("Job created");

        scheduler.scheduleAtFixedRate(() -> this.syncAllTenants(job), intialDelay, period, TimeUnit.MILLISECONDS);
    }

    @Override
    public void execute() {
        this.enqueueSystemScan();
    }

    private void enqueueSystemScan() {
        ScanJob job = new ScanJob();
        job.setType(ScanType.ALL_RECOMMENDATIONS);
        job.setTargetType(ScanTargetType.SYSTEM);
        job.setTargetId(null);
        job.setStatus(ScanJobStatus.PENDING);
        job.setProgress(0);
        job.setMessage("Job created");

        JobHelper.addJob(job);
    }

    public void syncAllTenants(ScanJob job) {
        Logger.info("Starting task");
        long start = System.currentTimeMillis();

        CustomerRepository customerRepository = ingestor.getCustomerRepository();

        try {
            List<Customer> customers = customerRepository.getAllCustomers();

            int totalCustomers = customers.size();
            int customerIndex = 0;

            for (Customer customer : customers) {
                Logger.info(String.format("Processing customer %s (%s/%s)", customer.getName(), ++customerIndex, totalCustomers), customer.getId());

                if (job != null) {
                    job.setStatus(ScanJobStatus.RUNNING);
                    job.setProgress(0);
                    job.setMessage("Job started with " + totalCustomers + " customers");
                    JobHelper.updateJob(job);
                }

                try {
                    processCustomer(job, customer);
                } catch (Exception ex) {
                    Logger.error(String.format("Failed to process customer %s", customer.getName()), ex, customer.getId());
                }

                if (job != null) {
//                    job.setStatus(ScanJobStatus.COMPLETE);
                    JobHelper.updateJob(job);
                }
            }

            long end = System.currentTimeMillis();
            long duration = end - start;
            Logger.info(String.format("Finished task (Took %dms)", duration));
        } catch (Exception ex) {
            Logger.error("Security recommendations sync failed", ex);

            if (job != null) {
//                job.setStatus(ScanJobStatus.FAILED);
                JobHelper.updateJob(job);
            }
        }
    }

    public void syncTenant(ScanJob job) {
        Logger.info("Starting task");
        long start = System.currentTimeMillis();

        CustomerRepository customerRepository = ingestor.getCustomerRepository();

        try {
            Customer customer = customerRepository.getCustomerById(job.getTargetId());

            if (customer == null) {
                throw new RuntimeException("Customer not found");
            }

            Logger.info("Processing customer " + customer.getName(), customer.getId());

            if (job != null) {
                job.setStatus(ScanJobStatus.RUNNING);
                job.setProgress(0);
                job.setMessage("Job started");
                JobHelper.updateJob(job);
            }

            try {
                processCustomer(job, customer);
            } catch (Exception ex) {
                Logger.error(String.format("Failed to process customer %s", customer.getName()), ex, customer.getId());
            }

            if (job != null) {
//                job.setStatus(ScanJobStatus.COMPLETE);
                JobHelper.updateJob(job);
            }

            long end = System.currentTimeMillis();
            long duration = end - start;
            Logger.info(String.format("Finished task (Took %dms)", duration));
        } catch (Exception ex) {
            Logger.error("Security recommendations sync failed", ex);

            if (job != null) {
//                job.setStatus(ScanJobStatus.FAILED);
                JobHelper.updateJob(job);
            }
        }
    }

    public void processCustomer(ScanJob job, Customer customer) throws IOException, InterruptedException {
        job.setStatus(ScanJobStatus.RUNNING);
        job.setMessage(String.format("Processing customer: %s", customer.getName()));
        JobHelper.updateJob(job);

        RecommendationRepository recommendationRepository = ingestor.getRecommendationRepository();
        DefenderClient client = ingestor.getDefenderClient();

        String accessToken = client.clientTenantAuth(customer.getTenantId());
        List<DefenderRecommendation> recommendations = client.getSecurityRecommendations(accessToken);

        for (DefenderRecommendation defenderRecommendation : recommendations) {
            int recommendationId = recommendationRepository.upsertSecurityRecommendation(defenderRecommendation);
            Optional<CustomerRecommendationMetrics> existing = recommendationRepository.getLatestMetrics(customer.getId(), recommendationId);

            existing.ifPresent(oldMetrics ->
                    recommendationRepository.detectChanges(customer.getId(), recommendationId, oldMetrics, defenderRecommendation)
            );

            recommendationRepository.insertMetrics(
                    customer.getId(),
                    recommendationId,
                    defenderRecommendation
            );
        }

        SecurityRecommendations data = new SecurityRecommendations(Instant.now().toString(), recommendations);
        try {
            JedisManager.getConnection().set("customers:" + customer.getId() + ":recommendations", Utils.GSON.toJson(data));
        } catch (Exception ex) {
            Logger.error(String.format("Failed to write recommendations to Redis for customer %s", customer.getName()), ex, customer.getId());
        }
    }
}