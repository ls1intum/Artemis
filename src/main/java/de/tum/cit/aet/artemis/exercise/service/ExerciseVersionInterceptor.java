package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Iterator;

import org.hibernate.Interceptor;
import org.hibernate.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisCategory;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPolicy;

/**
 * Hibernate Interceptor that automatically creates exercise versions when Exercise entities are saved or updated.
 * This interceptor works at the Hibernate level and catches all Exercise saves regardless of how they're invoked
 * (direct repository calls, method references like repository::save, or bulk operations).
 * <p>
 * Uses ApplicationContextAware to lazily resolve dependencies to avoid circular dependency issues
 * during Hibernate EntityManagerFactory initialization.
 */
@Profile(PROFILE_CORE)
@Component
public class ExerciseVersionInterceptor implements Interceptor, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(ExerciseVersionInterceptor.class);

    private ApplicationContext applicationContext;

    private ExerciseVersionService exerciseVersionService;

    private UserRepository userRepository;

    private enum ActionTrigger {
        CREATE, UPDATE, DELETE
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    // When an entity attached to Exercise is deleted, we need to create a new version for the Exercise
    @Override
    public void onDelete(Object entity, Object id, Object[] state, String[] propertyNames, Type[] types) {
        log.info("ExerciseVersionInterceptor: onDelete: {}, with id {}", entity, id);
        scheduleVersioningForRelevantEntity(entity, ActionTrigger.DELETE);
    }

    // When an Exercise or entity attached to Exercise is updated/created,
    // we need to create a new version for the Exercise.
    // when an entity attached to Exercise is deleted, it does not trigger a postFlush with said entity
    @Override
    public void postFlush(Iterator<Object> entities) {
        while (entities.hasNext()) {
            Object entity = entities.next();
            scheduleVersioningForRelevantEntity(entity, ActionTrigger.UPDATE);
        }
    }

    private void scheduleVersioningForRelevantEntity(Object entity, ActionTrigger trigger) {
        Exercise exerciseToVersion = switch (entity) {
            case Exercise exercise -> trigger == ActionTrigger.DELETE ? null : exercise;
            case CompetencyExerciseLink competencyExerciseLink -> competencyExerciseLink.getExercise();
            case ExampleSubmission exampleSubmission -> exampleSubmission.getExercise();
            case AuxiliaryRepository auxiliaryRepository -> auxiliaryRepository.getExercise();
            case StaticCodeAnalysisCategory staticCodeAnalysisCategory -> staticCodeAnalysisCategory.getExercise();
            case SubmissionPolicy submissionPolicy -> submissionPolicy.getProgrammingExercise();
            case ProgrammingExerciseBuildConfig buildConfig -> buildConfig.getProgrammingExercise();
            default -> null;
        };
        if (exerciseToVersion != null && exerciseToVersion.getId() != null) {
            log.info("ExerciseVersionInterceptor: Exercise {} ({}), triggered by {} action on entity {}", exerciseToVersion.getId(), exerciseToVersion.getTitle(), trigger, entity);
            scheduleVersionCreation(exerciseToVersion);
        }
    }

    /**
     * Lazy-loads the ExerciseVersionService to avoid circular dependencies.
     */
    private ExerciseVersionService getExerciseVersionService() {
        if (exerciseVersionService == null) {
            exerciseVersionService = applicationContext.getBean(ExerciseVersionService.class);
        }
        return exerciseVersionService;
    }

    private User getUser() {
        if (userRepository == null) {
            userRepository = applicationContext.getBean(UserRepository.class);
        }
        return userRepository.getUser();
    }

    /**
     * Schedules version creation to happen after the current transaction commits.
     * This ensures the exercise is fully persisted before we create the version.
     */
    private void scheduleVersionCreation(Exercise exercise) {
        log.info("ExerciseVersionInterceptor: Scheduling version creation for exercise {}", exercise.getId());
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.info("Transaction not active, creating exercise version for exercise {} ({})", exercise.getId(), exercise.getTitle());
            createVersionSafely(exercise);
            return;
        }
        // Transaction exists - use synchronization to run after commit
        log.info("Registering transaction synchronization for exercise {} version creation", exercise.getId());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                createVersionSafely(exercise);
            }
        });
    }

    /**
     * Safely creates an exercise version, handling any exceptions that might occur.
     * This prevents version creation failures from affecting the main exercise save operation.
     */
    private void createVersionSafely(Exercise exercise) {
        log.info("Transaction committed, creating exercise version for exercise {} ({})", exercise.getId(), exercise.getTitle());
        try {
            getExerciseVersionService().createExerciseVersion(exercise, getUser());
        }
        catch (Exception e) {
            log.error("Failed to create exercise version for exercise {} ({}): {}", exercise.getId(), exercise.getTitle(), e.getMessage(), e);
        }
    }
}
