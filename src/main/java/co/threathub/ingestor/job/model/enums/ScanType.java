package co.threathub.ingestor.job.model.enums;

public enum ScanType {
    /**
     * Sync entire software and vulnerability catalog using the home tenant credentials
     */
    GLOBAL_CATALOG_ALL,
    /**
     * Sync entire software catalog using the home tenant credentials
     */
    GLOBAL_SOFTWARE_CATALOG,
    /**
     * Sync entire vulnerability catalog using the home tenant credentials
     */
    GLOBAL_VULN_CATALOG,
    /**
     * Sync current vulnerabilities for all customers
     */
    ALL_CUSTOMERS,
    /**
     * Sync current vulnerabilities for a single customer
     */
    SINGLE_CUSTOMER,
    SINGLE_VULNERABILITY,
    SINGLE_RECOMMENDATION,
    /**
     * Sync all security recommendations for all customers
     */
    ALL_RECOMMENDATIONS,
    ALL_VULNERABILITIES,
    SINGLE_DEVICE,
    ALL_DEVICES,
    DEVICE_CLEANUP,
    SINGLE_TICKET,
    ALL_TICKETS_GLOBAL,
    ALL_TICKETS_CUSTOMER
}
