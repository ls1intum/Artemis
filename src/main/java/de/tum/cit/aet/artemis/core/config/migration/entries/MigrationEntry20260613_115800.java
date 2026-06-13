package de.tum.cit.aet.artemis.core.config.migration.entries;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.core.config.migration.MigrationEntry;
import de.tum.cit.aet.artemis.localci.service.LegacyBuildPlanConverterService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;

/**
 * Migrates LocalCI legacy build plans from the old build script plus build plan configuration format to build plan phases.
 */
public class MigrationEntry20260613_115800 extends MigrationEntry {

    private static final Logger log = LoggerFactory.getLogger(MigrationEntry20260613_115800.class);

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    private final LegacyBuildPlanConverterService legacyBuildPlanConverterService;

    public MigrationEntry20260613_115800(ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository,
            LegacyBuildPlanConverterService legacyBuildPlanConverterService) {
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
        this.legacyBuildPlanConverterService = legacyBuildPlanConverterService;
    }

    @Override
    public void execute() {
        List<ProgrammingExerciseBuildConfig> migrationCandidates = programmingExerciseBuildConfigRepository.findAllWithLegacyBuildScriptAndBuildPlanConfiguration();
        List<ProgrammingExerciseBuildConfig> migratedBuildConfigs = new ArrayList<>();
        int skippedBuildConfigs = 0;

        for (ProgrammingExerciseBuildConfig buildConfig : migrationCandidates) {
            var convertedBuildPlan = legacyBuildPlanConverterService.convertLegacyBuildPlanConfiguration(buildConfig);
            if (convertedBuildPlan.isEmpty()) {
                skippedBuildConfigs++;
                continue;
            }

            try {
                buildConfig.setBuildPlanConfiguration(convertedBuildPlan.orElseThrow().toBuildPlanConfiguration());
                buildConfig.setBuildScript(null);
                migratedBuildConfigs.add(buildConfig);
            }
            catch (JsonProcessingException e) {
                skippedBuildConfigs++;
                log.warn("Could not serialize converted LocalCI legacy build plan for build config {}", buildConfig.getId(), e);
            }
        }

        programmingExerciseBuildConfigRepository.saveAll(migratedBuildConfigs);
        log.info("Migrated {} LocalCI legacy build plans to build plan phases format. Skipped {} build configs.", migratedBuildConfigs.size(), skippedBuildConfigs);
    }

    @Override
    public String author() {
        return "Mátyás Hoffer-Tóth";
    }

    @Override
    public String date() {
        return "20260613_115800";
    }
}
