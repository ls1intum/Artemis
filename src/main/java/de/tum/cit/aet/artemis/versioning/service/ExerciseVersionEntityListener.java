package de.tum.cit.aet.artemis.versioning.service;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisCategory;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPolicy;
import de.tum.cit.aet.artemis.versioning.event.ExerciseChangedEvent;

@Configurable
public class ExerciseVersionEntityListener implements ApplicationEventPublisherAware {

    private static final Logger log = LoggerFactory.getLogger(ExerciseVersionEntityListener.class);

    private ApplicationEventPublisher eventPublisher;

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
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
     * @param exerciseId   the id of the exercise to publish the event for
     * @param exerciseType the type of the exercise to publish the event for
     */
    private void publishExerciseChangedEvent(Long exerciseId, ExerciseType exerciseType) {
        if (eventPublisher == null) {
            log.error("No application event publisher found, cannot publish ExerciseChangedEvent");
            return;
        }
        var userLogin = SecurityUtils.getCurrentUserLogin();
        if (userLogin.isEmpty()) {
            log.error("No user login found, cannot publish ExerciseChangedEvent");
            return;
        }
        eventPublisher.publishEvent(new ExerciseChangedEvent(exerciseId, exerciseType, userLogin.get()));
    }
}
