package de.tum.cit.aet.artemis.versioning.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
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
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisCategory;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPolicy;
import de.tum.cit.aet.artemis.versioning.service.event.ExerciseChangedEvent;

@Profile(PROFILE_CORE)
@Configurable
public class ExerciseVersionEntityListener implements ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(ExerciseVersionEntityListener.class);

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostPersist
    public void handlePostPersist(Object entity) {
        handleEntityChange(entity);
    }

    @PostUpdate
    public void handlePostUpdate(Object entity) {
        handleEntityChange(entity);
    }

    @PostRemove
    public void handlePostRemove(Object entity) {
        if (entity instanceof Exercise) {
            return;
        }
        handleEntityChange(entity);
    }

    private void handleEntityChange(Object entity) {
        if (entity instanceof Exercise exercise) {
            publishExerciseChangedEvent(exercise.getId(), exercise.getExerciseType());
        }
        else if (entity instanceof CompetencyExerciseLink link) {
            if (link.getExercise() == null) {
                return;
            }
            publishExerciseChangedEvent(link.getExercise().getId(), link.getExercise().getExerciseType());
        }
        else if (entity instanceof AuxiliaryRepository repository) {
            if (repository.getExercise() == null) {
                return;
            }
            publishExerciseChangedEvent(repository.getExercise().getId(), repository.getExercise().getExerciseType());
        }
        else if (entity instanceof StaticCodeAnalysisCategory category) {
            if (category.getExercise() == null) {
                return;
            }
            publishExerciseChangedEvent(category.getExercise().getId(), category.getExercise().getExerciseType());
        }
        else if (entity instanceof SubmissionPolicy submissionPolicy) {
            if (submissionPolicy.getProgrammingExercise() == null) {
                return;
            }
            publishExerciseChangedEvent(submissionPolicy.getProgrammingExercise().getId(), submissionPolicy.getProgrammingExercise().getExerciseType());
        }
        else if (entity instanceof ProgrammingExerciseBuildConfig config) {
            if (config.getProgrammingExercise() == null) {
                return;
            }
            publishExerciseChangedEvent(config.getProgrammingExercise().getId(), config.getProgrammingExercise().getExerciseType());
        }
    }

    /**
     * Publishes an ExerciseChangedEvent for the given exerciseId.
     * exerciseId is used instead of Exercise object, due to Exercise object potentially being uninitialized
     *
     * @param exerciseId the id of the exercise to publish the event for
     */
    private void publishExerciseChangedEvent(Long exerciseId, ExerciseType exerciseType) {
        Optional<String> currentUserLogin = SecurityUtils.getCurrentUserLogin();
        if (currentUserLogin.isEmpty()) {
            log.warn("No user logged in user found");
            return;
        }
        if (applicationContext == null) {
            log.warn("No application context found");
            return;
        }
        applicationContext.publishEvent(new ExerciseChangedEvent(exerciseId, currentUserLogin.get(), exerciseType));
    }
}
