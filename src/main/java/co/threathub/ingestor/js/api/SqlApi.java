package co.threathub.ingestor.js.api;

import co.threathub.ingestor.Ingestor;
import co.threathub.ingestor.js.JSValueConverter;
import co.threathub.ingestor.js.exception.ScriptException;
import co.threathub.ingestor.util.Utils;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.sql.*;
import java.util.*;

@RequiredArgsConstructor
public class SqlApi {
    private final Ingestor ingestor;

    @HostAccess.Export
    public Value query(String sql) {
        return query(sql, null);
    }

    @HostAccess.Export
    public Value query(String sql, List<Object> params) {
        boolean unrestrictedSql = ingestor.getConfigFile().isAllowUnrestrictedSqlInJs();
        String normalized = sql.trim().toLowerCase();
        HikariDataSource dataSource = unrestrictedSql ? ingestor.getDataSource() : ingestor.getReportDataSource();

        if (!unrestrictedSql && !normalized.startsWith("select")) {
            throw new ScriptException("Only SELECT queries are allowed");
        }

        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            if (params != null) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
            }

            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                List<Map<String, Object>> results = new ArrayList<>();

                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();

                    for (int i = 1; i <= columnCount; i++) {
                        String column = meta.getColumnLabel(i);
                        Object value = JSValueConverter.convert(rs.getObject(i));
                        row.put(column, value);
                    }
                    results.add(row);
                }

                String json = Utils.GSON.toJson(results);
                return Context.getCurrent().eval("js", "(" + json + ")");
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new ScriptException("Failed to execute query: " + sql + " | params=" + params, ex);
        }
    }
}