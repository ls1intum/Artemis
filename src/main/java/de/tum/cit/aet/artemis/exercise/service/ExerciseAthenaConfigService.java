package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATHENA;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseAthenaConfig;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseAthenaConfigRepository;

/**
 * Service for loading and persisting {@link ExerciseAthenaConfig} data explicitly.
 */
@Service
@Profile({ PROFILE_CORE, PROFILE_ATHENA })
@Lazy
public class ExerciseAthenaConfigService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseAthenaConfigService.class);

    private final ExerciseAthenaConfigRepository exerciseAthenaConfigRepository;

    public ExerciseAthenaConfigService(ExerciseAthenaConfigRepository exerciseAthenaConfigRepository) {
        this.exerciseAthenaConfigRepository = exerciseAthenaConfigRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ExerciseAthenaConfig> findByExerciseId(long exerciseId) {
        return exerciseAthenaConfigRepository.findByExerciseId(exerciseId);
    }

    @Transactional(readOnly = true)
    public void loadAthenaConfig(Exercise exercise) {
        if (exercise == null || exercise.getId() == null) {
            return;
        }
        findByExerciseId(exercise.getId()).ifPresent(exercise::setAthenaConfig);
    }

    /**
     * Creates, updates, or deletes the Athena configuration for the given exercise.
     *
     * @param exercise  the exercise for which the config should be updated
     * @param newConfig the new config. If {@code null} or empty, the existing config (if present) is removed
     */
    @Transactional
    public void updateAthenaConfig(Exercise exercise, ExerciseAthenaConfig newConfig) {
        if (exercise == null || exercise.getId() == null) {
            return;
        }

        Optional<ExerciseAthenaConfig> existing = exerciseAthenaConfigRepository.findByExerciseId(exercise.getId());

        if (newConfig == null || newConfig.isEmpty()) {
            log.debug("Removing Athena config for exercise {}", exercise.getId());
            existing.ifPresent(exerciseAthenaConfigRepository::delete);
            exercise.setAthenaConfig(null);
            return;
        }

        log.debug("Persisting Athena config for exercise {} with feedback module '{}' and preliminary module '{}'", exercise.getId(), newConfig.getFeedbackSuggestionModule(),
                newConfig.getPreliminaryFeedbackModule());
        ExerciseAthenaConfig configToPersist = existing.orElseGet(ExerciseAthenaConfig::new);
        configToPersist.setExercise(exercise);
        configToPersist.setFeedbackSuggestionModule(newConfig.getFeedbackSuggestionModule());
        configToPersist.setPreliminaryFeedbackModule(newConfig.getPreliminaryFeedbackModule());

        ExerciseAthenaConfig persisted = exerciseAthenaConfigRepository.save(configToPersist);
        exercise.setAthenaConfig(persisted);
    }

    @Transactional
    public void deleteByExerciseId(long exerciseId) {
        exerciseAthenaConfigRepository.deleteByExerciseId(exerciseId);
    }

    @Transactional(readOnly = true)
    public Set<Exercise> findAllFeedbackSuggestionsEnabledExercisesWithFutureDueDate() {
        return exerciseAthenaConfigRepository.findExercisesWithFeedbackSuggestionModuleAndDueDateAfter(ZonedDateTime.now());
    }
}
