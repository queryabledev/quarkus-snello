package dev.queryable.snello.runtime;

import java.util.List;
import java.util.Optional;

public record SnelloEvent<T>(
        Class<?> entityType,
        SnelloOperation operation,
        boolean after,
        Optional<T> entity,
        Optional<Object> id,
        Optional<List<T>> results) {

    public static <T> SnelloEvent<T> before(Class<?> entityType, SnelloOperation operation,
            T entity, Object id) {
        return new SnelloEvent<>(entityType, operation, false,
                Optional.ofNullable(entity), Optional.ofNullable(id), Optional.empty());
    }

    public static <T> SnelloEvent<T> after(Class<?> entityType, SnelloOperation operation,
            T entity, Object id) {
        return new SnelloEvent<>(entityType, operation, true,
                Optional.ofNullable(entity), Optional.ofNullable(id), Optional.empty());
    }

    public static <T> SnelloEvent<T> after(Class<?> entityType, SnelloOperation operation,
            List<T> results) {
        return new SnelloEvent<>(entityType, operation, true,
                Optional.empty(), Optional.empty(), Optional.of(List.copyOf(results)));
    }
}
