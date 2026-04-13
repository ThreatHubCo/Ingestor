package co.threathub.ingestor.config;

import com.zaxxer.hikari.HikariDataSource;
import co.threathub.ingestor.repository.exception.DatabaseException;
import lombok.RequiredArgsConstructor;

import java.sql.*;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

@RequiredArgsConstructor
public class ConfigRepository {
    private final HikariDataSource dataSource;

    public ConfigEntry upsert(ConfigKey key, String value, ConfigValueType type) {
        String sql = """
            INSERT INTO config (`key`, value, `type`, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                value = VALUES(value),
                type = VALUES(type),
                updated_at = VALUES(updated_at)
        """;

        Instant now = Instant.now();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, key.name());
            ps.setString(2, value);
            ps.setString(3, type.name());
            ps.setTimestamp(4, Timestamp.from(now));
            ps.setTimestamp(5, Timestamp.from(now));

            ps.executeUpdate();

            try (PreparedStatement select = conn.prepareStatement(
                    "SELECT * FROM config WHERE key = ?"
            )) {
                select.setString(1, key.name());
                try (ResultSet rs = select.executeQuery()) {
                    if (rs.next()) {
                        return ConfigEntry.of(rs);
                    }
                    throw new DatabaseException("Upsert succeeded but row not found");
                }
            }

        } catch (Exception ex) {
            throw new DatabaseException("Failed to upsert config key " + key, ex);
        }
    }

    /**
     * Returns all values in the config.
     */
    public Map<ConfigKey, ConfigEntry> getAll() {
        Map<ConfigKey, ConfigEntry> map = new EnumMap<>(ConfigKey.class);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM config");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                ConfigEntry entry = ConfigEntry.of(rs);
                map.put(entry.getKey(), entry);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
        return map;
    }
}