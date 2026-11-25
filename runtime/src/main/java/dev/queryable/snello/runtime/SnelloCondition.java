package dev.queryable.snello.runtime;

import java.util.Set;

/**
 * A parsed query parameter: the entity attribute to filter and the operator to apply.
 *
 * <p>
 * The last {@code _} of the parameter key separates the attribute name from the
 * operator suffix. When the trailing segment is not a known operator, or the
 * leading segment is not a filterable attribute, the whole key is treated as an
 * equality filter on the attribute with that exact name.
 */
public record SnelloCondition(String field, SnelloOperator operator) {

    public static SnelloCondition parse(String key, Set<String> fields) {
        int separator = key.lastIndexOf('_');
        if (separator > 0 && separator < key.length() - 1) {
            SnelloOperator operator = SnelloOperator.fromSuffix(key.substring(separator + 1));
            String field = key.substring(0, separator);
            if (operator != null && fields.contains(field)) {
                return new SnelloCondition(field, operator);
            }
        }
        if (fields.contains(key)) {
            return new SnelloCondition(key, SnelloOperator.EQ);
        }
        return null;
    }
}
