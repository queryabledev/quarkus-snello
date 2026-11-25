package dev.queryable.snello.runtime;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;

/**
 * Backs the REST Data with Panache generated controllers for the CRUD endpoints.
 * The collection listing is replaced by the snello generated resource.
 */
public abstract class SnelloPanacheAdapter<T> {

    @Inject
    SnelloEngine engine;

    private final Class<T> entityType;

    protected SnelloPanacheAdapter(Class<T> entityType) {
        this.entityType = entityType;
    }

    public List<T> list(Page page, Sort sort) {
        throw new UnsupportedOperationException("Snello replaces the REST Data list endpoint");
    }

    public List<T> list(Page page, Sort sort, String query, Map<String, Object> parameters) {
        throw new UnsupportedOperationException("Snello replaces the REST Data list endpoint");
    }

    public int listPageCount(Page page, String query, Map<String, Object> parameters) {
        throw new UnsupportedOperationException("Snello replaces the REST Data list endpoint");
    }

    public long count() {
        return engine.count(entityType);
    }

    public T get(Object id) {
        return engine.getEntity(entityType, id);
    }

    public T add(Object entity) {
        return engine.createEntity(entityType, entityType.cast(entity));
    }

    public T update(Object id, Object entity) {
        return engine.updateEntity(entityType, id, entityType.cast(entity));
    }

    public boolean delete(Object id) {
        return engine.deleteEntity(entityType, id);
    }
}
