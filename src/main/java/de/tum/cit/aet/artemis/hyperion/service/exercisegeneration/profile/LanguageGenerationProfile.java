package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile;

import java.util.EnumSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

/**
 * The Java Gradle configuration qualified by the generation worker.
 * <p>
 * Both Gradle project types are qualified because they share one test harness ({@code templates/java/test/gradle}); they differ only in whether the template and solution
 * ship a sample third-party dependency, which the worker never touches. Maven, static code analysis, and sequential test runs are outside what the sandbox verifies.
 */
public final class LanguageGenerationProfile {

    /** An {@link EnumSet}, whose {@code contains(null)} is {@code false}, because a project type can be unset. */
    private static final Set<ProjectType> SUPPORTED_PROJECT_TYPES = EnumSet.of(ProjectType.PLAIN_GRADLE, ProjectType.GRADLE_GRADLE);

    private LanguageGenerationProfile() {
    }

    public static Set<ProgrammingLanguage> supportedLanguages() {
        return Set.of(ProgrammingLanguage.JAVA);
    }

    public static boolean isSupported(@Nullable ProgrammingExercise exercise) {
        return exercise != null && exercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA && SUPPORTED_PROJECT_TYPES.contains(exercise.getProjectType())
                && !Boolean.TRUE.equals(exercise.isStaticCodeAnalysisEnabled()) && (exercise.getBuildConfig() == null || !exercise.getBuildConfig().hasSequentialTestRuns());
    }

    public static boolean isSupported(@Nullable ProgrammingExercise exercise, boolean hasAuxiliaryRepositories) {
        return isSupported(exercise) && !hasAuxiliaryRepositories;
    }
}
