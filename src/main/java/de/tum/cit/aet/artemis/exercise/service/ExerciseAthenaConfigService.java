package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseAthenaConfig;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseAthenaConfigRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ExerciseAthenaConfigService {

    private final ExerciseAthenaConfigRepository configRepository;

    public ExerciseAthenaConfigService(ExerciseAthenaConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    public void deleteByExerciseId(Long exerciseId) {
        configRepository.deleteByExerciseId(exerciseId);
    }

    /**
     * Creates or updates the Athena config for the given exercise.
     *
     * @param exercise          the exercise to configure
     * @param preliminaryModule the module name for preliminary feedback, or null to clear
     * @param gradedModule      the module name for graded feedback, or null to clear
     * @return the saved ExerciseAthenaConfig
     */
    public ExerciseAthenaConfig createOrUpdateConfig(Exercise exercise, String preliminaryModule, String gradedModule) {
        Optional<ExerciseAthenaConfig> existingConfig = configRepository.findByExerciseId(exercise.getId());

        ExerciseAthenaConfig config;
        if (existingConfig.isPresent()) {
            config = existingConfig.get();
            config.setPreliminaryFeedbackModule(preliminaryModule);
            config.setGradedFeedbackModule(gradedModule);
        }
        else {
            config = new ExerciseAthenaConfig();
            config.setExercise(exercise);
            config.setPreliminaryFeedbackModule(preliminaryModule);
            config.setGradedFeedbackModule(gradedModule);
        }

        try {
            return configRepository.save(config);
        }
        catch (DataIntegrityViolationException e) {
            // Another transaction inserted the row between our findByExerciseId and save.
            // Each repository call runs in its own transaction (no outer @Transactional here),
            // so the retry read and save succeed in fresh transactions.
            existingConfig = configRepository.findByExerciseId(exercise.getId());
            if (existingConfig.isPresent()) {
                config = existingConfig.get();
                config.setPreliminaryFeedbackModule(preliminaryModule);
                config.setGradedFeedbackModule(gradedModule);
                return configRepository.save(config);
            }
            throw e;
        }
    }
}
