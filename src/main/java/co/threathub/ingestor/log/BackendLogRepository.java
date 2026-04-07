package co.threathub.ingestor.log;

import com.zaxxer.hikari.HikariDataSource;
import co.threathub.ingestor.reporting.ReportingService;
import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BackendLogRepository {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final HikariDataSource dataSource;

    private final List<BackendLog> batch = new ArrayList<>();
    private final int batchSize = 50; // Flush after 50 logs

    public BackendLogRepository(HikariDataSource dataSource) {
        this.dataSource = dataSource;

        // Flush logs every 5 seconds
        scheduler.scheduleAtFixedRate(this::flush, 5, 5, TimeUnit.SECONDS);
    }

    public synchronized void insert(BackendLog log) {
        batch.add(log);
        if (batch.size() >= batchSize) {
            flush();
        }
    }

    public synchronized void flush() {
        if (batch.isEmpty()) {
            return;
        }

        String sql = """
            INSERT INTO backend_logs (level, source, text, customer_id)
            VALUES (?, ?, ?, ?)
        """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (BackendLog log : batch) {
                ps.setString(1, log.getLevel());
                ps.setString(2, log.getSource());
                ps.setString(3, log.getText());
                ps.setObject(4, log.getCustomerId());
                ps.addBatch();
            }

            ps.executeBatch();
            batch.clear();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}