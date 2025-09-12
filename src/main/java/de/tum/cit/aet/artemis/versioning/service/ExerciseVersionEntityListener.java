package de.tum.cit.aet.artemis.versioning.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisCategory;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPolicy;

@Profile(PROFILE_CORE)
@Configurable
public class ExerciseVersionEntityListener implements ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(ExerciseVersionEntityListener.class);

    private ApplicationContext applicationContext;

    private ExerciseVersionService exerciseVersionService;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostPersist
    @PostUpdate
    public void handleExerciseChange(Object entity) {
        Exercise exerciseToVersion = getExerciseFromEntity(entity);
        if (exerciseToVersion != null && exerciseToVersion.getId() != null) {
            triggerVersionCreation(exerciseToVersion);
        }
    }

    private Exercise getExerciseFromEntity(Object entity) {
        return switch (entity) {
            case Exercise exercise -> exercise;
            case CompetencyExerciseLink competencyExerciseLink -> competencyExerciseLink.getExercise();
            case AuxiliaryRepository auxiliaryRepository -> auxiliaryRepository.getExercise();
            case StaticCodeAnalysisCategory staticCodeAnalysisCategory -> staticCodeAnalysisCategory.getExercise();
            case SubmissionPolicy submissionPolicy -> submissionPolicy.getProgrammingExercise();
            case ProgrammingExerciseBuildConfig buildConfig -> buildConfig.getProgrammingExercise();
            default -> null;
        };
    }

    private void triggerVersionCreation(Exercise exercise) {
        try {
            Optional<String> currentUserLogin = SecurityUtils.getCurrentUserLogin();
            if (currentUserLogin.isPresent()) {
                getExerciseVersionService().createExerciseVersion(exercise, currentUserLogin.get());
            }
            else {
                log.warn("No user logged in user found with login");
            }
        }
        catch (Exception e) {
            log.error("Failed to create exercise version for exercise {} ({}): {}", exercise.getId(), exercise.getTitle(), e.getMessage(), e);
        }
    }

    private ExerciseVersionService getExerciseVersionService() {
        if (exerciseVersionService == null) {
            exerciseVersionService = applicationContext.getBean(ExerciseVersionService.class);
        }
        return exerciseVersionService;
    }

}
