package dev.queryable.snello.runtime.spi;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.enterprise.inject.spi.Bean;

import io.quarkus.arc.Arc;

public final class SnelloEntityType {

    private SnelloEntityType() {
    }

    public static Class<?> resolve(Object instance, Class<?> contract) {
        Set<Class<?>> entities = new LinkedHashSet<>();
        for (Class<?> type = instance.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Bean<?> bean : Arc.container().beanManager().getBeans(type)) {
                if (!bean.getBeanClass().isAssignableFrom(instance.getClass())) {
                    continue;
                }
                for (Type beanType : bean.getTypes()) {
                    if (beanType instanceof ParameterizedType parameterized
                            && parameterized.getRawType().equals(contract)
                            && parameterized.getActualTypeArguments()[0] instanceof Class<?> entity) {
                        entities.add(entity);
                    }
                }
            }
            if (!entities.isEmpty()) {
                break;
            }
        }
        if (entities.size() != 1) {
            throw new IllegalStateException("Cannot resolve a single entity type for "
                    + instance.getClass().getName() + " implementing " + contract.getName()
                    + "; declare a concrete generic entity type or override entityType()");
        }
        return entities.iterator().next();
    }
}
