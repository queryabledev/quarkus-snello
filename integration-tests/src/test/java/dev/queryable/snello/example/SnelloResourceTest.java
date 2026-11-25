package dev.queryable.snello.example;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class SnelloResourceTest {

    @Test
    void listsAllWithTotalCountHeader() {
        given().get("/api/articles").then().statusCode(200)
                .body("$", hasSize(5))
                .header("X-Total-Count", "5");
    }

    @Test
    void exposesEntitiesWithoutTableAnnotationAtLowercaseName() {
        given().auth().preemptive().basic("alice", "secret")
                .get("/api/team").then().statusCode(200)
                .body("$", hasSize(2));
    }

    @Test
    void protectsGeneratedRoutesWithQuarkusHttpPolicies() {
        given().get("/api/team").then().statusCode(401);
        given().get("/api/team/1").then().statusCode(401);
        given().contentType(ContentType.JSON).body("{\"name\": \"qa\"}")
                .post("/api/team").then().statusCode(401);

        given().auth().preemptive().basic("alice", "secret")
                .queryParam("name_contains", "back")
                .get("/api/team").then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].name", equalTo("backend"));
        given().auth().preemptive().basic("alice", "secret")
                .get("/api/team/1").then().statusCode(200)
                .body("name", equalTo("backend"));
    }

    @Test
    void documentsTypeAwareParametersInOpenApi() {
        String openApi = given().get("/q/openapi").then().statusCode(200)
                .extract().asString();
        org.junit.jupiter.api.Assertions.assertTrue(openApi.contains("title_contains"));
        org.junit.jupiter.api.Assertions.assertTrue(openApi.contains("price_lte"));
        org.junit.jupiter.api.Assertions.assertTrue(openApi.contains("published_at_gte"));
        org.junit.jupiter.api.Assertions.assertTrue(openApi.contains("avatar_inn"));
        org.junit.jupiter.api.Assertions.assertTrue(openApi.contains("select_fields"));
        org.junit.jupiter.api.Assertions.assertTrue(openApi.contains("Case-insensitive contains filter on 'title'"));
        org.junit.jupiter.api.Assertions.assertTrue(openApi.contains("Zero-based offset of the first record"));
        // No range operators on strings, no like operators on numbers.
        org.junit.jupiter.api.Assertions.assertFalse(openApi.contains("title_lte"));
        org.junit.jupiter.api.Assertions.assertFalse(openApi.contains("price_contains"));
    }

    @Test
    void doesNotExposeExcludedEntities() {
        given().get("/api/secret").then().statusCode(404);
    }

    @Test
    void filtersByEquality() {
        given().queryParam("status", "published")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(2))
                .body("id", hasItems(1, 4));
    }

    @Test
    void filtersByEqualityOnSnakeCaseForeignKey() {
        given().queryParam("team_id", "1")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(2))
                .body("id", hasItems(1, 2));
    }

    @Test
    void filtersByNotEqual() {
        given().queryParam("status_ne", "published")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(3));
    }

    @Test
    void filtersByComparisons() {
        given().queryParam("price_lt", "100")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(2))
                .body("id", hasItems(2, 5));

        given().queryParam("price_lte", "100")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(3));

        given().queryParam("price_gt", "100")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(2));

        given().queryParam("price_gte", "150").queryParam("price_lte", "200")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(2))
                .body("id", hasItems(3, 4));
    }

    @Test
    void castsNumericValuesAutomatically() {
        given().queryParam("views", "150")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(1));
    }

    @Test
    void splitsFieldAndOperatorOnLastUnderscore() {
        given().queryParam("published_at_gte", "2024-01-01")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(3))
                .body("id", hasItems(1, 2, 3));
    }

    @Test
    void filtersByLikeVariants() {
        given().queryParam("title_contains", "Spring")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(1));

        given().queryParam("title_like", "Spring")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(1));

        given().queryParam("title_ilike", "spring")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(2))
                .body("id", hasItems(1, 4));

        given().queryParam("title_containss", "spring")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(2));

        given().queryParam("title_rlike", "sku")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(3));

        given().queryParam("title_llike", "PRO")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(5));

        given().queryParam("title_ncontains", "Spring")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(4));
    }

    @Test
    void filtersByInList() {
        given().queryParam("status_in", "draft,review")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(2))
                .body("id", hasItems(2, 3));
    }

    @Test
    void filtersByNullChecksWithoutValue() {
        given().queryParam("avatar_nn", "")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(3))
                .body("id", hasItems(1, 3, 5));

        given().queryParam("avatar_inn", "")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(2))
                .body("id", hasItems(2, 4));
    }

    @Test
    void filtersByEmptyStringChecks() {
        given().queryParam("description_nie", "")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(3))
                .body("id", hasItems(1, 3, 5));

        given().queryParam("description_ie", "")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(2));
    }

    @Test
    void combinesFiltersWithAnd() {
        given().queryParam("status", "published")
                .queryParam("price_gte", "120")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(4));
    }

    @Test
    void combinesOrGroups() {
        given().queryParam("or1.status", "published")
                .queryParam("or1.status", "draft")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(3))
                .body("id", hasItems(1, 2, 4))
                .header("X-Total-Count", "3");

        given().queryParam("or1.status", "published")
                .queryParam("or1.status", "draft")
                .queryParam("or2.price_lte", "60")
                .queryParam("or2.price_gte", "140")
                .queryParam("views_gte", "10")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(2))
                .body("id", hasItems(2, 4));

        given().queryParam("or1.avatar_inn", "")
                .queryParam("or1.price_gte", "150")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(3))
                .body("id", hasItems(2, 3, 4));

        given().queryParam("or1.nonexistent", "5")
                .get("/api/articles").then().statusCode(400);
    }

    @Test
    void paginatesAndSorts() {
        given().queryParam("_limit", "2")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(2))
                .header("X-Total-Count", "5");

        given().queryParam("_sort", "price:asc")
                .queryParam("_start", "1")
                .queryParam("_limit", "2")
                .get("/api/articles").then().statusCode(200)
                .body("id", contains(5, 1))
                .header("X-Total-Count", "5");

        given().queryParam("_sort", "views:desc")
                .queryParam("_limit", "1")
                .get("/api/articles").then().statusCode(200)
                .body("[0].id", equalTo(4));
    }

    @Test
    void ignoresUnknownParametersAndRejectsInvalidValues() {
        given().queryParam("nonexistent", "x")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(5));

        given().queryParam("price_lt", "abc")
                .get("/api/articles").then().statusCode(400);

        given().queryParam("_sort", "nonexistent:asc")
                .get("/api/articles").then().statusCode(400);
    }

    @Test
    void projectsSelectedFields() {
        given().queryParam("select_fields", "id,title")
                .queryParam("_sort", "id:asc")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(5))
                .body("[0].id", equalTo(1))
                .body("[0].title", equalTo("Spring Boot in Action"))
                .body("[0]", not(hasKey("status")))
                .body("[0]", not(hasKey("price")))
                .header("X-Total-Count", "5");

        given().queryParam("select_fields", "id,status")
                .queryParam("price_gte", "150")
                .queryParam("_sort", "price:desc")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(2))
                .body("id", contains(3, 4))
                .body("[0]", not(hasKey("title")))
                .header("X-Total-Count", "2");

        given().queryParam("select_fields", "id,nonexistent")
                .get("/api/articles").then().statusCode(400);
    }

    @Test
    void appliesCustomCriteriaFilters() {
        given().queryParam("search", "spring")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(2))
                .body("id", hasItems(1, 4))
                .header("X-Total-Count", "2");

        given().queryParam("search", "spring")
                .queryParam("status", "published")
                .queryParam("price_gte", "120")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(4));
    }

    @Test
    void resolvesRelationsThroughCustomFilterSubqueries() {
        given().queryParam("team_name", "backend")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(2))
                .body("id", hasItems(1, 2))
                .header("X-Total-Count", "2");

        given().queryParam("team_name", "frontend")
                .queryParam("price_gte", "160")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(3));
    }

    @Test
    void rejectsInvalidCreatesAndUpdatesFromListeners() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"status": "draft", "price": 10.00}
                        """)
                .post("/api/articles").then().statusCode(400);

        given().contentType(ContentType.JSON)
                .body("""
                        {"title": "   ", "status": "draft", "price": 10.00}
                        """)
                .put("/api/articles/1").then().statusCode(400);

        given().get("/api/articles/1").then().statusCode(200)
                .body("title", equalTo("Spring Boot in Action"));
    }

    @Test
    void notifiesListenersAfterCommit() {
        int id = given().contentType(ContentType.JSON)
                .body("""
                        {"title": "Audited article", "status": "draft", "price": 5.00}
                        """)
                .post("/api/articles").then().statusCode(201)
                .extract().path("id");

        given().get("/test/audit").then().statusCode(200)
                .body("$", hasItems("created:" + id));

        given().delete("/api/articles/" + id).then().statusCode(204);
        given().get("/test/audit").then().statusCode(200)
                .body("$", hasItems("deleted:" + id));
    }

    @Test
    void generatesCrudEndpoints() {
        int id = given().contentType(ContentType.JSON)
                .body("""
                        {"title": "New article", "status": "draft", "price": 10.00, "views": 0}
                        """)
                .post("/api/articles").then().statusCode(201)
                .extract().path("id");

        given().get("/api/articles/" + id).then().statusCode(200)
                .body("title", equalTo("New article"));

        given().contentType(ContentType.JSON)
                .body("""
                        {"title": "Updated article", "status": "published", "price": 20.00, "views": 1}
                        """)
                .put("/api/articles/" + id).then().statusCode(204);

        given().get("/api/articles/" + id).then().statusCode(200)
                .body("title", equalTo("Updated article"));

        given().queryParam("title", "Updated article")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(1));

        given().delete("/api/articles/" + id).then().statusCode(204);
        given().get("/api/articles/" + id).then().statusCode(404);
    }
}
