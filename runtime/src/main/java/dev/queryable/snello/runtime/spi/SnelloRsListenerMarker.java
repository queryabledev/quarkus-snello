package dev.queryable.snello.runtime.spi;

import dev.queryable.snello.runtime.SnelloRsListener;

public interface SnelloRsListenerMarker {

    default Class<?> entityType() {
        return SnelloEntityType.resolve(this, SnelloRsListener.class);
    }
}
