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
@EnableJpaRepositories(basePackages = { "de.tum.cit.aet.artemis.assessment.repository", "de.tum.cit.aet.artemis.atlas.repository",
        "de.tum.cit.aet.artemis.communication.repository", "de.tum.cit.aet.artemis.core.repository", "de.tum.cit.aet.artemis.exam.repository",
        "de.tum.cit.aet.artemis.exercise.repository", "de.tum.cit.aet.artemis.fileupload.repository", "de.tum.cit.aet.artemis.iris.repository",
        "de.tum.cit.aet.artemis.lecture.repository", "de.tum.cit.aet.artemis.lti.repository", "de.tum.cit.aet.artemis.modeling.repository",
        "de.tum.cit.aet.artemis.plagiarism.repository", "de.tum.cit.aet.artemis.programming.repository", "de.tum.cit.aet.artemis.quiz.repository",
        "de.tum.cit.aet.artemis.text.repository", "de.tum.cit.aet.artemis.tutorialgroup.repository" }, repositoryBaseClass = RepositoryImpl.class)
@EnableJpaAuditing(auditorAwareRef = "springSecurityAuditorAware")
@EnableTransactionManagement
public class DatabaseConfiguration {
}
