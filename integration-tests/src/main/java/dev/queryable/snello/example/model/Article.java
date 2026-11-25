package dev.queryable.snello.example.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "articles")
public class Article {

    @Id
    @GeneratedValue
    public Long id;

    public String title;

    public String status;

    public BigDecimal price;

    public Integer views;

    public LocalDate published_at;

    public String description;

    public String avatar;

    public Long team_id;

    @Transient
    public String scratch;
}
