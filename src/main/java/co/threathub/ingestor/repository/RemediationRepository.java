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

    /**
     * Update the status of a ticket.
     *
     * @param ticketId The ID of the ticket to update
     * @param status The new status of the ticket
     */
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