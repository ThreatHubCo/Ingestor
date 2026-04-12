function isConfigured() {
    const config = th.config.getAll();

    return (
        config &&
        config.TICKET_SYSTEM_CLIENT_ID &&
        config.TICKET_SYSTEM_CLIENT_SECRET &&
        config.TICKET_SYSTEM_URL
    );
}

function daysBetween(date) {
    if (!date) return null;
    return (Date.now() - new Date(date).getTime()) / (1000 * 60 * 60 * 24);
}

async function findCandidates() {
    const sql = `
        SELECT DISTINCT
            s.id AS software_id,
            s.name AS software_name,
            s.auto_ticket_escalation_enabled,
            c.id AS customer_id,
            c.name AS customer_name
        FROM software s
        INNER JOIN vulnerability_affected_software vas ON vas.software_id = s.id
        INNER JOIN vulnerabilities v ON v.id = vas.vulnerability_id
        INNER JOIN device_vulnerabilities dv ON dv.vulnerability_id = v.id
        INNER JOIN devices d ON d.id = dv.device_id
        INNER JOIN customers c ON c.id = d.customer_id
        WHERE d.last_seen_at >= NOW() - INTERVAL 30 DAY
        AND dv.status IN ('OPEN', 'RE_OPENED')
        AND s.auto_ticket_escalation_enabled = TRUE
  `;

    const rows = await th.sql.query(sql);
    const map = new Map();

    for (const r of rows) {
        const key = `${r.software_id}:${r.customer_id}`;
        if (!map.has(key)) {
            map.set(key, {
                software: { id: r.software_id, name: r.software_name },
                customer: { id: r.customer_id, name: r.customer_name }
            });
        }
    }
    return [...map.values()];
}

async function getActiveTicket(customerId, softwareId) {
    const sql = `
        SELECT id, last_ticket_update_at FROM remediation_tickets
        WHERE customer_id = ? AND software_id = ? AND status = 'OPEN'
        LIMIT 1
    `;

    const rows = await th.sql.query(sql, [customerId, softwareId]);
    return rows[0] || null;
}

async function hasPublicExploit(customerId, softwareId) {
    const sql = `
        SELECT 1
        FROM vulnerabilities v
        INNER JOIN vulnerability_affected_software vas ON vas.vulnerability_id = v.id
        INNER JOIN device_vulnerabilities dv ON dv.vulnerability_id = v.id
        INNER JOIN devices d ON d.id = dv.device_id
        WHERE vas.software_id = ?
        AND d.customer_id = ?
        AND v.public_exploit = TRUE
        AND dv.status IN ('OPEN', 'RE_OPENED')
        AND d.last_seen_at >= NOW() - INTERVAL 30 DAY
        LIMIT 1
    `;

    const rows = await th.sql.query(sql, [softwareId, customerId]);
    return rows.length > 0;
}

async function getHighCriticalAge(customerId, softwareId) {
    const sql = `
        SELECT 
            MIN(v.first_detected_at) AS first_seen
        FROM vulnerabilities v
        INNER JOIN vulnerability_affected_software vas ON vas.vulnerability_id = v.id
        INNER JOIN device_vulnerabilities dv ON dv.vulnerability_id = v.id
        INNER JOIN devices d ON d.id = dv.device_id
        WHERE vas.software_id = ?
        AND d.customer_id = ?
        AND v.severity IN ('High', 'Critical')
        AND dv.status IN ('OPEN', 'RE_OPENED')
        AND d.last_seen_at >= NOW() - INTERVAL 30 DAY
    `;

    const rows = await th.sql.query(sql, [softwareId, customerId]);
    return daysBetween(rows?.[0]?.first_seen);
}

async function getEscalatableCveCount(customerId, softwareId) {
    const sql = `
        SELECT COUNT(DISTINCT v.id) AS vuln_count
        FROM vulnerabilities v
        INNER JOIN vulnerability_affected_software vas ON vas.vulnerability_id = v.id
        INNER JOIN device_vulnerabilities dv ON dv.vulnerability_id = v.id
        INNER JOIN devices d ON d.id = dv.device_id
        WHERE vas.software_id = ?
          AND d.customer_id = ?
          AND d.last_seen_at >= NOW() - INTERVAL 30 DAY
          AND dv.status NOT IN ('RESOLVED', 'AUTO_RESOLVED')
          AND (
              v.public_exploit = TRUE
              OR (v.public_exploit = FALSE AND v.first_detected_at <= NOW() - INTERVAL 7 DAY)
          )
    `;

    const rows = await th.sql.query(sql, [softwareId, customerId]);
    return rows?.[0]?.vuln_count || 0;
}

async function getAffectedDeviceCount(customerId, softwareId) {
    const sql = `
        SELECT COUNT(DISTINCT d.id) AS device_count
        FROM devices d
        INNER JOIN device_vulnerabilities dv ON dv.device_id = d.id
        INNER JOIN vulnerability_affected_software vas ON vas.vulnerability_id = dv.vulnerability_id
        INNER JOIN vulnerabilities v ON v.id = dv.vulnerability_id
        WHERE d.customer_id = ?
          AND vas.software_id = ?
          AND dv.status IN ('OPEN', 'RE_OPENED')
          AND d.last_seen_at >= NOW() - INTERVAL 30 DAY
    `;

    const rows = await th.sql.query(sql, [customerId, softwareId]);
    return rows?.[0]?.device_count || 0;
}

async function getHighestCveSeverity(customerId, softwareId) {
    const sql = `
        SELECT v.severity
        FROM vulnerabilities v
        INNER JOIN vulnerability_affected_software vas ON vas.vulnerability_id = v.id
        INNER JOIN device_vulnerabilities dv ON dv.vulnerability_id = v.id
        INNER JOIN devices d ON d.id = dv.device_id
        WHERE vas.software_id = ?
          AND d.customer_id = ?
          AND dv.status IN ('OPEN', 'RE_OPENED')
          AND d.last_seen_at >= NOW() - INTERVAL 30 DAY
        ORDER BY
            CASE v.severity
                WHEN 'Critical' THEN 4
                WHEN 'High' THEN 3
                WHEN 'Medium' THEN 2
                WHEN 'Low' THEN 1
                ELSE 0
            END DESC
        LIMIT 1
    `;

    const rows = await th.sql.query(sql, [softwareId, customerId]);
    return rows?.[0]?.severity || null;
}

async function runHaloEscalation() {
    if (!isConfigured()) {
        th.log.warn("Ticket system not configured");
        return;
    }

    const candidates = await findCandidates();

    for (const { software, customer } of candidates) {
        const name = (software.name || "").toLowerCase();
        const ticket = await getActiveTicket(customer.id, software.id);

        if (ticket) {
            const age = daysBetween(ticket.last_ticket_update_at);

            if (age > 14) {
                await th.email.send({
                    to: "support@example.com",
                    subject: "Stale vulnerability ticket",
                    body: `Ticket ${ticket.id} for ${software.name} (${customer.name}) has not been updated in over 14 days.`
                });
            }

            continue;
        }

        const publicExploit = await hasPublicExploit(customer.id, software.id);
        const isChromeOrEdge = name.includes("chrome") || name.includes("edge");
        const hcAge = await getHighCriticalAge(customer.id, software.id);

        const siteUrl = await th.config.get("SITE_URL");

        const cveCount = await getEscalatableCveCount(customer.id, software.id);
        const deviceCount = await getAffectedDeviceCount(customer.id, software.id);
        const highestCveSeverity = await getHighestCveSeverity(customer.id, software.id);

        const model = {
            publicExploit,
            cveCount,
            deviceCount,
            highestCveSeverity,
            softwareLink: siteUrl ? `${siteUrl}/software/${software.id}?customer=${customer.id}` : null
        }

        console.log(JSON.stringify(model,null,2))
       
        if (isChromeOrEdge) {
            if (hcAge !== null && hcAge >= 4) {
                await th.tickets.create(customer.id, software.id, model);
            }
            continue;
        }

        if (publicExploit) {
            await th.tickets.create(customer.id, software.id, model);
            continue;
        }

        if (hcAge !== null && hcAge < 7) {
            continue;
        }

        if (hcAge !== null && hcAge >= 7) {
            await th.tickets.create(customer.id, software.id, model);
            continue;
        }
    }
}


async function test() {

}

test();
//console.log("Running...");
//runHaloEscalation();