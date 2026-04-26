package de.tum.cit.aet.artemis.exercise.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseAthenaConfig;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseAthenaConfigRepository;

@Service
public class ExerciseAthenaConfigService {

    private final ExerciseAthenaConfigRepository configRepository;

    public ExerciseAthenaConfigService(ExerciseAthenaConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ExerciseAthenaConfig> findByExerciseId(Long exerciseId) {
        return configRepository.findByExerciseId(exerciseId);
    }

    @Transactional
    public ExerciseAthenaConfig save(ExerciseAthenaConfig config) {
        return configRepository.save(config);
    }

    @Transactional
    public void deleteByExerciseId(Long exerciseId) {
        configRepository.deleteByExerciseId(exerciseId);
    }

    @Transactional
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

        return configRepository.save(config);
    }
}
