package co.threathub.ingestor.repository;

import co.threathub.ingestor.repository.exception.DatabaseException;
import com.zaxxer.hikari.HikariDataSource;
import co.threathub.ingestor.model.Device;
import co.threathub.ingestor.defender.model.individual.DefenderMachine;
import co.threathub.ingestor.util.Utils;
import lombok.RequiredArgsConstructor;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class DeviceRepository {
    private final HikariDataSource dataSource;

    /**
     * Loads all device Machine ID + Database ID from the database into memory.
     *
     * @param conn The sql connection to re-use
     * @return A map of defender machine ids to Database ids
     */
    public Map<String, Integer> loadAllDeviceIds(Connection conn) throws SQLException {
        String sql = "SELECT id, machine_id FROM devices";

        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            Map<String, Integer> result = new HashMap<>();

            while (rs.next()) {
                result.put(rs.getString("machine_id"), rs.getInt("id"));
            }
            return result;
        }
    }

    /**
     * Inserts a device into the database based on data from Defender.
     *
     * @param customerId The customer the device is associated with
     * @param d The device information
     */
    public void insertDefenderDevice(int customerId, DefenderMachine d) {
        String sql = """
            INSERT INTO devices (
                customer_id, machine_id, dns_name, os_platform, os_version, os_build,
                is_aad_joined, aad_device_id, last_seen_at, updated_at,
                last_sync_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
                os_platform = VALUES(os_platform),
                os_version = VALUES(os_version),
                os_build = VALUES(os_build),
                is_aad_joined = VALUES(is_aad_joined),
                aad_device_id = VALUES(aad_device_id),
                last_seen_at = VALUES(last_seen_at),
                updated_at = NOW(),
                last_sync_at = NOW()
        """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, customerId);
            ps.setString(2, d.getId());
            ps.setString(3, d.getComputerDnsName());
            ps.setString(4, d.getOsPlatform());
            ps.setString(5, d.getVersion());
            ps.setString(6, d.getOsProcessor());
            ps.setBoolean(7, d.isAadJoined());
            ps.setString(8, d.getAadDeviceId());
//            ps.setTimestamp(9, ts(d.getFirstSeen()));
            ps.setTimestamp(9, Utils.parseSqlTimestamp(d.getLastSeen()));

            ps.executeUpdate();

        } catch (SQLException ex) {
            throw new DatabaseException("Failed to insert device", ex);
        }
    }

    public List<Device> getAllDevices() {
        List<Device> list = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM devices")) {

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(Device.of(rs));
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to fetch devices", ex);
        }
        return list;
    }

    /**
     * Delete a device from the database.
     *
     * @param id The device ID
     */
    public void removeDevice(int id) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM devices WHERE id = ?")) {

            ps.setInt(1, id);
            ps.execute();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to delete device", ex);
        }
    }
}
