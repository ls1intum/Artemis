package de.tum.in.www1.artemis.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import de.tum.in.www1.artemis.repository.RepositoryImpl;

@Configuration
@EnableJpaRepositories(basePackages = "de.tum.in.www1.artemis.repository", repositoryBaseClass = RepositoryImpl.class)
@EnableJpaAuditing(auditorAwareRef = "springSecurityAuditorAware")
@EnableTransactionManagement
public class DatabaseConfiguration {
}
