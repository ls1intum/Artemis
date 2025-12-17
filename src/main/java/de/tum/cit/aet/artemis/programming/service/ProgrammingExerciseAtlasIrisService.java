package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

@Service
@Lazy
@Profile(PROFILE_CORE)
public class ProgrammingExerciseAtlasIrisService {

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    public ProgrammingExerciseAtlasIrisService(Optional<CompetencyProgressApi> competencyProgressApi) {
        this.competencyProgressApi = competencyProgressApi;
    }

    public void updateCompetencyProgressOnCreation(ProgrammingExercise exercise) {
        competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectAsync(exercise));
    }

    public void updateCompetencyProgressOnExerciseUpdate(ProgrammingExercise programmingExerciseBeforeUpdate, ProgrammingExercise programmingExerciseAfterUpdate) {
        competencyProgressApi.ifPresent(api -> api.updateProgressForUpdatedLearningObjectAsync(programmingExerciseBeforeUpdate, Optional.of(programmingExerciseAfterUpdate)));
    }

}
