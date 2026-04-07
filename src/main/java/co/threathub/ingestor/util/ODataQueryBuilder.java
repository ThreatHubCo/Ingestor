package co.threathub.ingestor.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class ODataQueryBuilder {
    private final List<String> filters = new ArrayList<>();
    private String orderBy;
    private Integer top;
    private Integer skip;

    public ODataQueryBuilder filter(String condition) {
        if (condition != null && !condition.isEmpty()) {
            filters.add("(" + condition + ")");
        }
        return this;
    }

    public ODataQueryBuilder or(String... conditions) {
        StringJoiner joiner = new StringJoiner(" or ");
        for (String cond : conditions) {
            joiner.add(cond);
        }
        filters.add("(" + joiner + ")");
        return this;
    }

    public ODataQueryBuilder and(String... conditions) {
        StringJoiner joiner = new StringJoiner(" and ");
        for (String cond : conditions) {
            joiner.add(cond);
        }
        filters.add("(" + joiner + ")");
        return this;
    }

    public ODataQueryBuilder orderBy(String orderBy) {
        this.orderBy = orderBy;
        return this;
    }

    public ODataQueryBuilder top(int top) {
        this.top = top;
        return this;
    }

    public ODataQueryBuilder skip(int skip) {
        this.skip = skip;
        return this;
    }

    public String build() {
        StringBuilder sb = new StringBuilder();
        if (!filters.isEmpty()) {
            sb.append("$filter=");
            sb.append(String.join(" and ", filters));
        }
        if (orderBy != null) {
            if (!sb.isEmpty()) {
                sb.append("&");
            }
            sb.append("$orderby=").append(orderBy);
        }
        if (top != null) {
            if (!sb.isEmpty()) {
                sb.append("&");
            }
            sb.append("$top=").append(top);
        }
        if (skip != null) {
            if (!sb.isEmpty()) {
                sb.append("&");
            }
            sb.append("$skip=").append(skip);
        }
        return URLEncoder.encode(sb.toString(), StandardCharsets.UTF_8);
    }
}
