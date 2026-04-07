package co.threathub.ingestor.model.enums;

import lombok.Getter;

public enum RecommendationEventType {
    PUBLIC_EXPLOIT_CHANGED("public_exploit"),
    ACTIVE_ALERT_CHANGED("active_alert"),
    TOTAL_MACHINE_COUNT_CHANGED("total_machines_count"),
    EXPOSED_MACHINE_COUNT_CHANGED("exposed_machines_count"),
    HAS_UNPATCHABLE_CVE_CHANGED("has_unpatchable_cve"),
    EXPOSED_CRITICAL_DEVICES_CHANGED("exposed_critical_devices"),
    EXPOSURE_IMPACT_CHANGED("exposure_impact"),
    CONFIG_SCORE_IMPACT_CHANGED("config_score_impact"),
    STATUS_CHANGED("status");

    @Getter
    private final String fieldName;

    RecommendationEventType(String fieldName) {
        this.fieldName = fieldName;
    }
}
