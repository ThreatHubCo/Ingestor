package co.threathub.ingestor.config;

public enum ConfigValueType {
    STRING,
    BOOLEAN,
    JSON,
    INTEGER;

    public static ConfigValueType of(String value) {
        return ConfigValueType.valueOf(value.toUpperCase());
    }
}