package co.threathub.ingestor.model;

import co.threathub.ingestor.defender.model.IDefenderModel;
import co.threathub.ingestor.model.enums.DeviceVulnerabilityStatus;
import lombok.Data;

import java.sql.ResultSet;
import java.sql.SQLException;

@Data
public class Device implements IModel {
    private final int id;
    private final String createdAt;
    private final String updatedAt;
    private final String lastSeenAt;
    private final int customerId;
    private final String machineId;
    private final String dnsName;
    private final String osPlatform;
    private final String osVersion;
    private final String osBuild;
    private final boolean isAadJoined;
    private final String aadDeviceId;
    private final String lastSyncAt;

    public static Device of(ResultSet rs) throws SQLException {
        return new Device(
                rs.getInt("id"),
                rs.getString("created_at"),
                rs.getString("updated_at"),
                rs.getString("last_seen_at"),
                rs.getInt("customer_id"),
                rs.getString("machine_id"),
                rs.getString("dns_name"),
                rs.getString("os_platform"),
                rs.getString("os_version"),
                rs.getString("os_build"),
                rs.getBoolean("is_aad_joined"),
                rs.getString("aad_device_id"),
                rs.getString("last_sync_at")
        );
    }

    @Data
    public static class Note {
        private final int id;
        private final String createdAt;
        private final String updatedAt;
        private final String deletedAt;
        private final int deletedBy;
        private final int agentId;
        private final String text;
    }

    @Data
    public static class Vulnerability {
        private final int id;
        private final int vulnerabilityId;
        private final String detectedAt;
        private final DeviceVulnerabilityStatus status;
    }
}
