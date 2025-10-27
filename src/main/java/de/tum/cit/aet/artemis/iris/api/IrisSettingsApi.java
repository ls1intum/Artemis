package de.tum.cit.aet.artemis.iris.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.iris.dto.IrisCombinedSettingsDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisExerciseSettingsRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSettingsRepository;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;

@Profile(PROFILE_IRIS)
@Controller
@Lazy
public class IrisSettingsApi extends AbstractIrisApi {

    private final IrisExerciseSettingsRepository irisExerciseSettingsRepository;

    private final IrisSettingsService irisSettingsService;

    private final IrisSettingsRepository irisSettingsRepository;

    public IrisSettingsApi(IrisExerciseSettingsRepository irisExerciseSettingsRepository, IrisSettingsService irisSettingsService, IrisSettingsRepository irisSettingsRepository) {
        this.irisExerciseSettingsRepository = irisExerciseSettingsRepository;
        this.irisSettingsService = irisSettingsService;
        this.irisSettingsRepository = irisSettingsRepository;
    }

    public IrisCombinedSettingsDTO getCombinedIrisSettingsFor(Exercise exercise, boolean minimal) {
        return irisSettingsService.getCombinedIrisSettingsFor(exercise, minimal);
    }

    public boolean isProgrammingExerciseChatEnabled(long exerciseId) {
        return !Boolean.FALSE.equals(irisExerciseSettingsRepository.isProgrammingExerciseChatEnabled(exerciseId));
    }

    public boolean isCourseChatEnabled(long courseId) {
        return !Boolean.FALSE.equals(irisSettingsRepository.isCourseChatEnabled(courseId));
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

    public void deleteSettingsForExercise(long exerciseId) {
        irisSettingsService.deleteSettingsForExercise(exerciseId);
    }
}
