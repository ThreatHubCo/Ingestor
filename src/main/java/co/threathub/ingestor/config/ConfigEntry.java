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
        return new ConfigEntry(
                rs.getInt("id"),
                ConfigKey.valueOf(rs.getString("key")),
                rs.getString("value"),
                ConfigValueType.of(rs.getString("type")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    public Object getParsedValue() {
        return switch (type) {
            case BOOLEAN -> Boolean.parseBoolean(value);
            case JSON -> value;
            case STRING -> value;
            case INTEGER -> Integer.valueOf(value);
        };
    }
}