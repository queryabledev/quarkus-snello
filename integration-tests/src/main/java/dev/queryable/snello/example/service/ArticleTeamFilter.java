package dev.queryable.snello.example.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import dev.queryable.snello.example.model.Article;
import dev.queryable.snello.example.model.Team;
import dev.queryable.snello.runtime.SnelloFilter;
import dev.queryable.snello.runtime.SnelloFilterContext;

/**
 * Resolves the {@code team_id} foreign key against the {@link Team} entity:
 * {@code ?team_name=backend} matches the articles whose team has that name,
 * without mapping a JPA association on {@link Article}.
 */
@ApplicationScoped
public class ArticleTeamFilter implements SnelloFilter<Article> {

    @Override
    public void contribute(SnelloFilterContext<Article> context) {
        String teamName = context.param("team_name");
        if (teamName == null || teamName.isBlank()) {
            return;
        }
        Subquery<Long> teams = context.query().subquery(Long.class);
        Root<Team> team = teams.from(Team.class);
        teams.select(team.get("id"))
                .where(context.criteriaBuilder().equal(team.get("name"), teamName));
        context.add(context.root().get("team_id").in(teams));
    }
}
