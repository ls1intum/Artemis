package de.tum.cit.aet.artemis.shared;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import de.tum.cit.aet.artemis.atlas.test_repository.OnlineCourseConfigurationRepository;
import de.tum.cit.aet.artemis.core.repository.base.RepositoryImpl;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;

/**
 * Test configuration to enable JPA repositories for the respective test-only repositories.
 * This configuration is used to not rely on the scanned package paths in the main application
 * {@link de.tum.cit.aet.artemis.core.config.DatabaseConfiguration}.
 */
@TestConfiguration
@EnableJpaRepositories(basePackageClasses = { OnlineCourseConfigurationRepository.class, ProgrammingExerciseStudentParticipationTestRepository.class,
        ProgrammingExerciseTestRepository.class, ProgrammingSubmissionTestRepository.class, UserTestRepository.class, }, repositoryBaseClass = RepositoryImpl.class)
@EnableJpaAuditing(auditorAwareRef = "springSecurityAuditorAware")
public class TestRepositoryConfiguration {
}
