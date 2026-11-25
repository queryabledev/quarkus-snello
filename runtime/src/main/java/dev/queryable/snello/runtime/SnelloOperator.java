package dev.queryable.snello.runtime;

import java.util.HashMap;
import java.util.Map;

/**
 * Filter operators selected through field name suffixes, for example {@code price_lte=200}.
 */
public enum SnelloOperator {

    EQ(null, true),
    NE("ne", true),
    LT("lt", true),
    LTE("lte", true),
    GT("gt", true),
    GTE("gte", true),
    CONTAINS("contains", true),
    LIKE("like", true),
    ILIKE("ilike", true),
    CONTAINSS("containss", true),
    RLIKE("rlike", true),
    LLIKE("llike", true),
    NCONTAINS("ncontains", true),
    IN("in", true),
    NOT_NULL("nn", false),
    IS_NULL("inn", false),
    NOT_EMPTY("nie", false),
    EMPTY("ie", false);

    private static final Map<String, SnelloOperator> BY_SUFFIX = new HashMap<>();

    static {
        for (SnelloOperator operator : values()) {
            if (operator.suffix != null) {
                BY_SUFFIX.put(operator.suffix, operator);
            }
        }
    }

    private final String suffix;
    private final boolean requiresValue;

    SnelloOperator(String suffix, boolean requiresValue) {
        this.suffix = suffix;
        this.requiresValue = requiresValue;
    }

    public boolean requiresValue() {
        return requiresValue;
    }

    public static SnelloOperator fromSuffix(String suffix) {
        return BY_SUFFIX.get(suffix);
    }
}
