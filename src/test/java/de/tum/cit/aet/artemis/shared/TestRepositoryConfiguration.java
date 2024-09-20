package de.tum.cit.aet.artemis.shared;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import de.tum.cit.aet.artemis.core.repository.base.RepositoryImpl;

/**
 * Test configuration to enable JPA repositories for the respective test-only repositories.
 * This configuration is used to not rely on the scanned package paths in the main application
 * {@link de.tum.cit.aet.artemis.core.config.DatabaseConfiguration}.
 * </br>
 * <strong>Important</strong>* You need to annotate every TestRepository with {@link org.springframework.context.annotation.Primary}
 * to override the production repository beans. // ToDo: Add arch-test
 */
@TestConfiguration
@EnableJpaRepositories(basePackages = { "de.tum.cit.aet.artemis.assessment.test_repository", "de.tum.cit.aet.artemis.atlas.test_repository",
        "de.tum.cit.aet.artemis.communication.test_repository", "de.tum.cit.aet.artemis.core.test_repository", "de.tum.cit.aet.artemis.exam.test_repository",
        "de.tum.cit.aet.artemis.exercise.test_repository", "de.tum.cit.aet.artemis.lecture.test_repository", "de.tum.cit.aet.artemis.lti.test_repository",
        "de.tum.cit.aet.artemis.modeling.test_repository", "de.tum.cit.aet.artemis.programming.test_repository", "de.tum.cit.aet.artemis.quiz.test_repository",
        "de.tum.cit.aet.artemis.text.test_repository" }, repositoryBaseClass = RepositoryImpl.class)
@EnableJpaAuditing(auditorAwareRef = "springSecurityAuditorAware")
@EnableTransactionManagement
public class TestRepositoryConfiguration {
}
