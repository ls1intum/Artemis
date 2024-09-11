package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import de.tum.cit.aet.artemis.core.repository.base.RepositoryImpl;

@Profile(PROFILE_CORE)
@Configuration
@EnableJpaRepositories(basePackages = { "de.tum.cit.aet.artemis.*.repository", "de.tum.cit.aet.artemis.repository.**" // ToDo: Remove once test files have been moved
}, repositoryBaseClass = RepositoryImpl.class)
@EnableJpaAuditing(auditorAwareRef = "springSecurityAuditorAware")
@EnableTransactionManagement
public class DatabaseConfiguration {
}
