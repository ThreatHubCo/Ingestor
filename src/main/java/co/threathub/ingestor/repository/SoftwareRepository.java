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

    public int resolveSoftwareId(Connection conn, String name, String vendor) throws SQLException {
        String selectSql = "SELECT id FROM software WHERE name = ? AND (vendor <=> ?)";

        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, name);
            ps.setString(2, vendor);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }

        // Insert if doesn't exist
        String insertSql = "INSERT INTO software (name, vendor) VALUES (?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, vendor);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to resolve software");
    }
}
