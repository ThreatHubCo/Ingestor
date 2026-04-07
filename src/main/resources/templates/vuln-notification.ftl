<!DOCTYPE html>
<html>
<head>
  <meta charset='UTF-8'>

  <style>
    body {
      font-family: Arial, sans-serif;
    }

    .container {
      max-width: 700px;
      margin: 30px auto;
      background-color: #ffffff;
      padding: 30px;
      border-radius: 8px;
      border: 1px solid #ececec;
    }

    h2 {
      margin-top: 0;
    }

    .summary {
      font-size: 15px;
      margin-bottom: 20px;
    }

    .badge {
      display: inline-block;
      padding: 4px 8px;
      font-size: 12px;
      font-weight: bold;
      border-radius: 12px;
      color: #ffffff;
    }

    .high { background-color: #dc2626; }
    .medium { background-color: #f59e0b; }
    .low { background-color: #10b981; }
    .critical { background-color: #7f1d1d; }
    .none { color: black }

    .section {
      margin-top: 25px;
    }

    .stats-box {
      display: flex;
      gap: 20px;
      margin-top: 15px;
    }

    .stat-card {
      flex: 1;
      background-color: #faf8f8;
      padding: 20px;
      border-radius: 8px;
      text-align: center;
      border: 1px solid #e5e7eb;
    }

    .stat-number {
      font-size: 28px;
      font-weight: bold;
      color: #1f2937;
    }

    .stat-label {
      font-size: 13px;
      color: #6b7280;
      margin-top: 5px;
    }

    .stats-table {
      border-collapse: collapse;
    }

    .stats-table tr,
    .stats-table td {
      border: 1px solid #ececec;
      padding: 6px 20px;
    }

    .stats-table-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 20px;
    }

    .customer-box {
      background-color: #faf8f8;
      padding: 15px;
      border-radius: 6px;
      font-size: 14px;
      border: 1px solid #e5e7eb;
    }

    .button {
      display: inline-block;
      padding: 10px 18px;
      background-color: black;
      color: #ffffff;
      text-decoration: none;
      border-radius: 6px;
      font-weight: bold;
      font-size: 14px;
    }

    .button:hover {
      background-color: rgb(36, 36, 36);
      transform: scale(1.05);
      transition: transform 100ms ease-in-out;
    }

    .footer {
      margin-top: 30px;
      font-size: 12px;
      color: #6b7280;
      text-align: center;
    }
  </style>
</head>
<body>
  <div class='container'>
    <h2>Vulnerability Detected</h2>

    <p class='summary'>
      A vulnerability has been detected in <strong>${software.name}</strong>.
    </p>

    <div class='section'>
      <h3>Impact Summary</h3>

      <div class='stats-table-grid'>
        <table class='stats-table'>
          <tbody>
            <tr>
              <td>
                <strong>Public Exploit Available</strong>
              </td>
              <td>
                ${publicExploit?string("<span class='badge high'>Yes</span>", "<span class='badge low'>No</span>")}
              </td>
            </tr>
          </tbody>
        </table>

        <table class='stats-table'>
          <tbody>
            <tr>
              <td>
                <strong>Highest CVE Severity</strong>
              </td>
              <td>
                <span class="badge ${(highestCveSeverity!'')?lower_case}">
                  ${highestCveSeverity!'N/A'}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class='stats-box'>
        <div class='stat-card'>
          <div class='stat-number'>${cveCount}</div>
          <div class='stat-label'>Total CVEs</div>
        </div>

        <div class='stat-card'>
          <div class='stat-number'>${deviceCount}</div>
          <div class='stat-label'>Vulnerable Devices</div>
        </div>
      </div>
    </div>

    <div class='section'>
      <h3>Customer Information</h3>
      <div class='customer-box'>
        <strong>Name:</strong> ${customer.name}<br>
        <strong>Tenant ID:</strong> ${customer.tenantId}
      </div>
    </div>

    <#if softwareLink?? && softwareLink?has_content>
      <div class='section'>
        <a href='${softwareLink}' target='_blank' class='button'>
          View Full Details
        </a>
      </div>
    </#if>

    <div class='footer'>
      This is an automated security notification.
    </div>
  </div>
</body>
</html>