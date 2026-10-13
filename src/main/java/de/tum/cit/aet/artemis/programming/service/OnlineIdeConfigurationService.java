package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.programming.domain.OnlineIdeExperienceLevel;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;

/**
 * Provides the files that configure the online IDE (EduIDE) for a student repository.
 * <p>
 * EduIDE reads {@code .vscode/eduide.json} from the workspace root, which is where the student repository is cloned. It only applies the {@code level} in it to
 * students who have not chosen a level themselves, so the file sets where a student starts, not where they have to stay.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class OnlineIdeConfigurationService {

    /**
     * The path of the EduIDE configuration file, relative to the repository root.
     */
    public static final String EDU_IDE_CONFIGURATION_PATH = ".vscode/eduide.json";

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    public OnlineIdeConfigurationService(ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
    }

    /**
     * Gets the files to add to a student repository created from the template of the given exercise.
     *
     * @param programmingExercise the exercise the student repository belongs to
     * @return the files by repository-relative path; empty unless the online IDE is allowed and an experience level was selected
     */
    public Map<String, byte[]> getFilesForStudentRepository(ProgrammingExercise programmingExercise) {
        if (!programmingExercise.isAllowOnlineIde()) {
            return Map.of();
        }
        OnlineIdeExperienceLevel level = programmingExerciseBuildConfigRepository.getOnlineIdeExperienceLevelByProgrammingExerciseId(programmingExercise.getId());
        if (level == null) {
            return Map.of();
        }
        return Map.of(EDU_IDE_CONFIGURATION_PATH, createEduIdeConfiguration(level).getBytes(StandardCharsets.UTF_8));
    }

    private static String createEduIdeConfiguration(OnlineIdeExperienceLevel level) {
        return """
                // Created by Artemis from the settings of this exercise.
                // The experience level you start at in the online IDE. Once you choose a level yourself, this file no longer applies.
                {
                    "level": "%s"
                }
                """.formatted(level.toEduIdeValue());
    }
}
