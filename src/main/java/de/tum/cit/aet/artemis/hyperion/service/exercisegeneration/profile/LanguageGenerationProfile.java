package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.protocol.GenerationToolchain;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

/**
 * The Java Gradle configuration qualified by the generation worker.
 * <p>
 * Both Gradle project types are qualified because they share one test harness ({@code templates/java/test/gradle}); they differ only in whether the template and solution
 * ship a sample third-party dependency, which the worker never touches. Maven, static code analysis, and sequential test runs are outside what the sandbox verifies.
 */
public final class LanguageGenerationProfile {

    private record Profile(GenerationToolchain toolchain, ProgrammingLanguage language, Set<ProjectType> projectTypes) {
    }

    private static final List<Profile> PROFILES = List
            .of(new Profile(GenerationToolchain.JAVA_GRADLE, ProgrammingLanguage.JAVA, EnumSet.of(ProjectType.PLAIN_GRADLE, ProjectType.GRADLE_GRADLE)));

    private LanguageGenerationProfile() {
    }

    /**
     * Resolves only qualified exercise/toolchain pairs; a worker advertisement cannot expand product support.
     *
     * @param exercise the exercise whose language and project type were authorized
     * @return its qualified toolchain
     */
    public static GenerationToolchain toolchainFor(ProgrammingExercise exercise) {
        Profile profile = profileFor(exercise);
        if (profile == null) {
            throw new IllegalArgumentException("No authoring toolchain is qualified for this exercise");
        }
        return profile.toolchain();
    }

    public static Set<ProgrammingLanguage> supportedLanguages() {
        return PROFILES.stream().map(Profile::language).collect(Collectors.toUnmodifiableSet());
    }

    public static boolean isSupported(@Nullable ProgrammingExercise exercise, @Nullable ProgrammingExerciseBuildConfig buildConfig) {
        return profileFor(exercise) != null && !Boolean.TRUE.equals(exercise.isStaticCodeAnalysisEnabled()) && (buildConfig != null && !buildConfig.hasSequentialTestRuns());
    }

    @Nullable
    private static Profile profileFor(@Nullable ProgrammingExercise exercise) {
        if (exercise == null) {
            return null;
        }
        return PROFILES.stream().filter(profile -> profile.language() == exercise.getProgrammingLanguage() && profile.projectTypes().contains(exercise.getProjectType()))
                .findFirst().orElse(null);
    }

    public static boolean isSupported(@Nullable ProgrammingExercise exercise, @Nullable ProgrammingExerciseBuildConfig buildConfig, boolean hasAuxiliaryRepositories) {
        return isSupported(exercise, buildConfig) && !hasAuxiliaryRepositories;
    }
}
