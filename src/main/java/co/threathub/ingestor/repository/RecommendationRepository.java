package co.threathub.ingestor.repository;

import com.zaxxer.hikari.HikariDataSource;
import co.threathub.ingestor.defender.model.individual.DefenderRecommendation;
import co.threathub.ingestor.model.recommendation.CustomerRecommendationMetrics;
import co.threathub.ingestor.model.recommendation.SecurityRecommendations;
import co.threathub.ingestor.model.enums.RecommendationEventType;
import lombok.RequiredArgsConstructor;

import java.sql.*;
import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor
// TODO: This class needs come cleaning up, it's one of the oldest in the project and hasn't received much love!
public class RecommendationRepository implements IRepository {
    private final HikariDataSource dataSource;

    public int upsertSecurityRecommendation(DefenderRecommendation dr) {
        String sql = """
            INSERT INTO security_recommendations
                (defender_recommendation_id, product_name, recommendation_name, vendor, remediation_type, related_component)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                product_name = VALUES(product_name),
                recommendation_name = VALUES(recommendation_name),
                vendor = VALUES(vendor),
                remediation_type = VALUES(remediation_type),
                related_component = VALUES(related_component)
        """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, dr.getId());
            ps.setString(2, dr.getProductName());
            ps.setString(3, dr.getRecommendationName());
            ps.setString(4, dr.getVendor());
            ps.setString(5, dr.getRemediationType());
            ps.setString(6, dr.getRelatedComponent());

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }

            // If duplicate, fetch existing id
            return getRecommendationIdByDefenderId(dr.getId());

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int getRecommendationIdByDefenderId(String defenderRecommendationId) {
        String sql = "SELECT id FROM security_recommendations WHERE defender_recommendation_id = ?";

        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, defenderRecommendationId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
            throw new IllegalStateException("Security recommendation not found for defender id: " + defenderRecommendationId);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<CustomerRecommendationMetrics> getLatestMetrics(int customerId, int recommendationId) {
        String sql = """
            SELECT * FROM customer_security_recommendation_metrics
            WHERE customer_id = ? AND recommendation_id = ?
            ORDER BY created_at DESC
            LIMIT 1
        """;

        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.setInt(2, recommendationId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }

                CustomerRecommendationMetrics m = new CustomerRecommendationMetrics();

                m.setCustomerId(customerId);
                m.setRecommendationId(recommendationId);
                m.setExposedMachinesCount(rs.getInt("exposed_machines_count"));
                m.setTotalMachinesCount(rs.getInt("total_machines_count"));
                m.setExposedCriticalDevices(rs.getInt("exposed_critical_devices"));
                m.setPublicExploit(rs.getBoolean("public_exploit"));
                m.setActiveAlert(rs.getBoolean("active_alert"));
                m.setHasUnpatchableCve(rs.getBoolean("has_unpatchable_cve"));
                m.setStatus(rs.getString("status"));
                m.setWeaknesses(rs.getInt("weaknesses"));

                return Optional.of(m);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void insertEvent(int customerId, int recommendationId, RecommendationEventType type, String oldValue, String newValue) {
        String sql = """
            INSERT INTO customer_security_recommendation_events (customer_id, recommendation_id, field_name, old_value, new_value)
            VALUES (?, ?, ?, ?, ?)
        """;

        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, customerId);
            ps.setInt(2, recommendationId);
            ps.setString(3, type.getFieldName());
            ps.setString(4, oldValue);
            ps.setString(5, newValue);

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void insertMetrics(int customerId, int recommendationId, DefenderRecommendation dr) {
        String sql = """
            INSERT INTO customer_security_recommendation_metrics
                (customer_id, recommendation_id,
                 exposed_machines_count, total_machines_count,
                 exposed_critical_devices, config_score_impact, exposure_impact,
                 public_exploit, active_alert, has_unpatchable_cve, status, weaknesses)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, customerId);
            ps.setInt(2, recommendationId);
            ps.setInt(3, dr.getExposedMachinesCount());
            ps.setInt(4, dr.getTotalMachineCount());
            ps.setInt(5, dr.getExposedCriticalDevices());
            ps.setDouble(6, dr.getConfigScoreImpact());
            ps.setDouble(7, dr.getExposureImpact());
            ps.setBoolean(8, dr.isPublicExploit());
            ps.setBoolean(9, dr.isActiveAlert());
            ps.setBoolean(10, dr.isHasUnpatchableCve());
            ps.setString(11, dr.getStatus());
            ps.setInt(12, dr.getWeaknesses());

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void detectChanges(int customerId, int recommendationId, CustomerRecommendationMetrics oldM, DefenderRecommendation newM) {
        if (oldM.isPublicExploit() != newM.isPublicExploit()) {
            insertEvent(customerId, recommendationId,
                    RecommendationEventType.PUBLIC_EXPLOIT_CHANGED,
                    String.valueOf(oldM.isPublicExploit()),
                    String.valueOf(newM.isPublicExploit()));
        }

        if (oldM.isActiveAlert() != newM.isActiveAlert()) {
            insertEvent(customerId, recommendationId,
                    RecommendationEventType.ACTIVE_ALERT_CHANGED,
                    String.valueOf(oldM.isActiveAlert()),
                    String.valueOf(newM.isActiveAlert()));
        }

        if (oldM.getTotalMachinesCount() != newM.getTotalMachineCount()) {
            insertEvent(customerId, recommendationId,
                    RecommendationEventType.TOTAL_MACHINE_COUNT_CHANGED,
                    String.valueOf(oldM.getTotalMachinesCount()),
                    String.valueOf(newM.getTotalMachineCount()));
        }

        if (oldM.getExposedMachinesCount() != newM.getExposedMachinesCount()) {
            insertEvent(customerId, recommendationId,
                    RecommendationEventType.EXPOSED_MACHINE_COUNT_CHANGED,
                    String.valueOf(oldM.getExposedMachinesCount()),
                    String.valueOf(newM.getExposedMachinesCount()));
        }

        if (oldM.isHasUnpatchableCve() != newM.isHasUnpatchableCve()) {
            insertEvent(customerId, recommendationId,
                    RecommendationEventType.HAS_UNPATCHABLE_CVE_CHANGED,
                    String.valueOf(oldM.isHasUnpatchableCve()),
                    String.valueOf(newM.isHasUnpatchableCve()));
        }

        if (oldM.getExposedCriticalDevices() != newM.getExposedCriticalDevices()) {
            insertEvent(customerId, recommendationId,
                    RecommendationEventType.EXPOSED_CRITICAL_DEVICES_CHANGED,
                    String.valueOf(oldM.getExposedCriticalDevices()),
                    String.valueOf(newM.getExposedCriticalDevices()));
        }

        if (!Objects.equals(oldM.getStatus(), newM.getStatus())) {
            insertEvent(customerId, recommendationId,
                    RecommendationEventType.STATUS_CHANGED,
                    oldM.getStatus(),
                    newM.getStatus());
        }
    }

}
