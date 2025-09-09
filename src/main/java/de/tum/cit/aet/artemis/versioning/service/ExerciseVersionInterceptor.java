package de.tum.cit.aet.artemis.versioning.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.hibernate.Interceptor;
import org.hibernate.Transaction;
import org.hibernate.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

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
@Lazy
public class ExerciseVersionInterceptor implements Interceptor, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(ExerciseVersionInterceptor.class);

    private ApplicationContext applicationContext;

    private ExerciseVersionService exerciseVersionService;

    private UserRepository userRepository;

    private final Set<Exercise> exercisesToVersion = ConcurrentHashMap.newKeySet();

    private User currentUser;

    @Override
    public void afterTransactionCompletion(Transaction tx) {
        exercisesToVersion.forEach(this::createVersionSafely);
        exercisesToVersion.clear();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    // When an entity attached to Exercise is deleted, we need to create a new version for the Exercise
    @Override
    public void onDelete(Object entity, Object id, Object[] state, String[] propertyNames, Type[] types) {
        findExerciseToVersion(entity, true);
    }

    // When an Exercise or entity attached to Exercise is updated/created,
    // we need to create a new version for the Exercise.
    // when an entity attached to Exercise is deleted, it does not trigger a postFlush with said entity
    @Override
    public void postFlush(Iterator<Object> entities) {
        Set<Object> objects = StreamSupport.stream(Spliterators.spliteratorUnknownSize(entities, Spliterator.ORDERED), false).collect(Collectors.toSet());
        objects.forEach((entity) -> findExerciseToVersion(entity, false));
    }

    private void findExerciseToVersion(Object entity, boolean isDeletingEntity) {
        Exercise exerciseToVersion = switch (entity) {
            case Exercise exercise -> isDeletingEntity ? null : exercise;
            case CompetencyExerciseLink competencyExerciseLink -> competencyExerciseLink.getExercise();
            case AuxiliaryRepository auxiliaryRepository -> auxiliaryRepository.getExercise();
            case StaticCodeAnalysisCategory staticCodeAnalysisCategory -> staticCodeAnalysisCategory.getExercise();
            case SubmissionPolicy submissionPolicy -> submissionPolicy.getProgrammingExercise();
            case ProgrammingExerciseBuildConfig buildConfig -> buildConfig.getProgrammingExercise();
            default -> null;
        };
        if (exerciseToVersion != null && exerciseToVersion.getId() != null) {
            if (exercisesToVersion.stream().noneMatch(e -> e.getId().equals(exerciseToVersion.getId()))) {
                exercisesToVersion.add(exerciseToVersion);
                this.currentUser = getUser();
            }
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
     * Safely creates an exercise version, handling any exceptions that might occur.
     * This prevents version creation failures from affecting the main exercise save operation.
     */
    private void createVersionSafely(Exercise exercise) {
        try {
            getExerciseVersionService().createExerciseVersion(exercise, this.currentUser);
        }
        catch (Exception e) {
            log.error("Failed to create exercise version for exercise {} ({}): {}", exercise.getId(), exercise.getTitle(), e.getMessage(), e);
        }
    }
}
