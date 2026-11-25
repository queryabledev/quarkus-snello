package dev.queryable.snello.example.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import dev.queryable.snello.runtime.SnelloExclude;

/**
 * Entity excluded from the snello REST exposure.
 */
@Entity
@SnelloExclude
public class Secret {

    @Id
    @GeneratedValue
    public Long id;

    public String secret_value;
}
