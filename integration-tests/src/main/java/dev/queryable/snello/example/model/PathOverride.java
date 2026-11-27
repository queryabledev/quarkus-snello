package dev.queryable.snello.example.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import dev.queryable.snello.runtime.SnelloPath;

@Entity
@Table(name = "path_override_table")
@SnelloPath("path-override")
public class PathOverride {

    @Id
    public Long id;
}
