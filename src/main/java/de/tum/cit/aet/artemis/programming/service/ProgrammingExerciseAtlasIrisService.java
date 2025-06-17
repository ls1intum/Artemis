package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashSet;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseAtlasIrisService.class);

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final Optional<IrisSettingsApi> irisSettingsApi;

    public ProgrammingExerciseAtlasIrisService(Optional<CompetencyProgressApi> competencyProgressApi, Optional<IrisSettingsApi> irisSettingsApi) {
        this.competencyProgressApi = competencyProgressApi;
        this.irisSettingsApi = irisSettingsApi;
    }

    public void updateCompetencyProgressAndEnableIris(ProgrammingExercise exercise) {
        competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectAsync(exercise));
        irisSettingsApi.ifPresent(settingsApi -> settingsApi.setEnabledForExerciseByCategories(exercise, new HashSet<>()));
    }

}
