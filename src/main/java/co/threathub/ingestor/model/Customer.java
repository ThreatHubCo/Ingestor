package co.threathub.ingestor.model;

import lombok.Data;

import java.sql.ResultSet;
import java.sql.SQLException;

@Data
public class Customer implements IModel {
    private final int id;
    private final String createdAt;
    private final String updatedAt;
    private final String deletedAt;
    private final String name;
    private final String tenantId;
    private final String externalCustomerId;
    private final boolean supportsCsp;

    public static Customer of(ResultSet rs) throws SQLException {
        return new Customer(
                rs.getInt("id"),
                rs.getString("created_at"),
                rs.getString("updated_at"),
                rs.getString("deleted_at"),
                rs.getString("name"),
                rs.getString("tenant_id"),
                rs.getString("external_customer_id"),
                rs.getBoolean("supports_csp")
        );
    }
}
