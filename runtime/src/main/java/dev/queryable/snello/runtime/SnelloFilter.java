package dev.queryable.snello.runtime;

import dev.queryable.snello.runtime.spi.SnelloFilterMarker;

/**
 * Contributes custom Criteria predicates to the snello collection query of an
 * entity. Implementations are CDI beans; the entity type is inferred from the
 * generic parameter. Contributed predicates are combined in {@code AND} with the
 * suffix-based filters and apply to both the result and the total count.
 */
public interface SnelloFilter<T> extends SnelloFilterMarker {

    void contribute(SnelloFilterContext<T> context);
}
