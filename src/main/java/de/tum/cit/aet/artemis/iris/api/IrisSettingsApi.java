package de.tum.cit.aet.artemis.iris.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.iris.dto.IrisCombinedSettingsDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisExerciseSettingsRepository;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;

@Profile(PROFILE_IRIS)
@Controller
public class IrisSettingsApi extends AbstractIrisApi {

    private final IrisExerciseSettingsRepository irisExerciseSettingsRepository;

    private final IrisSettingsService irisSettingsService;

    public IrisSettingsApi(IrisExerciseSettingsRepository irisExerciseSettingsRepository, IrisSettingsService irisSettingsService) {
        this.irisExerciseSettingsRepository = irisExerciseSettingsRepository;
        this.irisSettingsService = irisSettingsService;
    }

    public IrisCombinedSettingsDTO getCombinedIrisSettingsFor(Exercise exercise, boolean minimal) {
        return irisSettingsService.getCombinedIrisSettingsFor(exercise, minimal);
    }

    public boolean isExerciseChatEnabled(long exerciseId) {
        return irisExerciseSettingsRepository.isExerciseChatEnabled(exerciseId);
    }

    public boolean shouldShowMinimalSettings(Exercise exercise, User user) {
        return irisSettingsService.shouldShowMinimalSettings(exercise, user);
    }

    public void setEnabledForExerciseByCategories(Exercise exercise, Set<String> oldExerciseCategories) {
        irisSettingsService.setEnabledForExerciseByCategories(exercise, oldExerciseCategories);
    }

    public void deleteSettingsFor(Course course) {
        irisSettingsService.deleteSettingsFor(course);
    }

    public void deleteSettingsFor(Exercise exercise) {
        irisSettingsService.deleteSettingsFor(exercise);
    }
}
