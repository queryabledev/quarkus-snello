package dev.queryable.snello.runtime;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.snello")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface SnelloRuntimeConfig {

    /**
     * Default maximum number of records returned by the collection endpoints when
     * the {@code _limit} query parameter is absent. {@code 0} means unlimited.
     * An explicit {@code _limit} always overrides this value, including
     * {@code _limit=0} to request an unlimited result.
     */
    @WithDefault("0")
    int defaultLimit();
}
