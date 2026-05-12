package co.threathub.ingestor.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

@Getter
@AllArgsConstructor
public class ConfigEntry {
    private int id;
    private ConfigKey key;
    private String value;
    private ConfigValueType type;
    private Instant createdAt;
    private Instant updatedAt;

    public static ConfigEntry of(ResultSet rs) throws SQLException {
        ConfigKey key;

        try {
            key = ConfigKey.valueOf(rs.getString("key"));
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }

        return new ConfigEntry(
                rs.getInt("id"),
                key,
                rs.getString("value"),
                ConfigValueType.of(rs.getString("type")),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
                rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null
        );
    }

    public Object getParsedValue() {
        return switch (type) {
            case BOOLEAN -> Boolean.parseBoolean(value);
            case JSON -> value;
            case STRING -> value;
            case NUMBER -> Integer.valueOf(value);
        };
    }
}