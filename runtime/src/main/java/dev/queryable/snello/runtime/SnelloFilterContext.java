package dev.queryable.snello.runtime;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.ws.rs.core.MultivaluedMap;

public final class SnelloFilterContext<T> {

    private final CriteriaBuilder criteriaBuilder;
    private final AbstractQuery<?> query;
    private final Root<T> root;
    private final MultivaluedMap<String, String> queryParameters;
    private final List<Predicate> predicates = new ArrayList<>();

    public SnelloFilterContext(CriteriaBuilder criteriaBuilder, AbstractQuery<?> query, Root<T> root,
            MultivaluedMap<String, String> queryParameters) {
        this.criteriaBuilder = criteriaBuilder;
        this.query = query;
        this.root = root;
        this.queryParameters = queryParameters;
    }

    public CriteriaBuilder criteriaBuilder() {
        return criteriaBuilder;
    }

    /**
     * The criteria query being built, useful to create subqueries, for example to
     * resolve a foreign key column against another entity.
     */
    public AbstractQuery<?> query() {
        return query;
    }

    public Root<T> root() {
        return root;
    }

    public String param(String name) {
        return queryParameters.getFirst(name);
    }

    public List<String> params(String name) {
        return queryParameters.getOrDefault(name, List.of());
    }

    public void add(Predicate predicate) {
        predicates.add(predicate);
    }

    List<Predicate> predicates() {
        return List.copyOf(predicates);
    }
}
