package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

/** The canonical Java Gradle configuration qualified by the generation worker. */
public final class LanguageGenerationProfile {

    private LanguageGenerationProfile() {
    }

    public static Set<ProgrammingLanguage> supportedLanguages() {
        return Set.of(ProgrammingLanguage.JAVA);
    }

    public static boolean isSupported(@Nullable ProgrammingExercise exercise) {
        return exercise != null && exercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA && exercise.getProjectType() == ProjectType.GRADLE_GRADLE
                && !Boolean.TRUE.equals(exercise.isStaticCodeAnalysisEnabled()) && (exercise.getBuildConfig() == null || !exercise.getBuildConfig().hasSequentialTestRuns());
    }

    public static boolean isSupported(@Nullable ProgrammingExercise exercise, boolean hasAuxiliaryRepositories) {
        return isSupported(exercise) && !hasAuxiliaryRepositories;
    }
}
