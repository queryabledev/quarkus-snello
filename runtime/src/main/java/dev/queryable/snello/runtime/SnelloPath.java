package dev.queryable.snello.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the entity path appended to {@code quarkus.snello.base-path} when exposing
 * the entity through the Snello REST API.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SnelloPath {

    String value();
}
