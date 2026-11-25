package dev.queryable.snello.example;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(SnelloDefaultLimitTest.DefaultLimitProfile.class)
class SnelloDefaultLimitTest {

    public static class DefaultLimitProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.snello.default-limit", "2");
        }
    }

    @Test
    void appliesTheDefaultLimitWhenAbsent() {
        given().get("/api/articles").then().statusCode(200)
                .body("$", hasSize(2))
                .header("X-Total-Count", "5")
                .header("X-Limit", "2");
    }

    @Test
    void explicitLimitOverridesTheDefault() {
        given().queryParam("_limit", "4")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(4))
                .header("X-Total-Count", "5");

        given().queryParam("_limit", "0")
                .get("/api/articles").then().statusCode(200)
                .body("$", hasSize(5));
    }
}
