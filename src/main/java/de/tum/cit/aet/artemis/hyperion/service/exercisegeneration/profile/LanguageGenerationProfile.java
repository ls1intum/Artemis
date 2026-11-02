package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.protocol.GenerationBuildTool;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationToolchain;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

/**
 * The Java configurations qualified by the generation worker.
 * <p>
 * One Java sandbox image builds both tools offline, so the {@code java-gradle} toolchain identifier (kept for deployed workers) serves Gradle and Maven alike; the build tool
 * travels in the brief. Each project type maps to the tool of its test harness ({@code templates/java/test/gradle}, {@code templates/java/test/maven}, or MAVEN_MAVEN's
 * superset). The project types of one tool differ only in whether the template and solution ship a sample third-party dependency, which the worker never touches. Static
 * code analysis, sequential test runs, black-box Maven, and auxiliary repositories are outside what the sandbox verifies.
 */
public final class LanguageGenerationProfile {

    private record Profile(GenerationToolchain toolchain, ProgrammingLanguage language, Map<ProjectType, GenerationBuildTool> projectTypes) {
    }

    private static final List<Profile> PROFILES = List
            .of(new Profile(GenerationToolchain.JAVA_GRADLE, ProgrammingLanguage.JAVA, Map.of(ProjectType.PLAIN_GRADLE, GenerationBuildTool.GRADLE, ProjectType.GRADLE_GRADLE,
                    GenerationBuildTool.GRADLE, ProjectType.PLAIN_MAVEN, GenerationBuildTool.MAVEN, ProjectType.MAVEN_MAVEN, GenerationBuildTool.MAVEN)));

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

    /**
     * Resolves the build tool of a qualified exercise's test harness.
     *
     * @param exercise the exercise whose language and project type were authorized
     * @return the tool whose canonical phases verify it
     */
    public static GenerationBuildTool buildToolFor(ProgrammingExercise exercise) {
        Profile profile = profileFor(exercise);
        if (profile == null) {
            throw new IllegalArgumentException("No authoring toolchain is qualified for this exercise");
        }
        return profile.projectTypes().get(exercise.getProjectType());
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
        return PROFILES.stream().filter(profile -> profile.language() == exercise.getProgrammingLanguage() && exercise.getProjectType() != null
                && profile.projectTypes().containsKey(exercise.getProjectType())).findFirst().orElse(null);
    }

    public static boolean isSupported(@Nullable ProgrammingExercise exercise, @Nullable ProgrammingExerciseBuildConfig buildConfig, boolean hasAuxiliaryRepositories) {
        return isSupported(exercise, buildConfig) && !hasAuxiliaryRepositories;
    }
}
