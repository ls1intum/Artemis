package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.iris.api.IrisSettingsApi;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

@Service
@Lazy
@Profile(PROFILE_CORE)
public class ProgrammingExerciseAtlasIrisService {

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final Optional<IrisSettingsApi> irisSettingsApi;

    public ProgrammingExerciseAtlasIrisService(Optional<CompetencyProgressApi> competencyProgressApi, Optional<IrisSettingsApi> irisSettingsApi) {
        this.competencyProgressApi = competencyProgressApi;
        this.irisSettingsApi = irisSettingsApi;
    }

    public void updateCompetencyProgressOnCreationAndEnableIris(ProgrammingExercise exercise) {
        competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectAsync(exercise));
        enableIrisForExercise(exercise);
    }

    public void updateCompetencyProgressOnExerciseUpdateAndEnableIris(ProgrammingExercise programmingExerciseBeforeUpdate, ProgrammingExercise programmingExerciseAfterUpdate) {
        competencyProgressApi.ifPresent(api -> api.updateProgressForUpdatedLearningObjectAsync(programmingExerciseBeforeUpdate, Optional.of(programmingExerciseAfterUpdate)));
        enableIrisForExercise(programmingExerciseAfterUpdate, programmingExerciseBeforeUpdate.getCategories());
    }

    public void enableIrisForExercise(ProgrammingExercise exercise) {
        enableIrisForExercise(exercise, new HashSet<>());
    }

    public void enableIrisForExercise(ProgrammingExercise exercise, Set<String> categories) {
        irisSettingsApi.ifPresent(settingsApi -> settingsApi.setEnabledForExerciseByCategories(exercise, categories));
    }

}
