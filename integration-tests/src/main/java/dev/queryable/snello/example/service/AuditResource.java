package dev.queryable.snello.example.service;

import java.util.List;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Exposes the audit entries recorded by {@link ArticleRsListener} so tests can
 * assert them also when the application runs in a separate process.
 */
@Path("/test/audit")
@Produces(MediaType.APPLICATION_JSON)
public class AuditResource {

    @GET
    public List<String> list() {
        return ArticleRsListener.AUDIT;
    }
}
