package co.threathub.ingestor.model;

import lombok.Data;

import java.sql.ResultSet;
import java.sql.SQLException;

@Data
public class RemediationTicket {
    private final int id;
    private final String createdAt;
    private final RemediationTicketStatus status;
    private final String lastTicketUpdateAt;
    private final String lastSyncAt;
    private final String externalTicketId;
    private final int customerId;
    private final int softwareId;
    private final String notes;

    public static RemediationTicket of(ResultSet rs) throws SQLException {
        return new RemediationTicket(
                rs.getInt("id"),
                rs.getString("created_at"),
                RemediationTicket.RemediationTicketStatus.valueOf(rs.getString("status")),
                rs.getString("last_ticket_update_at"),
                rs.getString("last_sync_at"),
                rs.getString("external_ticket_id"),
                rs.getInt("customer_id"),
                rs.getInt("software_id"),
                rs.getString("notes")
        );
    }

    public enum RemediationTicketStatus {
        OPEN,
        CLOSED_GRACE_PERIOD,
        CLOSED
    }
}
