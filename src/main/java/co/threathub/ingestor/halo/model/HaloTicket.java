package co.threathub.ingestor.halo.model;

import lombok.Data;

/**
 * Status IDs:
 * Closed: 9
 * Resolved: 8
 */

@Data
public class HaloTicket {
    private final int id;
    private final String dateoccurred;
    private final String summary;
    private final String details;
    private final String team;
    private final int client_id;
    private final String last_update;
    private final int status_id;

    public boolean isClosed() {
        return status_id == 9 || status_id == 8;
    }
}
