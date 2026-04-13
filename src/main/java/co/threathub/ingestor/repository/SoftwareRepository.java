package co.threathub.ingestor.repository;

import co.threathub.ingestor.model.Customer;
import co.threathub.ingestor.model.Software;
import co.threathub.ingestor.repository.exception.DatabaseException;
import com.zaxxer.hikari.HikariDataSource;
import co.threathub.ingestor.defender.model.individual.DefenderMachineVulnerability;
import co.threathub.ingestor.defender.model.individual.DefenderSoftware;
import lombok.RequiredArgsConstructor;

import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RequiredArgsConstructor
public class SoftwareRepository {
    private final HikariDataSource dataSource;

    public Software getSoftwareById(int id) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM software WHERE id = ?")) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Software.of(rs);
                }
            }
            return null;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to fetch software with ID " + id, ex);
        }
    }

    /**
     * Insert or update software from the Defender API in the database.
     * TODO / WARNING: This is the old method and not used currently.
     *
     * @param conn         The sql connection to re-use
     * @param defenderData The software information to add or update from Defender
     * @return The ID of the inserted row in the database
     */
    public int insertOrGetSoftwareOld(Connection conn, DefenderSoftware defenderData) {
        String sql = """
            INSERT INTO software (name, vendor) VALUES (?, ?)
            ON DUPLICATE KEY UPDATE name = VALUES(name)
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, defenderData.getName());
            ps.setString(2, defenderData.getVendor());

            int id = -1;

            // If inserted, get generated ID
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    id = rs.getInt(1);
                }
            }

            // If duplicate, fetch the existing ID
            if (id == -1) {
                try (PreparedStatement ps2 = conn.prepareStatement("SELECT id FROM software WHERE name = ?")) {
                    ps2.setString(1, defenderData.getName());

                    try (ResultSet rs = ps2.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Failed to resolve software ID for " + defenderData.getName());
                        }
                        id = rs.getInt(1);
                    }
                }
            }

            // Now update customer specific data
            // TODO: exposedMachines, installedMachines, impactScore
            // TODO: Add a customer_software table
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
        throw new RuntimeException("Failed to insert or update software");
    }

    // Currently the main insert method
    public int insertOrGetSoftwareNew2(Connection conn, String name, String vendor) {
        String sql = "INSERT IGNORE INTO software (name, vendor) VALUES (?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, vendor);
            ps.executeUpdate();

            int id = -1;

            // If inserted, get generated ID
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    id = rs.getInt(1);
                }
            }

            // If duplicate, fetch the existing ID
            if (id == -1) {
                try (PreparedStatement ps2 = conn.prepareStatement("SELECT id FROM software WHERE name = ?")) {
                    ps2.setString(1, name);

                    try (ResultSet rs = ps2.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Failed to resolve software ID for " + name);
                        }
                        id = rs.getInt(1);
                    }
                }
            }

            // Now update customer specific data
            // TODO: exposedMachines, installedMachines, impactScore
            // TODO: Add a customer_software table
            return id;
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void insertVulnerabilityAffectedSoftwareBatch(Connection conn, int vulnerabilityId, List<DefenderMachineVulnerability> affectedSoftwareList) throws SQLException {
        Map<String, Integer> softwareCache = new HashMap<>();

        String sqlVAS = """
            INSERT IGNORE INTO vulnerability_affected_software
            (vulnerability_id, software_id, vulnerable_versions)
            VALUES (?, ?, ?)
        """;

        try (PreparedStatement ps = conn.prepareStatement(sqlVAS)) {
            int batchSize = 0;
            final int BATCH_LIMIT = 500; // execute every 500 rows

            for (DefenderMachineVulnerability mv : affectedSoftwareList) {
                String key = mv.getProductName() + "::" + mv.getProductVendor();
                int softwareId;

                if (softwareCache.containsKey(key)) {
                    softwareId = softwareCache.get(key);
                } else {
                    softwareId = insertOrGetSoftwareNew2(conn, mv.getProductName(), mv.getProductVendor());
                    softwareCache.put(key, softwareId);
                }

                ps.setInt(1, vulnerabilityId);
                ps.setInt(2, softwareId);
                ps.setString(3, "TODO"); // placeholder value
                ps.addBatch();

                batchSize++;

                if (batchSize % BATCH_LIMIT == 0) {
                    ps.executeBatch();
                    ps.clearBatch();
                }
            }

            if (batchSize % BATCH_LIMIT != 0) {
                ps.executeBatch();
                ps.clearBatch();
            }
        }
    }

    public int resolveSoftwareId(String name, String vendor) {
        String sql = "SELECT id FROM software WHERE name = ? AND vendor = ?";

        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, vendor);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Software not found: " + name + " / " + vendor);
                }
                return rs.getInt("id");
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to resolve software ID", ex);
        }
    }
}
