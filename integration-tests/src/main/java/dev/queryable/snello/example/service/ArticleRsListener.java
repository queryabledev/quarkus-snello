package dev.queryable.snello.example.service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;

import dev.queryable.snello.example.model.Article;
import dev.queryable.snello.runtime.PostDelete;
import dev.queryable.snello.runtime.PostPersist;
import dev.queryable.snello.runtime.SnelloEvent;
import dev.queryable.snello.runtime.SnelloOperation;
import dev.queryable.snello.runtime.SnelloRsListener;

/**
 * Validates creates and updates and records audit entries after commit.
 */
@ApplicationScoped
public class ArticleRsListener implements SnelloRsListener<Article> {

    public static final List<String> AUDIT = new CopyOnWriteArrayList<>();

    @Override
    public void onBefore(SnelloEvent<Article> event) {
        if (event.operation() != SnelloOperation.CREATE && event.operation() != SnelloOperation.UPDATE) {
            return;
        }
        Article article = event.entity().orElseThrow();
        if (article.title == null || article.title.isBlank()) {
            throw new BadRequestException("Title is required");
        }
    }

    @PostPersist
    void created(Article article) {
        AUDIT.add("created:" + article.id);
    }

    @PostDelete
    void deleted(SnelloEvent<Article> event) {
        AUDIT.add("deleted:" + event.id().orElseThrow());
    }
}
