package dev.queryable.snello.runtime.spi;

import dev.queryable.snello.runtime.SnelloFilter;

public interface SnelloFilterMarker {

    default Class<?> entityType() {
        return SnelloEntityType.resolve(this, SnelloFilter.class);
    }
}
