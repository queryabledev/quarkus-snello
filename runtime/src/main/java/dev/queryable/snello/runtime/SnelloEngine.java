package dev.queryable.snello.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import org.jboss.logging.Logger;

import dev.queryable.snello.runtime.spi.SnelloFilterMarker;
import dev.queryable.snello.runtime.spi.SnelloRsListenerMarker;

/**
 * Executes the snello collection query (suffix-based filters, pagination, sorting
 * and field projection) and the CRUD operations delegated by the generated REST
 * Data adapters, dispatching REST lifecycle events along the way.
 */
@ApplicationScoped
public class SnelloEngine {

    private static final Logger LOG = Logger.getLogger(SnelloEngine.class);

    public static final String START = "_start";
    public static final String LIMIT = "_limit";
    public static final String SORT = "_sort";
    public static final String SELECT_FIELDS = "select_fields";

    private static final Set<String> RESERVED = Set.of(START, LIMIT, SORT, SELECT_FIELDS);

    private static final java.util.regex.Pattern OR_GROUP = java.util.regex.Pattern.compile("^(or\\d+)\\.(.+)$");

    @Inject
    EntityManager entityManager;

    @Inject
    TransactionSynchronizationRegistry transactionSynchronizationRegistry;

    @Inject
    Instance<SnelloRsListenerMarker> rsListeners;

    @Inject
    Instance<SnelloFilterMarker> filterBeans;

    private volatile Integer defaultLimit;

    int defaultLimit() {
        Integer limit = defaultLimit;
        if (limit == null) {
            limit = org.eclipse.microprofile.config.ConfigProvider.getConfig()
                    .getOptionalValue("quarkus.snello.default-limit", Integer.class).orElse(0);
            defaultLimit = limit;
        }
        return limit;
    }

    public <T> Response list(Class<T> entityType, UriInfo uriInfo) {
        MultivaluedMap<String, String> parameters = uriInfo.getQueryParameters();
        int start = intParam(parameters, START, 0);
        int limit = intParam(parameters, LIMIT, defaultLimit());
        Map<String, Class<?>> fields = filterableFields(entityType);
        List<String> selectedFields = selectedFields(parameters, fields);
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        dispatchBefore(entityType, SnelloOperation.LIST, null, null);

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<T> countRoot = countQuery.from(entityType);
        countQuery.select(cb.count(countRoot));
        List<Predicate> countPredicates = predicates(entityType, cb, countQuery, countRoot, fields, parameters);
        if (!countPredicates.isEmpty()) {
            countQuery.where(countPredicates.toArray(Predicate[]::new));
        }
        long total = entityManager.createQuery(countQuery).getSingleResult();

        Object payload;
        if (selectedFields == null) {
            CriteriaQuery<T> query = cb.createQuery(entityType);
            Root<T> root = query.from(entityType);
            query.select(root);
            List<Predicate> predicates = predicates(entityType, cb, query, root, fields, parameters);
            if (!predicates.isEmpty()) {
                query.where(predicates.toArray(Predicate[]::new));
            }
            applySort(cb, query, root, fields, parameters.getFirst(SORT));
            List<T> result = paged(entityManager.createQuery(query), start, limit).getResultList();
            dispatchAfter(entityType, SnelloOperation.LIST, result);
            payload = result;
        } else {
            CriteriaQuery<Tuple> query = cb.createTupleQuery();
            Root<T> root = query.from(entityType);
            List<Selection<?>> selections = new ArrayList<>();
            for (String field : selectedFields) {
                selections.add(root.get(field).alias(field));
            }
            query.multiselect(selections);
            List<Predicate> predicates = predicates(entityType, cb, query, root, fields, parameters);
            if (!predicates.isEmpty()) {
                query.where(predicates.toArray(Predicate[]::new));
            }
            applySort(cb, query, root, fields, parameters.getFirst(SORT));
            List<Tuple> tuples = paged(entityManager.createQuery(query), start, limit).getResultList();
            List<Map<String, Object>> rows = new ArrayList<>(tuples.size());
            for (Tuple tuple : tuples) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (String field : selectedFields) {
                    row.put(field, tuple.get(field));
                }
                rows.add(row);
            }
            payload = rows;
        }

        return Response.ok(payload)
                .header("X-Start", start)
                .header("X-Limit", limit)
                .header("X-Total-Count", total)
                .header("Access-Control-Expose-Headers", "X-Start, X-Limit, X-Total-Count")
                .build();
    }

    public long count(Class<?> entityType) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        query.select(cb.count(query.from(entityType)));
        return entityManager.createQuery(query).getSingleResult();
    }

    public <T> T getEntity(Class<T> entityType, Object id) {
        dispatchBefore(entityType, SnelloOperation.FETCH, null, id);
        T entity = entityManager.find(entityType, id);
        if (entity != null) {
            dispatchAfter(entityType, SnelloOperation.FETCH, entity, id);
        }
        return entity;
    }

    public <T> T createEntity(Class<T> entityType, T entity) {
        dispatchBefore(entityType, SnelloOperation.CREATE, entity, null);
        T persisted = persist(entity);
        dispatchAfter(entityType, SnelloOperation.CREATE, persisted, identifier(persisted));
        return persisted;
    }

    public <T> T updateEntity(Class<T> entityType, Object id, T entity) {
        Object suppliedId = identifier(entity);
        if (suppliedId == null) {
            assignIdentifier(entityType, entity, id);
        } else if (!id.equals(suppliedId)) {
            throw new BadRequestException("The entity id does not match the path id");
        }
        dispatchBefore(entityType, SnelloOperation.UPDATE, entity, id);
        T updated = merge(entity);
        dispatchAfter(entityType, SnelloOperation.UPDATE, updated, id);
        return updated;
    }

    public boolean deleteEntity(Class<?> entityType, Object id) {
        dispatchBefore(entityType, SnelloOperation.DELETE, null, id);
        if (!remove(entityType, id)) {
            return false;
        }
        dispatchAfter(entityType, SnelloOperation.DELETE, null, id);
        return true;
    }

    @Transactional
    <T> T persist(T entity) {
        entityManager.persist(entity);
        entityManager.flush();
        return entity;
    }

    @Transactional
    <T> T merge(T entity) {
        return entityManager.merge(entity);
    }

    @Transactional
    boolean remove(Class<?> entityType, Object id) {
        Object entity = entityManager.find(entityType, id);
        if (entity == null) {
            return false;
        }
        entityManager.remove(entity);
        entityManager.flush();
        return true;
    }

    private Object identifier(Object entity) {
        return entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
    }

    private <T> void assignIdentifier(Class<T> entityType, T entity, Object id) {
        SingularAttribute<? super T, ?> idAttribute = entityManager.getMetamodel().entity(entityType)
                .getId(entityManager.getMetamodel().entity(entityType).getIdType().getJavaType());
        Member member = idAttribute.getJavaMember();
        if (member instanceof Field field) {
            try {
                field.setAccessible(true);
                field.set(entity, id);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Cannot assign the entity id", exception);
            }
        }
    }

    private <T> Map<String, Class<?>> filterableFields(Class<T> entityType) {
        EntityType<T> metamodel = entityManager.getMetamodel().entity(entityType);
        Map<String, Class<?>> result = new LinkedHashMap<>();
        for (SingularAttribute<? super T, ?> attribute : metamodel.getSingularAttributes()) {
            if (attribute.isAssociation()) {
                continue;
            }
            Class<?> type = attribute.getJavaType();
            if (SnelloTypes.isScalar(type)) {
                result.put(attribute.getName(), type);
            }
        }
        return result;
    }

    private List<String> selectedFields(MultivaluedMap<String, String> parameters, Map<String, Class<?>> fields) {
        String value = parameters.getFirst(SELECT_FIELDS);
        if (value == null || value.isBlank()) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (String item : value.split(",")) {
            String field = item.trim();
            if (field.isEmpty()) {
                continue;
            }
            if (!fields.containsKey(field)) {
                throw new BadRequestException("Unknown field in " + SELECT_FIELDS + ": " + field);
            }
            if (!result.contains(field)) {
                result.add(field);
            }
        }
        return result.isEmpty() ? null : result;
    }

    private <T> List<Predicate> predicates(Class<T> entityType, CriteriaBuilder cb,
            jakarta.persistence.criteria.AbstractQuery<?> query, Root<T> root,
            Map<String, Class<?>> fields, MultivaluedMap<String, String> parameters) {
        List<Predicate> predicates = new ArrayList<>();
        Map<String, List<Predicate>> orGroups = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            String key = entry.getKey();
            if (RESERVED.contains(key)) {
                continue;
            }
            String plainKey = key;
            String groupKey = null;
            java.util.regex.Matcher matcher = OR_GROUP.matcher(key);
            if (matcher.matches()) {
                groupKey = matcher.group(1);
                plainKey = matcher.group(2);
            }
            SnelloCondition condition = SnelloCondition.parse(plainKey, fields.keySet());
            if (condition == null) {
                if (groupKey != null) {
                    throw new BadRequestException("Unknown field in OR group parameter: " + key);
                }
                LOG.debugf("Ignoring unknown query parameter: %s", key);
                continue;
            }
            List<Predicate> target = groupKey == null ? predicates
                    : orGroups.computeIfAbsent(groupKey, group -> new ArrayList<>());
            Class<?> type = fields.get(condition.field());
            if (!condition.operator().requiresValue()) {
                target.add(predicate(cb, root, condition, type, null));
                continue;
            }
            for (String value : entry.getValue()) {
                if (value == null || value.isEmpty()) {
                    throw new BadRequestException("Missing value for query parameter: " + key);
                }
                target.add(predicate(cb, root, condition, type, value));
            }
        }
        for (List<Predicate> group : orGroups.values()) {
            predicates.add(cb.or(group.toArray(Predicate[]::new)));
        }
        applyFilters(entityType, cb, query, root, parameters, predicates);
        return predicates;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private <T> void applyFilters(Class<T> entityType, CriteriaBuilder cb,
            jakarta.persistence.criteria.AbstractQuery<?> query, Root<T> root,
            MultivaluedMap<String, String> parameters, List<Predicate> predicates) {
        SnelloFilterContext<T> context = new SnelloFilterContext<>(cb, query, root, parameters);
        for (SnelloFilterMarker marker : filterBeans) {
            SnelloFilter filter = (SnelloFilter) marker;
            if (filter.entityType().equals(entityType)) {
                filter.contribute(context);
            }
        }
        predicates.addAll(context.predicates());
    }

    private Predicate predicate(CriteriaBuilder cb, Root<?> root, SnelloCondition condition, Class<?> type,
            String raw) {
        String field = condition.field();
        return switch (condition.operator()) {
            case EQ -> cb.equal(root.get(field), value(type, field, raw));
            case NE -> cb.notEqual(root.get(field), value(type, field, raw));
            case LT, LTE, GT, GTE -> compare(cb, root, condition, type, raw);
            case CONTAINS, LIKE -> cb.like(text(root, field, type), "%" + raw + "%");
            case NCONTAINS -> cb.notLike(text(root, field, type), "%" + raw + "%");
            case ILIKE, CONTAINSS -> cb.like(cb.lower(text(root, field, type)),
                    "%" + raw.toLowerCase(Locale.ROOT) + "%");
            case RLIKE -> cb.like(cb.lower(text(root, field, type)), raw.toLowerCase(Locale.ROOT) + "%");
            case LLIKE -> cb.like(cb.lower(text(root, field, type)), "%" + raw.toLowerCase(Locale.ROOT));
            case IN -> in(root, field, type, raw);
            case NOT_NULL -> cb.isNotNull(root.get(field));
            case IS_NULL -> cb.isNull(root.get(field));
            case NOT_EMPTY -> cb.notEqual(text(root, field, type), "");
            case EMPTY -> cb.equal(text(root, field, type), "");
        };
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Predicate compare(CriteriaBuilder cb, Root<?> root, SnelloCondition condition, Class<?> type,
            String raw) {
        Comparable value = (Comparable) value(type, condition.field(), raw);
        Expression expression = root.get(condition.field());
        return switch (condition.operator()) {
            case LT -> cb.lessThan(expression, value);
            case LTE -> cb.lessThanOrEqualTo(expression, value);
            case GT -> cb.greaterThan(expression, value);
            case GTE -> cb.greaterThanOrEqualTo(expression, value);
            default -> throw new IllegalStateException("Not a comparison operator: " + condition.operator());
        };
    }

    private Predicate in(Root<?> root, String field, Class<?> type, String raw) {
        List<Object> values = new ArrayList<>();
        for (String item : raw.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                values.add(value(type, field, trimmed));
            }
        }
        if (values.isEmpty()) {
            throw new BadRequestException("Expected a non-empty list for field: " + field);
        }
        return root.get(field).in(values);
    }

    @SuppressWarnings("unchecked")
    private Expression<String> text(Root<?> root, String field, Class<?> type) {
        if (type == String.class) {
            return (Expression<String>) (Expression<?>) root.get(field);
        }
        return root.get(field).as(String.class);
    }

    private Object value(Class<?> type, String field, String raw) {
        try {
            return SnelloTypes.convert(type, raw);
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            throw new BadRequestException("Invalid value for field " + field + ": " + raw, exception);
        }
    }

    private void applySort(CriteriaBuilder cb, CriteriaQuery<?> query, Root<?> root,
            Map<String, Class<?>> fields, String sort) {
        if (sort == null || sort.isBlank()) {
            return;
        }
        List<Order> orders = new ArrayList<>();
        for (String part : sort.split(",")) {
            String[] segments = part.trim().split(":");
            String field = segments[0].trim();
            if (!fields.containsKey(field)) {
                throw new BadRequestException("Unknown sort field: " + field);
            }
            boolean descending = segments.length > 1 && segments[1].trim().equalsIgnoreCase("desc");
            orders.add(descending ? cb.desc(root.get(field)) : cb.asc(root.get(field)));
        }
        query.orderBy(orders);
    }

    private static <Q extends TypedQuery<?>> Q paged(Q query, int start, int limit) {
        if (start > 0) {
            query.setFirstResult(start);
        }
        if (limit > 0) {
            query.setMaxResults(limit);
        }
        return query;
    }

    private static int intParam(MultivaluedMap<String, String> parameters, String name, int defaultValue) {
        String value = parameters.getFirst(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw new BadRequestException("Expected a non-negative value for " + name + ": " + value);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new BadRequestException("Invalid value for " + name + ": " + value, exception);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void dispatchBefore(Class<?> entityType, SnelloOperation operation, Object entity, Object id) {
        for (SnelloRsListenerMarker marker : rsListeners) {
            SnelloRsListener listener = (SnelloRsListener) marker;
            if (listener.entityType().equals(entityType)) {
                SnelloEvent<?> event = SnelloEvent.before(entityType, operation, entity, id);
                listener.onBefore(event);
                invokeAnnotatedCallbacks(listener, event);
            }
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void dispatchAfter(Class<?> entityType, SnelloOperation operation, Object entity, Object id) {
        SnelloEvent<?> event = SnelloEvent.after(entityType, operation, entity, id);
        dispatchAfter(entityType, operation, event);
    }

    private <T> void dispatchAfter(Class<T> entityType, SnelloOperation operation, List<T> results) {
        SnelloEvent<T> event = SnelloEvent.after(entityType, operation, results);
        dispatchAfter(entityType, operation, event);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void dispatchAfter(Class<?> entityType, SnelloOperation operation, SnelloEvent<?> event) {
        afterCommit(() -> {
            for (SnelloRsListenerMarker marker : rsListeners) {
                SnelloRsListener listener = (SnelloRsListener) marker;
                if (!listener.entityType().equals(entityType)) {
                    continue;
                }
                try {
                    listener.onAfter(event);
                    invokeAnnotatedCallbacks(listener, event);
                } catch (Exception exception) {
                    LOG.errorf(exception, "Snello %s after-listener failed for %s",
                            operation, entityType.getName());
                }
            }
        });
    }

    private void afterCommit(Runnable callback) {
        int status = transactionSynchronizationRegistry.getTransactionStatus();
        if (status == Status.STATUS_NO_TRANSACTION
                || status == Status.STATUS_COMMITTED
                || status == Status.STATUS_ROLLEDBACK) {
            callback.run();
            return;
        }
        transactionSynchronizationRegistry.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
            }

            @Override
            public void afterCompletion(int completionStatus) {
                if (completionStatus == Status.STATUS_COMMITTED) {
                    callback.run();
                }
            }
        });
    }

    private void invokeAnnotatedCallbacks(Object listener, SnelloEvent<?> event) {
        Class<? extends Annotation> annotationType = callbackAnnotation(event);
        for (Class<?> type = listener.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getAnnotation(annotationType) == null) {
                    continue;
                }
                validateAndInvoke(listener, method, event);
            }
        }
    }

    private Class<? extends Annotation> callbackAnnotation(SnelloEvent<?> event) {
        return switch (event.operation()) {
            case FETCH -> event.after() ? PostFetch.class : PreFetch.class;
            case LIST -> event.after() ? PostList.class : PreList.class;
            case CREATE -> event.after() ? PostPersist.class : PrePersist.class;
            case UPDATE -> event.after() ? PostUpdate.class : PreUpdate.class;
            case DELETE -> event.after() ? PostDelete.class : PreDelete.class;
        };
    }

    private void validateAndInvoke(Object listener, Method method, SnelloEvent<?> event) {
        if (method.getParameterCount() > 1) {
            throw new IllegalArgumentException(
                    method + " must declare no parameters or one supported lifecycle argument");
        }
        try {
            method.setAccessible(true);
            Object[] arguments = method.getParameterCount() == 0
                    ? new Object[0]
                    : new Object[] { callbackArgument(method.getParameterTypes()[0], method, event) };
            method.invoke(listener, arguments);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot invoke snello callback " + method, exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Snello callback failed: " + method, cause);
        }
    }

    private Object callbackArgument(Class<?> parameterType, Method method, SnelloEvent<?> event) {
        if (parameterType.isAssignableFrom(SnelloEvent.class)) {
            return event;
        }
        if (event.entity().isPresent() && parameterType.isInstance(event.entity().orElseThrow())) {
            return event.entity().orElseThrow();
        }
        if (event.results().isPresent() && parameterType.isAssignableFrom(event.results().orElseThrow().getClass())) {
            return event.results().orElseThrow();
        }
        if (event.id().isPresent() && parameterType.isInstance(event.id().orElseThrow())) {
            return event.id().orElseThrow();
        }
        throw new IllegalArgumentException(method
                + " parameter must be SnelloEvent, the entity, the result List, or the entity id");
    }
}
