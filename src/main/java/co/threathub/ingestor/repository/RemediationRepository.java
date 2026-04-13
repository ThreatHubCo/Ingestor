package co.threathub.ingestor.repository;

import co.threathub.ingestor.repository.exception.DatabaseException;
import com.zaxxer.hikari.HikariDataSource;
import co.threathub.ingestor.model.Customer;
import co.threathub.ingestor.model.RemediationTicket;
import lombok.RequiredArgsConstructor;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class RemediationRepository {
    private final HikariDataSource dataSource;

    /**
     * Inserts information about a ticket into the database.
     *
     * @param ticket The ticket information
     */
    public void insertTicket(RemediationTicket ticket) {
        String sql = """
            INSERT INTO remediation_tickets (customer_id, software_id, external_ticket_id, status, notes, last_ticket_update_at, last_sync_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, ticket.getCustomerId());
            ps.setInt(2, ticket.getSoftwareId());
            ps.setString(3, ticket.getExternalTicketId());
            ps.setString(4, ticket.getStatus().name());
            ps.setString(5, ticket.getNotes());

            Timestamp now = Timestamp.from(Instant.now());
            ps.setTimestamp(6, now);
            ps.setTimestamp(7, now);

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to insert ticket", e);
        }
    }

    /**
     * Returns the highest severity based on all CVEs for a given software.
     * TODO: Should this be moved to a different class?
     *
     * @param customerId The customer ID
     * @param softwareId The software ID
     * @return The highest severity, e.g. "Critical"
     */
    public String getHighestCveSeverityForSoftware(int customerId, int softwareId) throws SQLException {
        String sql = """
            SELECT v.severity
            FROM vulnerabilities v
            INNER JOIN vulnerability_affected_software vas ON vas.vulnerability_id = v.id
            INNER JOIN device_vulnerabilities dv ON dv.vulnerability_id = v.id
            INNER JOIN devices d ON d.id = dv.device_id
            WHERE vas.software_id = ?
              AND d.customer_id = ?
              AND dv.status IN ('OPEN', 'RE_OPENED')
              AND d.last_seen_at >= NOW() - INTERVAL 30 DAY
        """;

        Map<String, Integer> severityRank = Map.of(
                "Low", 1,
                "Medium", 2,
                "High", 3,
                "Critical", 4
        );

        int highestRank = 0;
        String highestSeverity = null;

        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, softwareId);
            ps.setInt(2, customerId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String severity = rs.getString("severity");
                    int rank = severityRank.getOrDefault(severity, 0);

                    if (rank > highestRank) {
                        highestRank = rank;
                        highestSeverity = severity;
                    }
                }
            }
        }
        return highestSeverity;
    }

    /**
     * Returns the total number of vulnerable devices for the given software.
     * TODO: Should this be moved to a different class?
     *
     * @param customerId The customer ID
     * @param softwareId The software ID
     * @return The number of vulnerable devices
     */
    public int getAffectedDeviceCountForSoftware(int customerId, int softwareId) throws SQLException {
        String sql = """
            SELECT COUNT(DISTINCT d.id) AS affected_device_count
            FROM devices d
            INNER JOIN device_vulnerabilities dv ON dv.device_id = d.id
            INNER JOIN vulnerability_affected_software vas ON vas.vulnerability_id = dv.vulnerability_id
            INNER JOIN vulnerabilities v ON v.id = dv.vulnerability_id
            WHERE d.customer_id = ?
              AND vas.software_id = ?
              AND dv.status IN ('OPEN', 'RE_OPENED')
              AND d.last_seen_at >= NOW() - INTERVAL 30 DAY
        """;

        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.setInt(2, softwareId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("affected_device_count");
                }
            }
        }
        return 0;
    }

    /**
     * Returns the total number of vulnerabilities that are eliible to be escalated
     * based on certain criteria.
     * TODO: Should this be moved to a different class?
     *
     * @param customerId The customer ID
     * @param softwareId The software ID
     * @return The total number of CVEs
     */
    public int getEscalatableVulnerabilityCountForSoftware(int customerId, int softwareId) throws SQLException {
        String sql = """
            SELECT COUNT(DISTINCT v.id) AS vuln_count
            FROM vulnerabilities v
            INNER JOIN vulnerability_affected_software vas ON vas.vulnerability_id = v.id
            INNER JOIN device_vulnerabilities dv ON dv.vulnerability_id = v.id
            INNER JOIN devices d ON d.id = dv.device_id
            WHERE vas.software_id = ?
              AND d.customer_id = ?
              AND d.last_seen_at >= NOW() - INTERVAL 30 DAY
              AND dv.status NOT IN ('RESOLVED', 'AUTO_RESOLVED')
              AND (
                  v.public_exploit = TRUE
                  OR (v.public_exploit = FALSE AND v.first_detected_at <= NOW() - INTERVAL 7 DAY)
              )
        """;

        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, softwareId);
            ps.setInt(2, customerId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("vuln_count");
                }
            }
        }
        return 0;
    }

    public List<co.threathub.ingestor.model.Vulnerability> getEscalatableVulnerabilitiesForSoftware(int customerId, int softwareId) throws SQLException {
        String sql = """
            SELECT DISTINCT v.*
            FROM vulnerabilities v
            JOIN vulnerability_affected_software vas ON vas.vulnerability_id = v.id
            JOIN device_vulnerabilities dv ON dv.vulnerability_id = v.id
            JOIN devices d ON d.id = dv.device_id
            WHERE vas.software_id = ?
              AND d.customer_id = ?
              AND d.last_seen_at >= NOW() - INTERVAL 30 DAY
              AND dv.status NOT IN ('RESOLVED', 'AUTO_RESOLVED')
              AND (
                  v.public_exploit = TRUE
                  OR (v.public_exploit = FALSE AND v.first_detected_at <= NOW() - INTERVAL 7 DAY)
              )
        """;

        List<co.threathub.ingestor.model.Vulnerability> vulnerabilities = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, softwareId);
                ps.setInt(2, customerId);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        vulnerabilities.add(co.threathub.ingestor.model.Vulnerability.of(rs));
                    }
                }
            }
        }
        return vulnerabilities;
    }

    /**
     * Returns a list of all tickets that are open or in grace period.
     *
     * @return A list of tickets
     */
    public List<RemediationTicket> getAllTickets() {
        List<RemediationTicket> list = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM remediation_tickets WHERE status != 'CLOSED'");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(RemediationTicket.of(rs));
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to fetch tickets", ex);
        }
        return list;
    }

    /**
     * Returns a list of all tickets that are open or in grace period
     * for a specific customer.
     *
     * @param customer The customer
     * @return A list of tickets
     */
    public List<RemediationTicket> getAllTicketsForCustomer(Customer customer) {
        List<RemediationTicket> list = new ArrayList<>();
        String sql = "SELECT * FROM remediation_tickets WHERE customer_id = ? AND status != 'CLOSED'";

        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customer.getId());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(RemediationTicket.of(rs));
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to fetch tickets", ex);
        }
        return list;
    }

    public void updateStatus(int ticketId, RemediationTicket.RemediationTicketStatus status) {
        String sql = "UPDATE remediation_tickets SET status = ?, last_sync_at = ? WHERE id = ?";

        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setInt(3, ticketId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update ticket status", e);
        }
    }
}