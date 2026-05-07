package co.threathub.ingestor.repository;

import co.threathub.ingestor.model.Customer;
import co.threathub.ingestor.model.Software;
import co.threathub.ingestor.repository.exception.DatabaseException;
import com.zaxxer.hikari.HikariDataSource;
import co.threathub.ingestor.defender.model.individual.DefenderMachineVulnerability;
import co.threathub.ingestor.defender.model.individual.DefenderSoftware;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.RequiredArgsConstructor;

import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RequiredArgsConstructor
public class SoftwareRepository {
    private final HikariDataSource dataSource;

    /**
     * Loads all software Name & Vendor + Database ID from the database into memory.
     *
     * @param conn The sql connection to re-use
     * @return A map of software keys (name & vendor) to Database ids
     */
    public Object2IntMap<String> loadAllSoftwareIds(Connection conn) throws SQLException {
        String sql = "SELECT id, name, vendor FROM software";

        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            Object2IntMap<String> result = new Object2IntOpenHashMap<>();

            while (rs.next()) {
                String key = rs.getString("name") + ":" + rs.getString("vendor");
                result.put(key, rs.getInt("id"));
            }
            return result;
        }
    }

    /**
     * Retrieve information about a software with the given ID.
     *
     * @param id The ID of the software to look up
     * @return The software information
     */
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

    public int insertSoftware(Connection conn, String name, String vendor) throws SQLException {
        String insertSql = "INSERT IGNORE INTO software (name, vendor) VALUES (?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, vendor);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                throw new SQLException("Failed to insert software");
            }
        }
    }
}
