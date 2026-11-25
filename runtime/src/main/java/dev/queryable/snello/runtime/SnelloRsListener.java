package dev.queryable.snello.runtime;

import dev.queryable.snello.runtime.spi.SnelloRsListenerMarker;

/**
 * REST lifecycle listener for a snello generated resource. Implementations are CDI
 * beans; the entity type is inferred from the generic parameter. Before-callbacks
 * can reject the operation by throwing a REST exception, for example
 * {@link jakarta.ws.rs.BadRequestException}. After-callbacks run once the enclosing
 * transaction has committed.
 */
public interface SnelloRsListener<T> extends SnelloRsListenerMarker {

    default void onBefore(SnelloEvent<T> event) {
    }

    default void onAfter(SnelloEvent<T> event) {
    }
}
