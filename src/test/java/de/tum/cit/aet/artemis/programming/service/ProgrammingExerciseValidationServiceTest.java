package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import de.tum.cit.aet.artemis.buildagent.dto.DockerFlagsDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.programming.domain.BuildTimeWindow;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
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

    private static final ZonedDateTime NOW = ZonedDateTime.now();

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

    @Mock
    private ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    private ProgrammingExerciseValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new ProgrammingExerciseValidationService(programmingExerciseBuildConfigRepository, auxiliaryRepositoryService, programmingExerciseRepository,
                submissionPolicyService, Optional.of(programmingLanguageFeatureService), Optional.empty(), programmingExerciseBuildConfigService, Optional.empty(),
                programmingExerciseTestCaseRepository, profileService, new MockEnvironment());
    }

    /**
     * Builds the service the way Spring does, with the allow-list read from real property sources.
     */
    private ProgrammingExerciseValidationService serviceWithAllowedRegistries(MockEnvironment environment) {
        return new ProgrammingExerciseValidationService(programmingExerciseBuildConfigRepository, auxiliaryRepositoryService, programmingExerciseRepository,
                submissionPolicyService, Optional.of(programmingLanguageFeatureService), Optional.empty(), programmingExerciseBuildConfigService, Optional.empty(),
                programmingExerciseTestCaseRepository, profileService, environment);
    }

    private ProgrammingExerciseValidationService serviceWithAllowedRegistries(String... registries) {
        MockEnvironment environment = new MockEnvironment();
        for (int i = 0; i < registries.length; i++) {
            environment.setProperty("artemis.continuous-integration.allowed-docker-registries[" + i + "]", registries[i]);
        }
        return serviceWithAllowedRegistries(environment);
    }

    private ProgrammingExercise exerciseWithPackageName(ProgrammingLanguage programmingLanguage, String packageName) {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(programmingLanguage);
        exercise.setPackageName(packageName);
        when(programmingLanguageFeatureService.getProgrammingLanguageFeatures(programmingLanguage))
                .thenReturn(new ProgrammingLanguageFeature(programmingLanguage, false, false, true, true, false, List.of(), true));
        return exercise;
    }

    private ProgrammingExerciseBuildConfig buildConfigWithDockerFlags(DockerFlagsDTO dockerFlags) {
        when(programmingExerciseBuildConfigService.parseDockerFlags(any())).thenReturn(dockerFlags);
        return new ProgrammingExerciseBuildConfig();
    }

    @ParameterizedTest
    @ValueSource(strings = { "de.test", "de.tum.cit.aet", "a", "de.test.sorting", "de.tum.in", "net.java", "org.example", "javax2.test", "de.tum.cit.ase.aresx", "dee",
            "org.jetbrainsx", "kotlinx.test" })
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

    @ParameterizedTest
    @ValueSource(strings = { "de", "de.tum", "de.tum.cit", "de.tum.cit.ase", "de.tum.cit.ase.ares", "com", "org", "net", "com.ibm", "com.tngtech", "java", "java.util",
            "javax.swing", "sun.misc", "jdk.internal", "com.sun", "com.sun.test", "net.bytebuddy", "org.aspectj.lang", "com.ibm.wala.test", "com.tngtech.archunit",
            "de.tum.cit.ase.ares.api", "de.tum.cit.ase.ares.api.test", "de.tum.cit.ase.ares.other", "org.apache.commons", "com.google.common", "org.junit.jupiter",
            "kotlin.collections", "org.jetbrains.annotations" })
    void validatePackageName_rejectsAPackageThatAresCannotSupervise(String packageName) {
        // Ares 2 refuses to run any test when a compiled class lies in a package that contains or lies inside one of its reserved namespaces,
        // and the build guards of the test templates reject student classes in the packages of Ares and of the libraries that come with it.
        for (ProgrammingLanguage language : List.of(ProgrammingLanguage.JAVA, ProgrammingLanguage.KOTLIN)) {
            assertThatExceptionOfType(BadRequestAlertException.class).as("the package name '%s' must be rejected for %s", packageName, language)
                    .isThrownBy(() -> validationService.validatePackageName(exerciseWithPackageName(language, packageName))).withMessageContaining("invalid");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "templates/java/test/maven/projectTemplate/pom.xml", "templates/java/maven_maven/test/projectTemplate/pom.xml",
            "templates/java/test/gradle/projectTemplate/gradle/AresReservedPackages.gradle", "templates/kotlin/test/maven/projectTemplate/pom.xml" })
    void validatePackageName_rejectsEveryPackageThatTheBuildGuardsOfTheTemplatesReject(String template) throws IOException {
        String content = new ClassPathResource(template).getContentAsString(StandardCharsets.UTF_8);
        // Both the Maven guard (<include name="org/apache/**"/>) and the Gradle guard ('org/apache/**') list a reserved package as a path with a trailing /**
        List<String> reservedPackages = Pattern.compile("[\"']([A-Za-z0-9_/]+)/\\*\\*[\"']").matcher(content).results().map(match -> match.group(1).replace('/', '.')).toList();
        assertThat(reservedPackages).as("the reserved packages that are listed in %s", template).hasSizeGreaterThan(30);

        for (String reservedPackage : reservedPackages) {
            for (String packageName : List.of(reservedPackage, reservedPackage + ".sorting")) {
                assertThatExceptionOfType(BadRequestAlertException.class).as("the package name '%s' must be rejected, as %s rejects it", packageName, template)
                        .isThrownBy(() -> validationService.validatePackageName(exerciseWithPackageName(ProgrammingLanguage.JAVA, packageName))).withMessageContaining("invalid");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "org", "java", "de" })
    void validatePackageName_acceptsAReservedNameForTheBlackBoxProjectType(String packageName) {
        // The tests of a black-box exercise are not run with Ares, and the package of its template is a folder name.
        ProgrammingExercise exercise = exerciseWithPackageName(ProgrammingLanguage.JAVA, packageName);
        exercise.setProjectType(ProjectType.MAVEN_BLACKBOX);

        assertThatCode(() -> validationService.validatePackageName(exercise)).doesNotThrowAnyException();
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

    @ParameterizedTest
    @ValueSource(strings = { "when", "de.tum.when", "de.fun.sorting", "de.object", "val" })
    void validatePackageName_rejectsKotlinKeywordsOnlyForKotlin(String packageName) {
        // Java allows these words, but the package declaration of the Kotlin templates would not compile with them.
        assertThatExceptionOfType(BadRequestAlertException.class).as("the Kotlin package name '%s' must be rejected", packageName)
                .isThrownBy(() -> validationService.validatePackageName(exerciseWithPackageName(ProgrammingLanguage.KOTLIN, packageName))).withMessageContaining("invalid");
        assertThatCode(() -> validationService.validatePackageName(exerciseWithPackageName(ProgrammingLanguage.JAVA, packageName))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = { "de.whenever", "de.tum.funky", "de.values" })
    void validatePackageName_acceptsKotlinPackagesThatOnlyContainAKeyword(String packageName) {
        assertThatCode(() -> validationService.validatePackageName(exerciseWithPackageName(ProgrammingLanguage.KOTLIN, packageName))).doesNotThrowAnyException();
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
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateDockerFlags(buildConfigWithDockerFlags(flags(1, 2, 0))))
                .withMessageContaining("memory limit is invalid");
    }

    @Test
    void validateDockerFlags_rejectsAContainerWithoutACpu() {
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateDockerFlags(buildConfigWithDockerFlags(flags(0, 1024, 0))))
                .withMessageContaining("cpu count is invalid");
    }

    @Test
    void validateDockerFlags_rejectsANegativeSwapLimit() {
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateDockerFlags(buildConfigWithDockerFlags(flags(1, 1024, -1))))
                .withMessageContaining("memory swap limit is invalid");
    }

    @Test
    void validateDockerFlags_rejectsAnEnvironmentVariableTooLongToPass() {
        DockerFlagsDTO withLongValue = new DockerFlagsDTO(null, Map.of("KEY", "v".repeat(1001)), 1, 1024, 0);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateDockerFlags(buildConfigWithDockerFlags(withLongValue)))
                .withMessageContaining("environment variables are too long");
    }

    @Test
    void validateDockerFlags_acceptsAUsableConfiguration() {
        assertThatCode(() -> validationService.validateDockerFlags(buildConfigWithDockerFlags(new DockerFlagsDTO(null, Map.of("KEY", "value"), 2, 2048, 0))))
                .doesNotThrowAnyException();
    }

    @Test
    void validateDockerFlags_withoutAnyFlags_acceptsTheExercise() {
        // Most exercises configure no flags at all and then run with the instance defaults.
        assertThatCode(() -> validationService.validateDockerFlags(buildConfigWithDockerFlags(null))).doesNotThrowAnyException();
    }

    @Test
    void validateDockerFlags_whenTheFlagsCannotBeParsed_saysSoRatherThanFailingLater() {
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        when(programmingExerciseBuildConfigService.parseDockerFlags(any())).thenThrow(new IllegalArgumentException("not json"));

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateDockerFlags(buildConfig))
                .withMessageContaining("parsing the docker flags");
    }

    @Test
    void validateBuildConfigSize_rejectsABuildConfigurationTooLargeForTheColumn() {
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setBuildPlanConfiguration("x".repeat(1024 * 1024 + 1));

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateBuildConfigSize(buildConfig))
                .withMessageContaining("build plan configuration is too long");
    }

    @Test
    void validateBuildConfigSize_rejectsDockerFlagsTooLargeForTheColumn() {
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setDockerFlags("x".repeat(8 * 1024 + 1));

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateBuildConfigSize(buildConfig))
                .withMessageContaining("docker flags are too long");
    }

    @Test
    void validateBuildConfigSize_rejectsABranchRegexThatIsNotValid() {
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setAllowBranching(true);
        buildConfig.setBranchRegex("feature/[");

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateBuildConfigSize(buildConfig))
                .withMessageContaining("branch regex is not a valid");
    }

    @Test
    void validateBuildConfigSize_ignoresABranchRegexWhileBranchingIsNotAllowed() {
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setAllowBranching(false);
        buildConfig.setBranchRegex("feature/[");

        assertThatCode(() -> validationService.validateBuildConfigSize(buildConfig)).doesNotThrowAnyException();
    }

    @Test
    void validateBuildConfigSize_rejectsABranchRegexTooLargeForTheColumnEvenWithoutBranching() {
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setAllowBranching(false);
        buildConfig.setBranchRegex("a".repeat(129));

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateBuildConfigSize(buildConfig))
                .withMessageContaining("branch regex is too long");
    }

    @Test
    void validateBuildConfigSize_rejectsABranchRegexTooLargeForTheColumn() {
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setAllowBranching(true);
        buildConfig.setBranchRegex("a".repeat(129));

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateBuildConfigSize(buildConfig))
                .withMessageContaining("branch regex is too long");
    }

    @Test
    void validateBuildConfigSize_acceptsAConfigurationWithinTheLimits() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setBuildPlanConfiguration("{}");
        buildConfig.setDockerFlags("{}");

        assertThatCode(() -> validationService.validateBuildConfigSize(buildConfig)).doesNotThrowAnyException();
        assertThatCode(() -> validationService.validateBuildConfigSize(null)).as("a request without a build config has nothing to check").doesNotThrowAnyException();
    }

    @Test
    void validateCheckoutDirectoriesUnchanged_rejectsAChangeAfterTheExerciseExists() {
        // The checkout paths are baked into the build script and into every repository already checked out, so they cannot move once students have started.
        ProgrammingExerciseBuildConfig original = buildConfigWithCheckoutPaths("assignment", "solution", "tests");
        ProgrammingExerciseBuildConfig updated = buildConfigWithCheckoutPaths("somewhere-else", "solution", "tests");

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateCheckoutDirectoriesUnchanged(original, updated))
                .withMessageContaining("cannot be changed");
    }

    @Test
    void validateCheckoutDirectoriesUnchanged_acceptsAnUpdateThatLeavesThemAlone() {
        ProgrammingExerciseBuildConfig original = buildConfigWithCheckoutPaths("assignment", "solution", "tests");
        ProgrammingExerciseBuildConfig updated = buildConfigWithCheckoutPaths("assignment", "solution", "tests");

        assertThatCode(() -> validationService.validateCheckoutDirectoriesUnchanged(original, updated)).doesNotThrowAnyException();
        assertThat(updated.getAssignmentCheckoutPath()).isEqualTo("assignment");
    }

    private static ProgrammingExercise exerciseWithDates(ZonedDateTime release, ZonedDateTime start, ZonedDateTime due) {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setReleaseDate(release);
        exercise.setStartDate(start);
        exercise.setDueDate(due);
        return exercise;
    }

    private static ProgrammingExerciseBuildConfig buildConfigWithWindow(ZonedDateTime start, ZonedDateTime end) {
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setBuildTimeWindow(new BuildTimeWindow(start, end));
        return buildConfig;
    }

    @Test
    void validateBuildTimeWindow_acceptsAnExerciseWithoutAWindow() {
        ProgrammingExercise exercise = exerciseWithDates(NOW.plusDays(1), null, NOW.plusDays(10));

        assertThatCode(() -> validationService.validateBuildTimeWindow(exercise, new ProgrammingExerciseBuildConfig())).doesNotThrowAnyException();
    }

    @Test
    void validateBuildTimeWindow_acceptsAWindowInsideTheStartAndDueDate() {
        ProgrammingExercise exercise = exerciseWithDates(NOW.plusDays(1), NOW.plusDays(3), NOW.plusDays(10));

        assertThatCode(() -> validationService.validateBuildTimeWindow(exercise, buildConfigWithWindow(NOW.plusDays(5), NOW.plusDays(8)))).doesNotThrowAnyException();
        // the bounds themselves are inside
        assertThatCode(() -> validationService.validateBuildTimeWindow(exercise, buildConfigWithWindow(NOW.plusDays(3), NOW.plusDays(10)))).doesNotThrowAnyException();
    }

    @Test
    void validateBuildTimeWindow_acceptsAnOpenEndedWindow() {
        ProgrammingExercise exercise = exerciseWithDates(NOW.plusDays(1), NOW.plusDays(3), NOW.plusDays(10));

        // builds begin a week after the release, which is the use case of keeping the CI off while students prepare
        assertThatCode(() -> validationService.validateBuildTimeWindow(exercise, buildConfigWithWindow(NOW.plusDays(5), null))).doesNotThrowAnyException();
        assertThatCode(() -> validationService.validateBuildTimeWindow(exercise, buildConfigWithWindow(null, NOW.plusDays(5)))).doesNotThrowAnyException();
    }

    @Test
    void validateBuildTimeWindow_usesTheReleaseDateWhenThereIsNoStartDate() {
        ProgrammingExercise exercise = exerciseWithDates(NOW.plusDays(2), null, NOW.plusDays(10));

        assertThatCode(() -> validationService.validateBuildTimeWindow(exercise, buildConfigWithWindow(NOW.plusDays(2), NOW.plusDays(4)))).doesNotThrowAnyException();
        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> validationService.validateBuildTimeWindow(exercise, buildConfigWithWindow(NOW.plusDays(1), NOW.plusDays(4))))
                .withMessageContaining("must lie between");
    }

    @Test
    void validateBuildTimeWindow_rejectsAWindowThatEndsBeforeItStartsOrIsEmpty() {
        ProgrammingExercise exercise = exerciseWithDates(null, null, null);

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> validationService.validateBuildTimeWindow(exercise, buildConfigWithWindow(NOW.plusDays(5), NOW.plusDays(4)))).withMessageContaining("end after");
        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> validationService.validateBuildTimeWindow(exercise, buildConfigWithWindow(NOW.plusDays(5), NOW.plusDays(5)))).withMessageContaining("end after");
    }

    @Test
    void validateBuildTimeWindow_rejectsAWindowOutsideTheStartAndDueDate() {
        ProgrammingExercise exercise = exerciseWithDates(NOW.plusDays(1), NOW.plusDays(3), NOW.plusDays(10));

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> validationService.validateBuildTimeWindow(exercise, buildConfigWithWindow(NOW.plusDays(2), NOW.plusDays(5))))
                .withMessageContaining("must lie between");
        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> validationService.validateBuildTimeWindow(exercise, buildConfigWithWindow(NOW.plusDays(5), NOW.plusDays(11))))
                .withMessageContaining("must lie between");
        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> validationService.validateBuildTimeWindow(exercise, buildConfigWithWindow(NOW.plusDays(11), null))).withMessageContaining("must lie between");
        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> validationService.validateBuildTimeWindow(exercise, buildConfigWithWindow(null, NOW.plusDays(2)))).withMessageContaining("must lie between");
    }

    @Test
    void validateBuildTimeWindow_isNotAvailableForExamExercises() {
        ProgrammingExercise exercise = exerciseWithDates(null, null, null);
        exercise.setExerciseGroup(new ExerciseGroup());

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateBuildTimeWindow(exercise, buildConfigWithWindow(NOW, NOW.plusDays(1))))
                .withMessageContaining("exam exercises");
        assertThatCode(() -> validationService.validateBuildTimeWindow(exercise, new ProgrammingExerciseBuildConfig())).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = { "ghcr.io/ls1intum/image:1", "ghcr.io:443/ls1intum/image", "GHCR.IO/ls1intum/image" })
    void validateDockerImageRegistry_acceptsAnImageOfAnAllowedRegistry(String dockerImage) {
        validationService = serviceWithAllowedRegistries("ghcr.io", "ghcr.io:443");

        assertThatCode(() -> validationService.validateDockerImageRegistry(dockerImage)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = { "registry.example.org/team/image:1", "ubuntu:24.04", "library/ubuntu", "docker.io/library/ubuntu", "localhost:5000/image", "Other/team/image",
            "ghcr.io:443/ls1intum/image" })
    void validateDockerImageRegistry_rejectsAnImageOfAnotherRegistry(String dockerImage) {
        validationService = serviceWithAllowedRegistries("ghcr.io");

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateDockerImageRegistry(dockerImage)).withMessageContaining("not allowed")
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("dockerImageRegistryNotAllowed"));
    }

    @Test
    void validateDockerImageRegistry_readsAYamlList() {
        // the property sources of a YAML list hold name[0], name[1], ...
        validationService = serviceWithAllowedRegistries("ghcr.io", "registry.example.org");

        assertThatCode(() -> validationService.validateDockerImageRegistry("registry.example.org/team/image")).doesNotThrowAnyException();
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateDockerImageRegistry("quay.io/team/image"));
    }

    @Test
    void validateDockerImageRegistry_readsACommaSeparatedString() {
        validationService = serviceWithAllowedRegistries(
                new MockEnvironment().withProperty("artemis.continuous-integration.allowed-docker-registries", "ghcr.io, registry.example.org"));

        assertThatCode(() -> validationService.validateDockerImageRegistry("registry.example.org/team/image")).doesNotThrowAnyException();
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateDockerImageRegistry("quay.io/team/image"));
    }

    @Test
    void validateDockerImageRegistry_treatsAFirstComponentWithUppercaseLettersAsAHost() {
        // Docker only reads the first component as a repository path if it is lower case, so Myregistry is a host here
        validationService = serviceWithAllowedRegistries("myregistry");

        assertThatCode(() -> validationService.validateDockerImageRegistry("Myregistry/team/img")).doesNotThrowAnyException();
        validationService = serviceWithAllowedRegistries("docker.io");
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateDockerImageRegistry("Myregistry/team/img"));
        assertThatCode(() -> validationService.validateDockerImageRegistry("myregistry/team/img")).doesNotThrowAnyException();
    }

    @Test
    void validateDockerImageRegistry_normalizesTheEntriesOfTheList() {
        validationService = serviceWithAllowedRegistries("HTTPS://Registry.Example.org/", "http://other.example.org//", "registry-1.docker.io");

        assertThatCode(() -> validationService.validateDockerImageRegistry("registry.example.org/team/image")).doesNotThrowAnyException();
        assertThatCode(() -> validationService.validateDockerImageRegistry("other.example.org/team/image")).doesNotThrowAnyException();
        assertThatCode(() -> validationService.validateDockerImageRegistry("ubuntu:24.04")).doesNotThrowAnyException();
    }

    @Test
    void validateDockerImageRegistry_treatsAnImageWithoutRegistryAsDockerHub() {
        validationService = serviceWithAllowedRegistries("docker.io");

        assertThatCode(() -> validationService.validateDockerImageRegistry("ubuntu:24.04")).doesNotThrowAnyException();
        assertThatCode(() -> validationService.validateDockerImageRegistry("index.docker.io/library/ubuntu")).doesNotThrowAnyException();
        assertThatCode(() -> validationService.validateDockerImageRegistry("registry-1.docker.io/library/ubuntu")).doesNotThrowAnyException();
    }

    @Test
    void validateDockerImageRegistry_allowsEveryRegistryWhileTheListIsEmpty() {
        assertThatCode(() -> validationService.validateDockerImageRegistry("registry.example.org/team/image:1")).doesNotThrowAnyException();
    }

    @Test
    void validateDockerImageRegistry_checksTheImageOfTheBuildPlanConfiguration() {
        validationService = serviceWithAllowedRegistries("ghcr.io");
        ProgrammingExerciseBuildConfig allowed = new ProgrammingExerciseBuildConfig();
        allowed.setBuildPlanConfiguration("{\"phases\":[],\"dockerImage\":\"ghcr.io/ls1intum/image:1\"}");
        ProgrammingExerciseBuildConfig refused = new ProgrammingExerciseBuildConfig();
        refused.setBuildPlanConfiguration("{\"phases\":[],\"dockerImage\":\"registry.example.org/team/image:1\"}");

        assertThatCode(() -> validationService.validateDockerImageRegistry(allowed)).doesNotThrowAnyException();
        assertThatCode(() -> validationService.validateDockerImageRegistry(new ProgrammingExerciseBuildConfig())).doesNotThrowAnyException();
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateDockerImageRegistry(refused));
    }

    @Test
    void validateDockerImageRegistry_ignoresABuildPlanConfigurationThatIsTheJsonTextNull() {
        validationService = serviceWithAllowedRegistries("ghcr.io");
        ProgrammingExerciseBuildConfig nothingToRead = new ProgrammingExerciseBuildConfig();
        nothingToRead.setBuildPlanConfiguration("null");

        assertThatCode(() -> validationService.validateDockerImageRegistry(nothingToRead)).doesNotThrowAnyException();
    }

    @Test
    void validateBuildPhaseNames_rejectsABuildPlanConfigurationThatIsTheJsonTextNull() {
        when(profileService.isLocalCIActive()).thenReturn(true);
        ProgrammingExerciseBuildConfig nothingToRead = new ProgrammingExerciseBuildConfig();
        nothingToRead.setBuildPlanConfiguration("null");

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateBuildPhaseNames(nothingToRead))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("invalidBuildPlanConfiguration"));
    }

    @Test
    void validateBuildConfigSize_rejectsABranchRegexThatCannotBeCompiled() {
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setAllowBranching(true);
        buildConfig.setBranchRegex("feature/[");

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateBuildConfigSize(buildConfig))
                .satisfies(e -> assertThat(e.getErrorKey()).isEqualTo("branchRegexInvalid"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "(?:^){2147483647}", "(?x)(?:^){2147483647 }", "(a+)+", "(a|aa)+", "(?=a)b", "(a)\\1", "a*?", "\\p{Alpha}", "a{1001}", "[a&&b]", "(^|^)", "(a?|b*)",
            "(x{0}|y{0})" })
    void validateBuildConfigSize_rejectsABranchRegexWithSyntaxThatIsNotSupported(String regex) {
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setAllowBranching(true);
        buildConfig.setBranchRegex(regex);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateBuildConfigSize(buildConfig))
                .satisfies(e -> assertThat(e.getErrorKey()).isEqualTo("branchRegexNotSupported"));
    }

    @ParameterizedTest
    @ValueSource(strings = { ".*", "feature/.*", "^(feature|bugfix)/[a-z0-9-]+$", "[a-z]{3,10}-\\d{2}" })
    void validateBuildConfigSize_acceptsTypicalBranchRegexes(String regex) {
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setAllowBranching(true);
        buildConfig.setBranchRegex(regex);

        assertThatCode(() -> validationService.validateBuildConfigSize(buildConfig)).doesNotThrowAnyException();
    }

    @Test
    void validateBuildConfigSize_acceptsAnUnsupportedBranchRegexWhileBranchingIsDisabled() {
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setAllowBranching(false);
        buildConfig.setBranchRegex("(a+)+");

        assertThatCode(() -> validationService.validateBuildConfigSize(buildConfig)).doesNotThrowAnyException();
    }

    @Test
    void validateBuildConfigSize_rejectsABranchRegexThatIsTooLong() {
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setAllowBranching(true);
        buildConfig.setBranchRegex("a".repeat(129));

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> validationService.validateBuildConfigSize(buildConfig))
                .satisfies(e -> assertThat(e.getErrorKey()).isEqualTo("branchRegexTooLong"));
    }

    @Test
    void validateBuildConfigSize_acceptsAUsableBranchRegex() {
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setAllowBranching(true);
        buildConfig.setBranchRegex("feature/.*");

        assertThatCode(() -> validationService.validateBuildConfigSize(buildConfig)).doesNotThrowAnyException();
    }

    private static DockerFlagsDTO flags(int cpuCount, int memory, int memorySwap) {
        return new DockerFlagsDTO(null, Map.of(), cpuCount, memory, memorySwap);
    }

    private static ProgrammingExerciseBuildConfig buildConfigWithCheckoutPaths(String assignment, String solution, String tests) {
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setAssignmentCheckoutPath(assignment);
        buildConfig.setSolutionCheckoutPath(solution);
        buildConfig.setTestCheckoutPath(tests);
        return buildConfig;
    }
}
