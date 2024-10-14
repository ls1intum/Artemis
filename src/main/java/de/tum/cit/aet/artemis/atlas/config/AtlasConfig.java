package de.tum.cit.aet.artemis.atlas.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import de.tum.cit.aet.artemis.core.config.AbstractModuleConfig;
import de.tum.cit.aet.artemis.core.config.DatabaseConfiguration;
import de.tum.cit.aet.artemis.core.repository.base.RepositoryImpl;

@Profile(PROFILE_CORE) // in the future, we will switch to PROFILE_ATLAS
@Configuration
public class AtlasConfig extends AbstractModuleConfig {

    /**
     * Crucially required profiles for which - if not enabled - renders Atlas useless.
     */
    private static final Set<Profiles> requiredProfiles = Set.of(Profiles.of(PROFILE_CORE));

    public AtlasConfig(Environment environment) {
        super(environment, requiredProfiles);
    }

    @Configuration
    @EnableJpaRepositories(basePackages = { "de.tum.cit.aet.artemis.atlas.repository" }, repositoryBaseClass = RepositoryImpl.class)
    static class AtlasDatabaseConfig extends DatabaseConfiguration {
    }
}
