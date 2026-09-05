package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.buildagent.dto.DockerFlagsDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/**
 * Unit tests for the settings an instructor is not allowed to save.
 * <p>
 * These checks are the last thing between an exercise configuration and the build agents that run it. A package name
 * that is a keyword produces a repository that does not compile, a container without enough memory produces builds that
 * die without a usable error, and a checkout path that escapes its directory writes outside the build. Each rejection
 * therefore has to happen for the right reason and, just as importantly, must not reject a configuration that is fine.
 */
@ExtendWith(MockitoExtension.class)
class ProgrammingExerciseValidationServiceTest {

    @Mock
    private AuxiliaryRepositoryService auxiliaryRepositoryService;

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private SubmissionPolicyService submissionPolicyService;

    @Mock
    private ProgrammingLanguageFeatureService programmingLanguageFeatureService;

    @Mock
    private ProgrammingExerciseBuildConfigService programmingExerciseBuildConfigService;

    @Mock
    private ProgrammingExerciseTestCaseTestRepository programmingExerciseTestCaseRepository;

    @Mock
    private ProfileService profileService;

    private ProgrammingExerciseValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new ProgrammingExerciseValidationService(auxiliaryRepositoryService, programmingExerciseRepository, submissionPolicyService,
                Optional.of(programmingLanguageFeatureService), Optional.empty(), programmingExerciseBuildConfigService, Optional.empty(), programmingExerciseTestCaseRepository,
                profileService);
    }

    private ProgrammingExercise exerciseWithPackageName(ProgrammingLanguage programmingLanguage, String packageName) {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(programmingLanguage);
        exercise.setPackageName(packageName);
        when(programmingLanguageFeatureService.getProgrammingLanguageFeatures(programmingLanguage))
                .thenReturn(new ProgrammingLanguageFeature(programmingLanguage, false, false, true, true, false, List.of(), true));
        return exercise;
    }

    private ProgrammingExercise exerciseWithDockerFlags(DockerFlagsDTO dockerFlags) {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setBuildConfig(new ProgrammingExerciseBuildConfig());
        when(programmingExerciseBuildConfigService.parseDockerFlags(any())).thenReturn(dockerFlags);
        return exercise;
    }

    @ParameterizedTest
    @ValueSource(strings = { "de.test", "de.tum.cit.aet", "a", "de.test.sorting" })
    void validatePackageName_acceptsAWellFormedJavaPackage(String packageName) {
        assertThatCode(() -> validationService.validatePackageName(exerciseWithPackageName(ProgrammingLanguage.JAVA, packageName))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = { "de.class", "package", "de..test", "de.test.", "1de.test", "de test", "de.test$" })
    void validatePackageName_rejectsAJavaPackageThatWouldNotCompile(String packageName) {
        // A reserved word or an illegal character produces a template repository that does not compile, which the student cannot fix.
        assertThatExceptionOfType(BadRequestAlertException.class).as("the package name '%s' must be rejected", packageName)
                .isThrownBy(() -> validationService.validatePackageName(exerciseWithPackageName(ProgrammingLanguage.JAVA, packageName))).withMessageContaining("invalid");
    }

    @Test
    void validatePackageName_rejectsAMissingPackageNameWhereTheLanguageNeedsOne() {
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validatePackageName(exerciseWithPackageName(ProgrammingLanguage.JAVA, null)))
                .withMessageContaining("invalid");
    }

    @Test
    void validatePackageName_rejectsAPackageNameLongerThanTheColumnHolds() {
        String tooLong = "de." + "a".repeat(100);

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> validationService.validatePackageName(exerciseWithPackageName(ProgrammingLanguage.JAVA, tooLong))).withMessageContaining("too long");
    }

    @Test
    void validatePackageName_appliesTheRulesOfTheLanguageRatherThanJavaRules() {
        // Swift, Go and Dart each have their own keywords and their own idea of a legal name, and a dotted Java package is not one of them.
        assertThatCode(() -> validationService.validatePackageName(exerciseWithPackageName(ProgrammingLanguage.SWIFT, "swiftTest"))).doesNotThrowAnyException();
        assertThatExceptionOfType(BadRequestAlertException.class).as("a dotted name is not a Swift package")
                .isThrownBy(() -> validationService.validatePackageName(exerciseWithPackageName(ProgrammingLanguage.SWIFT, "de.test")));

        assertThatCode(() -> validationService.validatePackageName(exerciseWithPackageName(ProgrammingLanguage.GO, "sorting"))).doesNotThrowAnyException();
        assertThatExceptionOfType(BadRequestAlertException.class).as("a Go keyword is not a package name")
                .isThrownBy(() -> validationService.validatePackageName(exerciseWithPackageName(ProgrammingLanguage.GO, "package")));

        assertThatCode(() -> validationService.validatePackageName(exerciseWithPackageName(ProgrammingLanguage.DART, "test_package"))).doesNotThrowAnyException();
        assertThatExceptionOfType(BadRequestAlertException.class).as("a Dart keyword is not a package name")
                .isThrownBy(() -> validationService.validatePackageName(exerciseWithPackageName(ProgrammingLanguage.DART, "class")));
    }

    @Test
    void validatePackageName_forALanguageThatNeedsNone_acceptsAnything() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.PYTHON);
        exercise.setPackageName(null);
        when(programmingLanguageFeatureService.getProgrammingLanguageFeatures(ProgrammingLanguage.PYTHON))
                .thenReturn(new ProgrammingLanguageFeature(ProgrammingLanguage.PYTHON, false, false, true, false, false, List.of(), true));

        assertThatCode(() -> validationService.validatePackageName(exercise)).doesNotThrowAnyException();
    }

    @Test
    void validateDockerFlags_rejectsAContainerTooSmallToBuildIn() {
        // A container below the minimum dies during the build without an error a student could act on.
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateDockerFlags(exerciseWithDockerFlags(flags(1, 2, 0))))
                .withMessageContaining("memory limit is invalid");
    }

    @Test
    void validateDockerFlags_rejectsAContainerWithoutACpu() {
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateDockerFlags(exerciseWithDockerFlags(flags(0, 1024, 0))))
                .withMessageContaining("cpu count is invalid");
    }

    @Test
    void validateDockerFlags_rejectsANegativeSwapLimit() {
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateDockerFlags(exerciseWithDockerFlags(flags(1, 1024, -1))))
                .withMessageContaining("memory swap limit is invalid");
    }

    @Test
    void validateDockerFlags_rejectsAnEnvironmentVariableTooLongToPass() {
        DockerFlagsDTO withLongValue = new DockerFlagsDTO(null, Map.of("KEY", "v".repeat(1001)), 1, 1024, 0);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateDockerFlags(exerciseWithDockerFlags(withLongValue)))
                .withMessageContaining("environment variables are too long");
    }

    @Test
    void validateDockerFlags_acceptsAUsableConfiguration() {
        assertThatCode(() -> validationService.validateDockerFlags(exerciseWithDockerFlags(new DockerFlagsDTO(null, Map.of("KEY", "value"), 2, 2048, 0))))
                .doesNotThrowAnyException();
    }

    @Test
    void validateDockerFlags_withoutAnyFlags_acceptsTheExercise() {
        // Most exercises configure no flags at all and then run with the instance defaults.
        assertThatCode(() -> validationService.validateDockerFlags(exerciseWithDockerFlags(null))).doesNotThrowAnyException();
    }

    @Test
    void validateDockerFlags_whenTheFlagsCannotBeParsed_saysSoRatherThanFailingLater() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setBuildConfig(new ProgrammingExerciseBuildConfig());
        when(programmingExerciseBuildConfigService.parseDockerFlags(any())).thenThrow(new IllegalArgumentException("not json"));

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateDockerFlags(exercise))
                .withMessageContaining("parsing the docker flags");
    }

    @Test
    void validateBuildConfigSize_rejectsABuildConfigurationTooLargeForTheColumn() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setBuildPlanConfiguration("x".repeat(1024 * 1024 + 1));
        exercise.setBuildConfig(buildConfig);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateBuildConfigSize(exercise))
                .withMessageContaining("build plan configuration is too long");
    }

    @Test
    void validateBuildConfigSize_rejectsDockerFlagsTooLargeForTheColumn() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setDockerFlags("x".repeat(8 * 1024 + 1));
        exercise.setBuildConfig(buildConfig);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateBuildConfigSize(exercise))
                .withMessageContaining("docker flags are too long");
    }

    @Test
    void validateBuildConfigSize_acceptsAConfigurationWithinTheLimits() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setBuildPlanConfiguration("{}");
        buildConfig.setDockerFlags("{}");
        exercise.setBuildConfig(buildConfig);

        assertThatCode(() -> validationService.validateBuildConfigSize(exercise)).doesNotThrowAnyException();
        exercise.setBuildConfig(null);
        assertThatCode(() -> validationService.validateBuildConfigSize(exercise)).as("an exercise without a build config has nothing to check").doesNotThrowAnyException();
    }

    @Test
    void validateCheckoutDirectoriesUnchanged_rejectsAChangeAfterTheExerciseExists() {
        // The checkout paths are baked into the build script and into every repository already checked out, so they cannot move once students have started.
        ProgrammingExercise original = exerciseWithCheckoutPaths("assignment", "solution", "tests");
        ProgrammingExercise updated = exerciseWithCheckoutPaths("somewhere-else", "solution", "tests");

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateCheckoutDirectoriesUnchanged(original, updated))
                .withMessageContaining("cannot be changed");
    }

    @Test
    void validateCheckoutDirectoriesUnchanged_acceptsAnUpdateThatLeavesThemAlone() {
        ProgrammingExercise original = exerciseWithCheckoutPaths("assignment", "solution", "tests");
        ProgrammingExercise updated = exerciseWithCheckoutPaths("assignment", "solution", "tests");

        assertThatCode(() -> validationService.validateCheckoutDirectoriesUnchanged(original, updated)).doesNotThrowAnyException();
        assertThat(updated.getBuildConfig().getAssignmentCheckoutPath()).isEqualTo("assignment");
    }

    private static DockerFlagsDTO flags(int cpuCount, int memory, int memorySwap) {
        return new DockerFlagsDTO(null, Map.of(), cpuCount, memory, memorySwap);
    }

    private static ProgrammingExercise exerciseWithCheckoutPaths(String assignment, String solution, String tests) {
        ProgrammingExercise exercise = new ProgrammingExercise();
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setAssignmentCheckoutPath(assignment);
        buildConfig.setSolutionCheckoutPath(solution);
        buildConfig.setTestCheckoutPath(tests);
        exercise.setBuildConfig(buildConfig);
        return exercise;
    }
}
