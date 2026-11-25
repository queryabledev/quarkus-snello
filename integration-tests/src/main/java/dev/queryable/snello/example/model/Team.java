package dev.queryable.snello.example.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

/**
 * Entity without an explicit {@code @Table} name: exposed at /api/team.
 */
@Entity
public class Team {

    @Id
    @GeneratedValue
    public Long id;

    public String name;
}
