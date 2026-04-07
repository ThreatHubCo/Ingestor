package co.threathub.ingestor.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * NOTE: This should match EXACTLY to the ConfigKey enum and DEFAULT_CONFIG
 * variable in the ThreatHub Web application to avoid things getting out of sync.
 */
@Getter
@RequiredArgsConstructor
public enum ConfigKey {
    ENABLE_PASSWORD_AUTH(true),
    ENABLE_MICROSOFT_AUTH(true),
    ENABLE_TICKETING(false),

    TICKET_SYSTEM_TYPE(null),
    TICKET_SYSTEM_URL(""),
    TICKET_SYSTEM_CLIENT_ID(""),
    TICKET_SYSTEM_CLIENT_SECRET(""),
    MIN_CVE_SEVERITY_FOR_ESCALATION("High"),
//    WAIT_TIME_BEFORE_ESCALATION(7),
    ESCALATE_PUBLIC_EXPLOIT_IMMEDIATELY(true),

    HOME_TENANT_ID(""),
    SITE_URL(""),

    ENTRA_AUTH_CLIENT_ID(""),
    ENTRA_AUTH_CLIENT_SECRET(""),
    ENTRA_BACKEND_CLIENT_ID(""),
    ENTRA_BACKEND_CLIENT_SECRET(""),

    DEV_LOGGING_ENABLED(false),
    DEV_LOGGING_URL(""),

    INSTANCE_ID(""),
    EXTERNAL_LOG_FORWARDING(false),
    SEND_EXTERNAL_HEARTBEAT(false),

    SKIP_NON_ENTRA_JOINED_DEVICES(true);

    private final Object defaultValue;
}