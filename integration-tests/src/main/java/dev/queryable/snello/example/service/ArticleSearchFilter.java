package dev.queryable.snello.example.service;

import java.util.Locale;

import jakarta.enterprise.context.ApplicationScoped;

import dev.queryable.snello.example.model.Article;
import dev.queryable.snello.runtime.SnelloFilter;
import dev.queryable.snello.runtime.SnelloFilterContext;

/**
 * Custom Criteria filter: {@code ?search=x} matches the title or the description,
 * case-insensitively.
 */
@ApplicationScoped
public class ArticleSearchFilter implements SnelloFilter<Article> {

    @Override
    public void contribute(SnelloFilterContext<Article> context) {
        String search = context.param("search");
        if (search == null || search.isBlank()) {
            return;
        }
        String pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
        var cb = context.criteriaBuilder();
        var root = context.root();
        context.add(cb.or(
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(root.get("description")), pattern)));
    }
}
