package de.tum.in.www1.artemis.config.migration.entries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.connectors.ci.CIMigrationService;

@Component
public class MigrationEntry20230602_111100 extends MigrationEntry {

    private final Logger log = LoggerFactory.getLogger(MigrationEntry20230602_111100.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final Optional<CIMigrationService> ciMigrationService;

    public MigrationEntry20230602_111100(ProgrammingExerciseRepository programmingExerciseRepository, Optional<CIMigrationService> ciMigrationService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.ciMigrationService = ciMigrationService;
    }

    @Override
    public void execute() {
        long exerciseCount = programmingExerciseRepository.count();
        if (exerciseCount == 0 && ciMigrationService.isEmpty()) {
            return;
        }

        log.info("Migrating {} programming exercises", exerciseCount);

        // Maps exercise to a boolean indicating whether the exercise had an issue with the template (true) or
        // solution (false) build plan
        // Didn't use an enum for simplicity of the migration
        Map<ProgrammingExercise, Boolean> errorMap = new HashMap<>();

        // do batches
        int batchSize = 100;
        for (int i = 0, j = 0; i < exerciseCount; i += batchSize, j++) {
            Pageable pageable = PageRequest.of(j, batchSize);
            programmingExerciseRepository.findAll(pageable).forEach(exercise -> migrateExercise(exercise, errorMap));
            log.info("Migrated {} / {} programming exercises", i + 1, exerciseCount);
        }

        if (!errorMap.isEmpty()) {
            List<Long> failedExerciseIdsForTemplates = errorMap.entrySet().stream().filter(entry -> Boolean.TRUE.equals(entry.getValue())).map(entry -> entry.getKey().getId())
                    .toList();
            List<Long> failedExerciseIdsForSolutions = errorMap.entrySet().stream().filter(entry -> Boolean.FALSE.equals(entry.getValue())).map(entry -> entry.getKey().getId())
                    .toList();
            log.warn("Failed to migrate {} programming exercises", errorMap.size());
            log.warn("Failed to migrate template build plan for exercises: {}", failedExerciseIdsForTemplates);
            log.warn("Failed to migrate solution build plan for exercises: {}", failedExerciseIdsForSolutions);
            log.warn(
                    "Please check the logs for more information. Then either fix the issues and rerun the migration or fix the build plans yourself and mark the migration as run.");
        }
    }

    private void migrateExercise(ProgrammingExercise exercise, Map<ProgrammingExercise, Boolean> errorMap) {
        try {
            ciMigrationService.orElseThrow().overrideBuildPlanNotification(exercise.getProjectKey(), exercise.getTemplateBuildPlanId(), exercise.getVcsTemplateRepositoryUrl());
        }
        catch (ContinuousIntegrationException e) {
            log.warn("Failed to migrate template build plan for exercise {}", exercise.getId(), e);
            errorMap.put(exercise, true);
        }
        try {
            ciMigrationService.orElseThrow().overrideBuildPlanNotification(exercise.getProjectKey(), exercise.getSolutionBuildPlanId(), exercise.getVcsSolutionRepositoryUrl());
        }
        catch (ContinuousIntegrationException e) {
            log.warn("Failed to migrate solution build plan for exercise {}", exercise.getId(), e);
            errorMap.put(exercise, false);
        }
    }

    @Override
    public String author() {
        return "julian_christl";
    }

    @Override
    public String date() {
        return "20230602_111100";
    }
}
