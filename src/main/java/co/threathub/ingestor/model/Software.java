package co.threathub.ingestor.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.sql.ResultSet;
import java.sql.SQLException;

@Data
public class Software implements IModel {
    private final int id;
    private final String name;
    private final String vendor;
    private final String notes;
    private final boolean autoTicketEscalationEnabled;

    public static Software of(ResultSet rs) throws SQLException {
        return new Software(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("vendor"),
                rs.getString("notes"),
                rs.getBoolean("auto_ticket_escalation_enabled")
        );
    }
}
