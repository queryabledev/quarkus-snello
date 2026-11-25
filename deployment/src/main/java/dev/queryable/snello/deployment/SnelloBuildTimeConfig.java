package dev.queryable.snello.deployment;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.snello")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface SnelloBuildTimeConfig {

    /**
     * Base path prepended to every generated resource path. The table name of each
     * entity is appended to it, for example {@code /api/articles}.
     */
    @WithDefault("/api")
    String basePath();
}
