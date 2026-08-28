package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.artemis.assessment.domain.CategoryState;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResultDTO;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.FakeInteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.LanguageGenerationProfile;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.GenerationWorkspaceService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.SandboxBuildCommandService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.WorkspaceArchive;
import de.tum.cit.aet.artemis.localci.service.BuildPhasesTemplateService;
import de.tum.cit.aet.artemis.localci.service.BuildScriptProviderService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisCategory;
import de.tum.cit.aet.artemis.programming.repository.StaticCodeAnalysisCategoryRepository;

/**
 * Deterministic unit test for the differential oracle: a fake sandbox serves the {@code copyOut} of the verifier-owned reports dir as a tar of real JUnit (and, where exercised,
 * SCA) reports for the solution and template builds, parsed by the production parsers ({@code TestResultXmlParser}, {@code ReportParser}) exactly as against a live container. Live
 * build behaviour is covered by the gated end-to-end test.
 */
class DifferentialVerificationServiceTest {

    /** Production always restores the captured candidate before each pristine build; tests that do not exercise that hook pass this no-op. */
    private static final Runnable NO_RESTORE = () -> {
    };

    private static SandboxBuildCommandService sandboxBuildCommandService() {
        BuildPhasesTemplateService phases = mock(BuildPhasesTemplateService.class);
        when(phases.getDefaultBuildPlanPhasesFor(any())).thenReturn(List.of());
        return new SandboxBuildCommandService(Optional.of(phases), Optional.of(new BuildScriptProviderService()));
    }

    private static DifferentialVerificationService newVerifier() {
        return new DifferentialVerificationService(sandboxBuildCommandService());
    }

    private static DifferentialVerificationService newVerifier(String approvedSpecification) {
        ApprovedSpecRegistry approvedSpecs = new ApprovedSpecRegistry();
        approvedSpecs.approve("s", approvedSpecification);
        return new DifferentialVerificationService(sandboxBuildCommandService(), Optional.empty(), approvedSpecs);
    }

    /**
     * One build's report fixture: the JUnit/SCA reports the verifier {@code copyOut}s plus the build's exit code and timeout flag.
     *
     * @param allNames         every test name the JUnit report carries (one {@code <testcase>} each)
     * @param failedNames      the subset that carry a {@code <failure>}
     * @param scaReports       SCA reports keyed by their canonical per-tool file name (e.g. {@code spotbugsXml.xml}); empty for the common non-SCA case
     * @param exitCode         the build's exit code; the solution gate requires 0
     * @param timedOut         whether the build was killed for exceeding its timeout
     * @param explicitJunitXml verbatim JUnit XML to serve instead of one synthesized from {@code allNames}/{@code failedNames} (for the skipped/multi-suite shapes); {@code null}
     *                             otherwise
     */
    private record BuildReportSpec(List<String> allNames, List<String> failedNames, Map<String, String> scaReports, int exitCode, boolean timedOut, String explicitJunitXml) {

        static BuildReportSpec of(List<String> allNames, List<String> failedNames, int exitCode) {
            return new BuildReportSpec(allNames, failedNames, Map.of(), exitCode, false, null);
        }

        static BuildReportSpec withScaReports(List<String> allNames, List<String> failedNames, Map<String, String> scaReports, int exitCode) {
            return new BuildReportSpec(allNames, failedNames, scaReports, exitCode, false, null);
        }

        /** A spec whose JUnit report is the given verbatim XML (for the skipped/multi-suite shapes the production parser must interpret); names/fails are unused. */
        static BuildReportSpec withJunitXml(String junitXml, int exitCode) {
            return new BuildReportSpec(List.of(), List.of(), Map.of(), exitCode, false, junitXml);
        }

        static BuildReportSpec timedOutBuild() {
            return new BuildReportSpec(List.of(), List.of(), Map.of(), 124, true, null);
        }

        TarArchiveInputStream reportsTar(String assignment) {
            if (explicitJunitXml != null) {
                return ReportTarFixtures.tar(assignment, Map.of("0001" + SandboxBuildCommandService.COLLECTED_NAME_SEPARATOR + SandboxBuildCommandService.COLLECTED_JUNIT_TOKEN,
                        explicitJunitXml.getBytes(StandardCharsets.UTF_8)));
            }
            return ReportTarFixtures.junitAndScaReports(assignment, allNames, failedNames, scaReports);
        }
    }

    /** The two graded test names the default {@link #PROBLEM_STATEMENT_WITH_TASK} binds; a build's reports must include them so the [task] bindings resolve. */
    private static final String[] DEFAULT_BOUND_NAMES = { "sortsUnsortedArray", "sortsArrayWithDuplicates" };

    /** A spec with {@code tests} testcases (default-bound names first, then fillers); when failures/errors > 0 EVERY test is marked failed (canonical failing template). */
    private static BuildReportSpec result(int tests, int failures, int errors, int exit) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < tests; i++) {
            names.add(i < DEFAULT_BOUND_NAMES.length ? DEFAULT_BOUND_NAMES[i] : "BuildHyperion" + i);
        }
        List<String> failed = (failures + errors) > 0 ? names : List.of();
        return BuildReportSpec.of(names, failed, exit);
    }

    private static BuildReportSpec resultWithFails(int exit, List<String> allNames, List<String> failedNames) {
        return BuildReportSpec.of(allNames, failedNames, exit);
    }

    private static final String PROBLEM_STATEMENT_WITH_TASK = "# Sort\n[task][Sort an array](sortsUnsortedArray,sortsArrayWithDuplicates)\nImplement sorting for unsorted arrays and duplicates.\n";

    /** A grading plan mapping EVERY {@link #DEFAULT_BOUND_NAMES} test, so the approved-test-plan gate stays silent and cannot mask the gate a test is actually about. */
    private static final String FULL_PLAN_FOR_DEFAULT_BOUND_NAMES = "{\"tests\":[{\"name\":\"sortsUnsortedArray\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"},"
            + "{\"name\":\"sortsArrayWithDuplicates\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"}]}";

    private static String aresPom() {
        return """
                <project>
                    <dependencies>
                        <dependency>
                            <groupId>de.tum.in.ase</groupId>
                            <artifactId>artemis-java-test-sandbox</artifactId>
                        </dependency>
                    </dependencies>
                    <build><plugins><plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-enforcer-plugin</artifactId>
                        <configuration><rules><requireFilesDontExist><files>
                            <file>${project.build.outputDirectory}/de/tum/in/test/api/</file>
                            <file>${project.build.outputDirectory}/org/junit/</file>
                        </files></requireFilesDontExist></rules></configuration>
                    </plugin></plugins></build>
                </project>
                """;
    }

    /** Serves the solution/template report tars on {@code copyOut} (routed by the reports-dir path) and the build exit code/timeout on {@code exec}. */
    private static final class ScriptedSandbox extends FakeInteractiveSandbox {

        private final BuildReportSpec solution;

        private final BuildReportSpec template;

        private BuildReportSpec structuralSolution;

        private BuildReportSpec structuralTemplate;

        private BuildReportSpec lastSolution;

        private BuildReportSpec lastTemplate;

        private final String problemStatement;

        private final String buildOutput;

        /** Served for {@code cat .../test-plan.json}; {@code null} means "no plan", the fail-open shape most tests in this file run with. */
        private String testPlanJson;

        private String specDocument;

        private Map<String, String> solutionRepositoryFiles = Map.of();

        private Map<String, String> templateRepositoryFiles = Map.of();

        private Map<String, String> testsRepositoryFiles = Map.of();

        private final List<Map<String, String>> restoredWorkspaceSources = new ArrayList<>();

        /** Served instead of the solution build's reports, for the hardened-reader rejection paths. */
        private TarArchiveInputStream tamperedSolutionReports;

        private ScriptedSandbox(BuildReportSpec solution, BuildReportSpec template, String problemStatement) {
            this(solution, template, problemStatement, "build ran");
        }

        private ScriptedSandbox withTamperedSolutionReports(TarArchiveInputStream reports) {
            this.tamperedSolutionReports = reports;
            return this;
        }

        private ScriptedSandbox withTestPlan(String plan) {
            this.testPlanJson = plan;
            return this;
        }

        private ScriptedSandbox withSpec(String spec) {
            this.specDocument = spec;
            return this;
        }

        private ScriptedSandbox withStructuralReports(BuildReportSpec solutionReports, BuildReportSpec templateReports) {
            structuralSolution = solutionReports;
            structuralTemplate = templateReports;
            return this;
        }

        private ScriptedSandbox withRepositories(Map<String, String> solutionFiles, Map<String, String> templateFiles, Map<String, String> testsFiles) {
            this.solutionRepositoryFiles = solutionFiles;
            this.templateRepositoryFiles = templateFiles;
            this.testsRepositoryFiles = testsFiles;
            return this;
        }

        private ScriptedSandbox(BuildReportSpec solution, BuildReportSpec template, String problemStatement, String buildOutput) {
            this.solution = solution;
            this.template = template;
            this.lastSolution = solution;
            this.lastTemplate = template;
            this.problemStatement = problemStatement;
            this.buildOutput = buildOutput;
        }

        @Override
        protected SandboxExecResultDTO respond(String[] command) {
            String joined = String.join(" ", command);
            if ("cat".equals(command[0])) {
                if (command.length > 1 && command[1].endsWith("test-plan.json")) {
                    return testPlanJson == null ? new SandboxExecResultDTO(1, "", "no such file", false) : new SandboxExecResultDTO(0, testPlanJson, "", false);
                }
                if (command.length > 1 && command[1].endsWith("SPEC.md")) {
                    return specDocument == null ? new SandboxExecResultDTO(1, "", "no such file", false) : new SandboxExecResultDTO(0, specDocument, "", false);
                }
                return new SandboxExecResultDTO(0, problemStatement, "", false);
            }
            if (!joined.contains("verify.sh")) {
                return new SandboxExecResultDTO(0, "", "", false);
            }
            boolean solutionBuild = joined.contains("solution");
            boolean structural = joined.contains("trusted-structural");
            BuildReportSpec spec = solutionBuild ? structural && structuralSolution != null ? structuralSolution : solution
                    : structural && structuralTemplate != null ? structuralTemplate : template;
            if (solutionBuild) {
                lastSolution = spec;
            }
            else {
                lastTemplate = spec;
            }
            return new SandboxExecResultDTO(spec.exitCode(), buildOutput, "", spec.timedOut());
        }

        @Override
        public TarArchiveInputStream copyOut(String sessionId, String path) {
            // A /workspace path is a REPOSITORY read-back, never a reports read: an unset repository must read back empty, not silently serve the build's JUnit reports.
            if (path.startsWith(GenerationWorkspaceService.WORKSPACE + "/")) {
                return switch (path.substring(GenerationWorkspaceService.WORKSPACE.length() + 1)) {
                    case "solution" -> repositoryTar("solution", solutionRepositoryFiles);
                    case "template" -> repositoryTar("template", templateRepositoryFiles);
                    case "tests" -> repositoryTar("tests", testsRepositoryFiles);
                    default -> null;
                };
            }
            if (path.endsWith("/solution")) {
                return tamperedSolutionReports != null ? tamperedSolutionReports : lastSolution.reportsTar("solution");
            }
            if (path.endsWith("/template")) {
                return lastTemplate.reportsTar("template");
            }
            return null;
        }

        @Override
        public void copyIn(String sessionId, String destinationPath, InputStream tarArchive) {
            if (!GenerationWorkspaceService.WORKSPACE.equals(destinationPath)) {
                return;
            }
            try (TarArchiveInputStream tar = new TarArchiveInputStream(tarArchive)) {
                restoredWorkspaceSources.add(WorkspaceArchive.readTar(tar, ""));
            }
            catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        private static TarArchiveInputStream repositoryTar(String directory, Map<String, String> files) {
            return files == null ? null
                    : ReportTarFixtures.tar(directory,
                            files.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getBytes(StandardCharsets.UTF_8))));
        }
    }

    private static VerificationResult verify(BuildReportSpec solution, BuildReportSpec template) {
        return verify(solution, template, PROBLEM_STATEMENT_WITH_TASK);
    }

    private static VerificationResult verify(BuildReportSpec solution, BuildReportSpec template, String problemStatement) {
        return verifyGenerate(newVerifier(), new ScriptedSandbox(solution, template, problemStatement), new ProgrammingExercise());
    }

    @Test
    void authoritativeVerificationRestoresTheCapturedCandidateBeforeAndAfterSourceIsolatedBuilds() {
        List<String> names = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
        ScriptedSandbox sandbox = new ScriptedSandbox(resultWithFails(0, names, List.of()), resultWithFails(1, names, names), PROBLEM_STATEMENT_WITH_TASK);
        AtomicInteger restorations = new AtomicInteger();

        VerificationResult result = newVerifier().verify(sandbox, "s", new ProgrammingExercise(),
                new VerificationRequest(Map.of(), Map.of(), Map.of(), Map.of(), Set.of(), SeededStructuralTests.EMPTY, Set.of()), restorations::incrementAndGet);

        assertThat(result.mechanicallyVerified()).isTrue();
        assertThat(restorations).hasValue(3);
    }

    @Test
    void inLoopSelfCheckRestoresExactJavaSourcesFromServerMemoryAfterEveryIsolatedBuild() {
        List<String> names = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
        ScriptedSandbox sandbox = new ScriptedSandbox(resultWithFails(0, names, List.of()), resultWithFails(1, names, names), PROBLEM_STATEMENT_WITH_TASK).withRepositories(
                Map.of("src/Answer.java", "class Answer {}", "README.md", "solution"), Map.of("src/Answer.java", "class Answer { /* TODO */ }"),
                Map.of("test/AnswerTest.java", "class AnswerTest {}", "pom.xml", "<project/>"));
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);

        newVerifier().selfCheck(sandbox, "s", exercise, Map.of(), false, SeededStructuralTests.EMPTY);

        assertThat(sandbox.restoredWorkspaceSources).hasSize(3)
                .allSatisfy(restored -> assertThat(restored).containsEntry("solution/src/Answer.java", "class Answer {}")
                        .containsEntry("template/src/Answer.java", "class Answer { /* TODO */ }").containsEntry("tests/test/AnswerTest.java", "class AnswerTest {}")
                        .doesNotContainKeys("solution/README.md", "tests/pom.xml"));
    }

    @Test
    void authoritativeVerificationCarriesEnvironmentReportedTemplateFailuresIntoReviewContext() {
        BuildReportSpec solution = BuildReportSpec.withJunitXml("""
                <testsuite name="GeneratedSuite">
                  <testcase name="classifiesBoundary"/>
                </testsuite>
                """, 0);
        BuildReportSpec template = BuildReportSpec.withJunitXml("""
                <testsuite name="GeneratedSuite">
                  <testcase name="classifiesBoundary"><error message="TODO"/></testcase>
                </testsuite>
                """, 1);

        VerificationResult result = verify(solution, template);

        assertThat(result.mechanicallyVerified()).isFalse();
        assertThat(result.templateFailureEvidence()).containsExactly(new AgentVerifyReport.TestFailureEvidence("classifiesBoundary", "TODO"));
    }

    @Test
    void buildEnvironmentPreflightLogsBoundedRedactedOutputForOperators() {
        Logger logger = (Logger) LoggerFactory.getLogger(DifferentialVerificationService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            String buildOutput = "Gradle could not resolve testRuntimeClasspath\nAuthorization: Bearer bearer-secret\n"
                    + "https://repo-user:repo-password@example.invalid/maven\npassword=plain-secret\n" + "diagnostic ".repeat(1_000);
            BuildReportSpec failedBuild = result(0, 0, 0, 1);

            Optional<String> failure = newVerifier().checkBuildEnvironment(new ScriptedSandbox(failedBuild, failedBuild, PROBLEM_STATEMENT_WITH_TASK, buildOutput), "session",
                    new ProgrammingExercise());

            assertThat(failure).hasValueSatisfying(message -> assertThat(message).doesNotContain("Gradle could not resolve", "bearer-secret", "repo-password", "plain-secret"));
            assertThat(appender.list).hasSize(1);
            String diagnostic = appender.list.getFirst().getFormattedMessage();
            assertThat(diagnostic).contains("Gradle could not resolve testRuntimeClasspath", "[REDACTED]", "[truncated]")
                    .doesNotContain("bearer-secret", "repo-user", "repo-password", "plain-secret").hasSizeLessThanOrEqualTo(4_500);
        }
        finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void productionProfileSupportsPlainAndWrapperBasedMavenAndGradle() {
        for (ProjectType projectType : List.of(ProjectType.PLAIN_MAVEN, ProjectType.MAVEN_MAVEN, ProjectType.PLAIN_GRADLE, ProjectType.GRADLE_GRADLE)) {
            ProgrammingExercise exercise = new ProgrammingExercise();
            exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
            exercise.setProjectType(projectType);

            assertThat(LanguageGenerationProfile.isSupported(exercise)).as("%s should use the Java generation image", projectType).isTrue();
            assertThat(LanguageGenerationProfile.guidanceFor(exercise)).contains("Maven or Gradle", "tests/build.gradle");
        }
    }

    @Test
    void buildEnvironmentPreflightConvertsBuildExecFailureIntoASafeError() {
        InteractiveSandbox sandbox = mock(InteractiveSandbox.class);
        when(sandbox.exec(anyString(), any(), any(String[].class))).thenReturn(new SandboxExecResultDTO(0, "", "", false))
                .thenThrow(new IllegalStateException("Bearer secret-value"));

        Optional<String> failure = newVerifier().checkBuildEnvironment(sandbox, "session", new ProgrammingExercise());

        assertThat(failure).hasValueSatisfying(message -> assertThat(message).contains("could not be prepared", "authoring agent was not started").doesNotContain("secret-value"));
    }

    @Test
    void authoritativeBuildTimeoutIsADeadSessionInsteadOfAModelRepairSignal() {
        assertThatThrownBy(() -> verifyGenerate(newVerifier(), new ScriptedSandbox(BuildReportSpec.timedOutBuild(), result(4, 4, 0, 1), PROBLEM_STATEMENT_WITH_TASK),
                new ProgrammingExercise())).isInstanceOfSatisfying(DifferentialVerificationService.VerificationInfrastructureException.class,
                        exception -> assertThat(exception.isRetryableInSameSession()).isFalse());
    }

    @Test
    void authoritativeVerificationRejectsAContractThatPassedTheSpecGateButWasLaterScaffoldedAway() {
        ApprovedSpecRegistry approvedSpecs = new ApprovedSpecRegistry();
        approvedSpecs.approve("s", """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `PlaybackStrategy` | abstraction students design | student-creates |
                """);
        DifferentialVerificationService verifier = new DifferentialVerificationService(sandboxBuildCommandService(), Optional.empty(), approvedSpecs);
        VerificationRequest request = new VerificationRequest(Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of("src/PlaybackStrategy.java", "public interface PlaybackStrategy { /* TODO */ }"),
                Map.of("src/PlaybackStrategy.java", "public interface PlaybackStrategy { int order(); }"), Set.of(), SeededStructuralTests.EMPTY, Set.of(),
                PROBLEM_STATEMENT_WITH_TASK, FULL_PLAN_FOR_DEFAULT_BOUND_NAMES, false);

        VerificationResult result = verifier.verify(new ScriptedSandbox(result(2, 0, 0, 0), result(2, 2, 0, 1), PROBLEM_STATEMENT_WITH_TASK), "s", new ProgrammingExercise(),
                request, NO_RESTORE);

        assertThat(result.mechanicallyVerified()).isFalse();
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("template already declares") && reason.contains("PlaybackStrategy"));
    }

    @Test
    void authoritativeVerificationIgnoresUnreviewedWorkAddedToTheLiveSpecAfterApproval() {
        ApprovedSpecRegistry approvedSpecs = new ApprovedSpecRegistry();
        approvedSpecs.approve("s", "## Design\n| Type | Role | Template status |\n|---|---|---|\n| `Track` | data | given |\n");
        String clarifiedSpec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `Track` | data | given |
                | `PlaybackStrategy` | abstraction students design | student-creates |
                ## Testing Strategy
                | Seam | Partitions | Weight | Hidden-variant (yes/no) |
                |---|---|---|---|
                | strategy | typical values | 3 | no |
                """;
        String plan = "{\"tests\":[{\"name\":\"sortsUnsortedArray\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"}]}";
        DifferentialVerificationService verifier = new DifferentialVerificationService(sandboxBuildCommandService(), Optional.empty(), approvedSpecs);
        VerificationRequest request = new VerificationRequest(Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of("src/PlaybackStrategy.java", "public interface PlaybackStrategy { /* TODO */ }"),
                Map.of("src/PlaybackStrategy.java", "public interface PlaybackStrategy { int order(); }"), Set.of(), SeededStructuralTests.EMPTY, Set.of(),
                PROBLEM_STATEMENT_WITH_TASK, plan, false);

        VerificationResult result = verifier.verify(new ScriptedSandbox(result(2, 0, 0, 0), result(2, 2, 0, 1), PROBLEM_STATEMENT_WITH_TASK).withSpec(clarifiedSpec), "s",
                new ProgrammingExercise(), request, NO_RESTORE);

        assertThat(result.reasons()).noneMatch(reason -> reason.contains("PlaybackStrategy"));
    }

    @Test
    void buildEnvironmentPreflightAcceptsOnlyAParsedPassingTestRun() {
        BuildReportSpec readinessPass = resultWithFails(0, List.of("testPublicApi", "testRepresentativeScores", "testBoundaryScores", "testEmptyInput"), List.of());
        assertThat(newVerifier().checkBuildEnvironment(new ScriptedSandbox(readinessPass, result(0, 0, 0, 1), PROBLEM_STATEMENT_WITH_TASK), "session", new ProgrammingExercise()))
                .isEmpty();

        assertThat(
                newVerifier().checkBuildEnvironment(new ScriptedSandbox(result(3, 0, 0, 0), result(0, 0, 0, 1), PROBLEM_STATEMENT_WITH_TASK), "session", new ProgrammingExercise()))
                .hasValueSatisfying(message -> assertThat(message).contains("readiness probe"));

        BuildReportSpec gradleReadinessPass = resultWithFails(0, List.of("testPublicApi()", "testRepresentativeScores()", "testBoundaryScores()", "testEmptyInput()"), List.of());
        assertThat(newVerifier().checkBuildEnvironment(new ScriptedSandbox(gradleReadinessPass, result(0, 0, 0, 1), PROBLEM_STATEMENT_WITH_TASK), "session",
                new ProgrammingExercise())).isEmpty();

        BuildReportSpec malformed = BuildReportSpec.withJunitXml("not xml", 0);
        assertThat(newVerifier().checkBuildEnvironment(new ScriptedSandbox(malformed, malformed, PROBLEM_STATEMENT_WITH_TASK), "session", new ProgrammingExercise()))
                .hasValueSatisfying(message -> assertThat(message).contains("could not be prepared", "authoring agent was not started"));
    }

    /** Invokes the full production verify(...) in GENERATE mode with empty integrity-gate inputs. */
    private static VerificationResult verifyGenerate(DifferentialVerificationService verifier, InteractiveSandbox sandbox, ProgrammingExercise exercise) {
        return verifier.verify(sandbox, "s", exercise, new VerificationRequest(Map.of(), Map.of(), Map.of(), Map.of(), Set.of(), SeededStructuralTests.EMPTY, Set.of()),
                NO_RESTORE);
    }

    /** Runs the in-loop self-check (the agent's {@code verify} tool) against the same scripted sandbox, so its report shares the differential with the post-loop {@code verify}. */
    private static AgentVerifyReport selfCheck(BuildReportSpec solution, BuildReportSpec template, String problemStatement) {
        return newVerifier().selfCheck(new ScriptedSandbox(solution, template, problemStatement), "s", new ProgrammingExercise(), Map.of(), false, SeededStructuralTests.EMPTY);
    }

    private static SeededStructuralTests structuralTests(Set<String> names) {
        return names.isEmpty() ? SeededStructuralTests.EMPTY
                : new SeededStructuralTests(names, Map.of("test/de/tum/cit/aet/artemis/TrustedStructuralTest.java", "// server-owned test fixture"));
    }

    private static BuildReportSpec onlyTests(BuildReportSpec reports, Set<String> selectedNames) {
        List<String> names = reports.allNames().stream().filter(selectedNames::contains).toList();
        List<String> failures = reports.failedNames().stream().filter(selectedNames::contains).toList();
        int exitCode = failures.isEmpty() ? 0 : reports.exitCode();
        return BuildReportSpec.withScaReports(names, failures, reports.scaReports(), exitCode);
    }

    /** Runs verify with the authoritative auto-seeded structural test names, so the structural-binding exemption is exercised with (and without) that set. */
    private static VerificationResult verifyWithSeededStructural(BuildReportSpec solution, BuildReportSpec template, String problemStatement, Set<String> seededStructural) {
        Set<String> behavioralNames = solution.allNames().stream().filter(name -> !seededStructural.contains(name)).collect(Collectors.toSet());
        ScriptedSandbox sandbox = new ScriptedSandbox(onlyTests(solution, behavioralNames), onlyTests(template, behavioralNames), problemStatement)
                .withStructuralReports(onlyTests(solution, seededStructural), onlyTests(template, seededStructural));
        return newVerifier().verify(sandbox, "s", new ProgrammingExercise(),
                new VerificationRequest(Map.of(), Map.of(), Map.of(), Map.of(), Set.of(), structuralTests(seededStructural), Set.of()), NO_RESTORE);
    }

    @Test
    void provenanceSeparatedVerificationRunsFourLanesAndMergesOnlyExactTrustedStructuralResults() {
        String structuralName = "testClass[Calculator]";
        List<String> behaviorNames = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
        ScriptedSandbox sandbox = new ScriptedSandbox(resultWithFails(0, behaviorNames, List.of()), resultWithFails(1, behaviorNames, behaviorNames),
                PROBLEM_STATEMENT_WITH_TASK + "\n[task][Create Calculator](" + structuralName + ")\nCreate the Calculator type.")
                .withStructuralReports(BuildReportSpec.of(List.of(structuralName), List.of(), 0), BuildReportSpec.of(List.of(structuralName), List.of(), 0));

        VerificationResult result = newVerifier().verify(sandbox, "s", new ProgrammingExercise(),
                new VerificationRequest(Map.of(), Map.of(), Map.of(), Map.of(), Set.of(), structuralTests(Set.of(structuralName)), Set.of()), NO_RESTORE);

        assertThat(result.mechanicallyVerified()).isTrue();
        assertThat(result.testCount()).isEqualTo(3);
        assertThat(sandbox.executedCommands()).filteredOn(command -> command.contains("verify.sh")).hasSize(4);
        assertThat(sandbox.executedCommands()).anyMatch(command -> command.contains("solution behavior-isolated"))
                .anyMatch(command -> command.contains("template behavior-isolated")).anyMatch(command -> command.contains("solution trusted-structural"))
                .anyMatch(command -> command.contains("template trusted-structural"));
    }

    @Test
    void provenanceSeparatedVerificationFailsClosedWhenTrustedLaneDoesNotReportTheExactSeededNames() {
        String structuralName = "testClass[Calculator]";
        List<String> behaviorNames = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
        ScriptedSandbox sandbox = new ScriptedSandbox(resultWithFails(0, behaviorNames, List.of()), resultWithFails(1, behaviorNames, behaviorNames),
                PROBLEM_STATEMENT_WITH_TASK + "\n[task][Create Calculator](" + structuralName + ")\nCreate the Calculator type.")
                .withStructuralReports(BuildReportSpec.of(List.of("candidateLookalike"), List.of(), 0), BuildReportSpec.of(List.of(structuralName), List.of(), 0));

        VerificationResult result = newVerifier().verify(sandbox, "s", new ProgrammingExercise(),
                new VerificationRequest(Map.of(), Map.of(), Map.of(), Map.of(), Set.of(), structuralTests(Set.of(structuralName)), Set.of()), NO_RESTORE);

        assertThat(result.mechanicallyVerified()).isFalse();
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("trusted structural solution lane") && reason.contains("exactly the server-seeded checks"));
    }

    @Test
    void shouldRunThePristineScriptAndReadTheVerifierOwnedReportsDir() {
        List<String> names = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
        ScriptedSandbox sandbox = new ScriptedSandbox(resultWithFails(0, names, List.of()), resultWithFails(1, names, names), PROBLEM_STATEMENT_WITH_TASK);
        VerificationResult result = verifyGenerate(newVerifier(), sandbox, new ProgrammingExercise());
        assertThat(result.mechanicallyVerified()).isTrue();
        assertThat(sandbox.executedCommands()).filteredOn(c -> c.equals("sh -c find /opt/hyperion -mindepth 1 -delete")).hasSize(2);
        assertThat(sandbox.executedCommands()).anyMatch(c -> c.contains(SandboxBuildCommandService.PRISTINE_VERIFY_PATH + " solution"));
        assertThat(sandbox.executedCommands()).noneMatch(c -> c.contains("/workspace/verify.sh"));
    }

    @Test
    void shouldEvaluateTheCapturedProblemStatementThatWillBePersisted() {
        List<String> names = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
        ScriptedSandbox sandbox = new ScriptedSandbox(resultWithFails(0, names, List.of()), resultWithFails(1, names, names), "No task bindings");

        VerificationResult result = newVerifier().verify(sandbox, "s", new ProgrammingExercise(),
                new VerificationRequest(Map.of(), Map.of(), Map.of(), Map.of(), Set.of(), SeededStructuralTests.EMPTY, Set.of(), PROBLEM_STATEMENT_WITH_TASK), NO_RESTORE);

        assertThat(result.mechanicallyVerified()).isTrue();
    }

    @Test
    void shouldRejectARepairAddedTestMissingFromTheCapturedPlanWithoutASpecification() {
        List<String> names = List.of("planned", "addedDuringRepair");
        String statement = "# Exercise\n[task][Implement both cases](planned,addedDuringRepair)\nImplement both cases.\n";
        String plan = "{\"tests\":[{\"name\":\"planned\",\"seam\":\"S1\",\"seamWeightTier\":1,\"visibility\":\"ALWAYS\"}]}";
        ScriptedSandbox sandbox = new ScriptedSandbox(resultWithFails(0, names, List.of()), resultWithFails(1, names, names), statement);
        VerificationRequest request = new VerificationRequest(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Set.of(), SeededStructuralTests.EMPTY, Set.of(),
                statement, plan, false);

        VerificationResult result = newVerifier().verify(sandbox, "s", new ProgrammingExercise(), request, NO_RESTORE);

        assertThat(result.mechanicallyVerified()).isFalse();
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("omits verified gradable test(s)") && reason.contains("addedDuringRepair"));
    }

    @Test
    void shouldAcceptWhenSolutionPassesAndTemplateFailsSameTests() {
        VerificationResult result = verify(result(2, 0, 0, 0), result(2, 2, 0, 1));
        assertThat(result.mechanicallyVerified()).isTrue();
        assertThat(result.solutionPassed()).isTrue();
        assertThat(result.templateFailed()).isTrue();
        assertThat(result.testCount()).isEqualTo(2);
    }

    @Test
    void shouldRejectDuplicateTestNamesBecauseProductionGradingZeroesThem() {
        List<String> names = List.of("sortsUnsortedArray", "sortsUnsortedArray", "sortsArrayWithDuplicates");

        VerificationResult result = verify(resultWithFails(0, names, List.of()), resultWithFails(1, names, names));

        assertThat(result.mechanicallyVerified()).isFalse();
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("Duplicate test names"));
    }

    @Test
    void shouldRejectWhenStudentProseLeaksGraderMechanics() {
        String leaky = PROBLEM_STATEMENT_WITH_TASK + "\nEach method should raise NotImplementedError in the template file to make the tests fail.";
        VerificationResult result = verify(result(5, 0, 0, 0), result(5, 3, 0, 1), leaky);
        assertThat(result.mechanicallyVerified()).isFalse();
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("leaks grader internals"));
    }

    @Test
    void shouldSurfaceProseLeakToTheInLoopSelfCheck() {
        String leaky = PROBLEM_STATEMENT_WITH_TASK + "\nRaise NotImplementedError to make the tests fail.";
        AgentVerifyReport report = selfCheck(result(5, 0, 0, 0), result(5, 3, 0, 1), leaky);
        assertThat(report.wouldBeAccepted()).isFalse();
        assertThat(report.toObservation()).contains("leaks grader internals");
    }

    @Test
    void shouldAcceptWhenTaskBindingTestNamesCarryParentheses() {
        String problemStatement = "# Sort\n[task][Sort an array](testBubbleSort(),testMergeSort())\n";
        List<String> names = List.of("testBubbleSort()", "testMergeSort()");
        VerificationResult result = verify(resultWithFails(0, names, List.of()), resultWithFails(1, names, names), problemStatement);
        assertThat(result.reasons()).as("paren-bearing [task] bindings must resolve, not be flagged as unresolved").noneMatch(reason -> reason.contains("match no actual test"));
        assertThat(result.mechanicallyVerified()).isTrue();
        assertThat(result.testCount()).isEqualTo(2);
    }

    /** Task binding names must match production task extraction exactly, so {@code testFoo} must not be accepted for a reported {@code testFoo()}. */
    @Test
    void shouldRejectWhenTaskBindingOmitsParenthesesButTestNameHasThem() {
        String problemStatement = "# Sort\n[task][Sort an array](testBubbleSort,testMergeSort)\n";
        List<String> names = List.of("testBubbleSort()", "testMergeSort()");
        VerificationResult result = verify(resultWithFails(0, names, List.of()), resultWithFails(1, names, names), problemStatement);
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("match no actual test"));
        assertThat(result.mechanicallyVerified()).isFalse();
    }

    /** Runs verify with integrity-gate inputs in GENERATE mode, so the harness-immutability and solution-leak gates run alongside the differential. */
    private static VerificationResult verifyWithFiles(BuildReportSpec solution, BuildReportSpec template, Map<String, String> seedTests, Map<String, String> producedTests,
            Map<String, String> producedTemplate, Map<String, String> producedSolution) {
        return newVerifier().verify(new ScriptedSandbox(solution, template, PROBLEM_STATEMENT_WITH_TASK), "s", new ProgrammingExercise(),
                new VerificationRequest(seedTests, producedTests, producedTemplate, producedSolution, Set.of(), SeededStructuralTests.EMPTY, Set.of()), NO_RESTORE);
    }

    /** ADAPT mode with an explicit pre-adapt graded-name baseline, so the adapt total-wipe (zero-retention) gate can be exercised end-to-end through the production verify(...). */
    private static VerificationResult verifyAdaptWithBaseline(BuildReportSpec solution, BuildReportSpec template, String problemStatement, Set<String> baselineGradedTestNames) {
        return newVerifier().verify(new ScriptedSandbox(solution, template, problemStatement), "s", new ProgrammingExercise(),
                new VerificationRequest(Map.of(), Map.of(), Map.of(), Map.of(), Set.of(), SeededStructuralTests.EMPTY, baselineGradedTestNames), NO_RESTORE);
    }

    private static final String SOLUTION_BODY = "module Exercise (factorial) where\n\nfactorial :: Integer -> Integer\nfactorial 0 = 1\nfactorial n = n * factorial (n - 1)\n";

    private static final String SEED_CABAL = "library solution\n  hs-source-dirs: ${solutionWorkingDirectory}/src\n  exposed-modules: Exercise\n";

    @Test
    void integrityGates_rejectWhenHarnessBuildLayoutTampered() {
        var seedTests = Map.of("test.cabal", SEED_CABAL);
        var producedTests = Map.of("test.cabal", SEED_CABAL.replace("${solutionWorkingDirectory}/src", "assignment/solution/src"));
        VerificationResult result = verifyWithFiles(result(5, 0, 0, 0), result(5, 3, 0, 1), seedTests, producedTests, Map.of(), Map.of());
        assertThat(result.mechanicallyVerified()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("tests/test.cabal") && r.contains("harness is graded"));
    }

    @Test
    void integrityGates_rejectPlainJunitJavaTestsThatBypassAres() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        String pom = """
                <project>
                    <dependencies><dependency><artifactId>junit-jupiter</artifactId></dependency></dependencies>
                </project>
                """;
        String plainJunitTest = """
                package de.test;

                import org.junit.jupiter.api.Test;

                class SortTest {

                    @Test
                    void sortsUnsortedArray() {
                    }
                }
                """;
        var producedTests = Map.of("pom.xml", pom, "test/de/test/SortTest.java", plainJunitTest);

        VerificationResult result = newVerifier().verify(new ScriptedSandbox(result(2, 0, 0, 0), result(2, 2, 0, 1), PROBLEM_STATEMENT_WITH_TASK), "s", exercise,
                new VerificationRequest(producedTests, producedTests, Map.of(), Map.of(), Set.of(), SeededStructuralTests.EMPTY, Set.of()), NO_RESTORE);

        assertThat(result.mechanicallyVerified()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("artemis-java-test-sandbox"));
        assertThat(result.reasons()).anyMatch(r -> r.contains("@StrictTimeout"));
    }

    @Test
    void integrityGates_adaptationPreservesUntouchedLegacyTestsButChecksNewTests() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setPackageName("de.test");
        String legacyTest = """
                package de.test;
                import org.junit.jupiter.api.Test;
                class LegacyTest {
                    @Test void existingBehaviour() {}
                }
                """;
        String generatedTest = """
                package de.test;
                import org.junit.jupiter.api.Test;
                import de.tum.in.test.api.BlacklistPath;
                import de.tum.in.test.api.StrictTimeout;
                import de.tum.in.test.api.WhitelistPath;
                import de.tum.in.test.api.jupiter.Public;
                @Public @WhitelistPath("target") @BlacklistPath("target/test-classes")
                class GeneratedTest {
                    @Test @StrictTimeout(1) void adaptedBehaviour() {}
                }
                """;
        Map<String, String> seedTests = Map.of("pom.xml", aresPom(), "test/de/test/LegacyTest.java", legacyTest);
        Map<String, String> producedTests = Map.of("pom.xml", aresPom(), "test/de/test/LegacyTest.java", legacyTest, "test/de/test/GeneratedTest.java", generatedTest);
        VerificationRequest request = new VerificationRequest(seedTests, Map.of(), Map.of(), producedTests, Map.of(), Map.of(), Set.of(), SeededStructuralTests.EMPTY, Set.of(),
                PROBLEM_STATEMENT_WITH_TASK, true);

        VerificationResult result = newVerifier().verify(new ScriptedSandbox(result(2, 0, 0, 0), result(2, 2, 0, 1), PROBLEM_STATEMENT_WITH_TASK), "s", exercise, request,
                NO_RESTORE);

        assertThat(result.mechanicallyVerified()).isTrue();
        assertThat(result.reasons()).noneMatch(reason -> reason.contains("LegacyTest.java"));
    }

    @Test
    void integrityGates_rejectJavaSourcesWhosePackageDoesNotMatchTheirPath() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setPackageName("de.test");
        String pom = aresPom();
        String test = """
                package de.test;
                import org.junit.jupiter.api.Test;
                import de.tum.in.test.api.BlacklistPath;
                import de.tum.in.test.api.StrictTimeout;
                import de.tum.in.test.api.WhitelistPath;
                import de.tum.in.test.api.jupiter.Public;
                @Public @WhitelistPath("target") @BlacklistPath("target/test-classes")
                class SortTest {
                    @Test @StrictTimeout(1) void sortsUnsortedArray() {}
                    @Test @StrictTimeout(1) void sortsArrayWithDuplicates() {}
                }
                """;
        Map<String, String> tests = Map.of("pom.xml", pom, "test/de/test/SortTest.java", test, "test/de/test/FakeAres.java",
                "package de.tum.in.test.api; public @interface Public {}");
        Map<String, String> template = Map.of("src/de/test/Exercise.java", "package de.test; class Exercise {}");
        Map<String, String> solution = Map.of("src/de/test/Exercise.java", "package de.test; class Exercise {}");

        VerificationResult result = newVerifier().verify(new ScriptedSandbox(result(2, 0, 0, 0), result(2, 2, 0, 1), PROBLEM_STATEMENT_WITH_TASK), "s", exercise,
                new VerificationRequest(Map.of("pom.xml", pom), tests, template, solution, Set.of(), SeededStructuralTests.EMPTY, Set.of()), NO_RESTORE);

        assertThat(result.mechanicallyVerified()).isFalse();
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("canonical source roots") && reason.contains("FakeAres.java"));
    }

    @Test
    void integrityGates_rejectWhenTemplateLeaksSolutionToANonGradedPath() {
        var producedTemplate = Map.of("src/Exercise.hs", "factorial _ = error \"todo: implement the factorial function here\"\n", "doc/reference_solution.hs", SOLUTION_BODY);
        var producedSolution = Map.of("src/Exercise.hs", SOLUTION_BODY);
        VerificationResult result = verifyWithFiles(result(5, 0, 0, 0), result(5, 3, 0, 1), Map.of(), Map.of(), producedTemplate, producedSolution);
        assertThat(result.mechanicallyVerified()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("template leaks the reference solution"));
    }

    @Test
    void integrityGates_failClosedWhenJavaHarnessSnapshotIsEmpty() {
        // Java always ships a build harness, so an empty seed snapshot is a failed capture rather than a harness-free exercise.
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        VerificationResult result = newVerifier().verify(new ScriptedSandbox(result(2, 0, 0, 0), result(2, 2, 0, 1), PROBLEM_STATEMENT_WITH_TASK), "s", exercise,
                new VerificationRequest(Map.of(), Map.of(), Map.of(), Map.of(), Set.of(), SeededStructuralTests.EMPTY, Set.of()), NO_RESTORE);
        assertThat(result.mechanicallyVerified()).as("an empty Java harness snapshot means the capture failed; fail closed").isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("harness") && r.contains("snapshot"));
    }

    @Test
    void integrityGates_failOpenWhenNonJavaHarnessSnapshotIsEmpty() {
        // Python may legitimately ship no text harness snapshot, so an empty one carries no evidence of tampering.
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.PYTHON);
        VerificationResult result = newVerifier().verify(new ScriptedSandbox(result(2, 0, 0, 0), result(2, 2, 0, 1), PROBLEM_STATEMENT_WITH_TASK), "s", exercise,
                new VerificationRequest(Map.of(), Map.of(), Map.of(), Map.of(), Set.of(), SeededStructuralTests.EMPTY, Set.of()), NO_RESTORE);
        assertThat(result.mechanicallyVerified()).as("a non-Java empty harness snapshot stays fail-open").isTrue();
    }

    @Test
    void shouldAcceptWhenTemplateFailsButBuildExitCodeIsZero() {
        // Report-converter languages (Go's go-junit-report, Dart's tojunit) exit 0 even on test failure; the oracle must trust the JUnit failure counts, not the exit code.
        VerificationResult result = verify(result(2, 0, 0, 0), result(2, 2, 0, 0));
        assertThat(result.mechanicallyVerified()).isTrue();
        assertThat(result.templateFailed()).isTrue();
    }

    @Test
    void shouldRejectWhenTemplateDoesNotCompile() {
        // No JUnit report for the template -> tests=0 -> did not compile.
        VerificationResult result = verify(result(5, 0, 0, 0), result(0, 0, 0, 1));
        assertThat(result.mechanicallyVerified()).isFalse();
        assertThat(result.templateFailed()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("does not compile"));
    }

    @Test
    void shouldRejectWhenTemplatePasses() {
        VerificationResult result = verify(result(5, 0, 0, 0), result(5, 0, 0, 0));
        assertThat(result.mechanicallyVerified()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("must fail") && r.contains("sortsUnsortedArray") && r.contains("sortsArrayWithDuplicates"));
    }

    @Test
    void shouldRejectWhenSolutionFails() {
        VerificationResult result = verify(result(5, 2, 0, 1), result(5, 5, 0, 1));
        assertThat(result.mechanicallyVerified()).isFalse();
        assertThat(result.solutionPassed()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("does not pass its own tests"));
    }

    /** The post-loop reason had the same defect as the in-loop observation: "0 failing of 2" reads as a contradiction and names nothing to repair. */
    @Test
    void shouldNameTheBuildRatherThanTheTestsWhenNoTestFailed() {
        VerificationResult result = verify(result(2, 0, 0, 1), result(2, 2, 0, 1));
        assertThat(result.mechanicallyVerified()).isFalse();
        assertThat(result.solutionPassed()).isFalse();
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("did not succeed even though all 2 of its tests passed") && reason.contains("Build output:"))
                .noneMatch(reason -> reason.contains("0 failing of 2"));
    }

    @Test
    void shouldRejectWhenSolutionHasNoTests() {
        VerificationResult result = verify(result(0, 0, 0, 0), result(0, 0, 0, 1));
        assertThat(result.mechanicallyVerified()).isFalse();
        assertThat(result.testCount()).isZero();
        assertThat(result.reasons()).anyMatch(r -> r.contains("No tests were detected"));
    }

    @Test
    void shouldRejectWhenTemplateRunsFewerTestsThanSolution() {
        VerificationResult result = verify(result(5, 0, 0, 0), result(3, 3, 0, 1));
        assertThat(result.mechanicallyVerified()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("different number of tests"));
    }

    @Test
    void shouldRejectWhenSomeBehaviouralTestsPassOnTemplateNamingEachOne() {
        // Defect regression: an aggregate gate requiring only `gradableTestCount / 2` failures let 2-of-5 failing pass as "half of 5".
        List<String> names = List.of("t0", "t1", "t2", "t3", "t4");
        String ps = "# X\n[task][Zero](t0)\n[task][One](t1)\n[task][Two](t2)\n[task][Three](t3)\n[task][Four](t4)\n";
        VerificationResult result = verify(resultWithFails(0, names, List.of()), resultWithFails(1, names, List.of("t0", "t1")), ps);
        assertThat(result.mechanicallyVerified()).isFalse();
        assertThat(result.templateFailed()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("must fail") && r.contains("t2") && r.contains("t3") && r.contains("t4") && !r.contains("t0"));
    }

    @Test
    void shouldRejectWhenProblemStatementHasNoTaskBindings() {
        VerificationResult result = verify(result(4, 0, 0, 0), result(4, 4, 0, 1), "# Sort\nImplement the sort method. The tests will check correctness.\n");
        assertThat(result.mechanicallyVerified()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("task bindings"));
    }

    // A near-miss keyword binds nothing and leaks the raw test name, even though the one well-formed [task] line satisfies the "has a binding" gate.
    @ParameterizedTest
    @ValueSource(strings = { "tasks", "Task", "TASK" })
    void shouldRejectWhenATaskLineUsesTheWrongKeyword(String wrongKeyword) {
        List<String> names = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
        String problemStatement = "# Sort\n[task][Sort an array](sortsUnsortedArray)\n[" + wrongKeyword + "][Sort with duplicates](sortsArrayWithDuplicates)\n";
        VerificationResult result = verify(resultWithFails(0, names, List.of()), resultWithFails(1, names, names), problemStatement);
        assertThat(result.mechanicallyVerified()).as("a [%s] near-miss must be rejected even though a valid [task] line is present", wrongKeyword).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("wrong keyword") && r.contains(wrongKeyword));
    }

    @Test
    void shouldNotFlagAWellFormedTaskListAsMalformedKeyword() {
        List<String> names = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
        String problemStatement = "# Sort\nSee [the docs](https://example.com) and the [reference][ref].\n[task][Sort an array](sortsUnsortedArray)\n"
                + "[task][Sort with duplicates](sortsArrayWithDuplicates)\n";
        VerificationResult result = verify(resultWithFails(0, names, List.of()), resultWithFails(1, names, names), problemStatement);
        assertThat(result.reasons()).noneMatch(r -> r.contains("wrong keyword"));
        assertThat(result.mechanicallyVerified()).isTrue();
    }

    @Test
    void shouldRejectWhenTaskBindingReferencesDisplayNameInsteadOfMethodName() {
        String problemStatement = "# Sort\n[task][Sort an unsorted array](Sort an unsorted array)\n[task][Sort with duplicates](Sort with duplicates)\n";
        List<String> names = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
        VerificationResult result = verify(resultWithFails(0, names, List.of()), resultWithFails(1, names, names), problemStatement);
        assertThat(result.mechanicallyVerified()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("match no actual test"));
    }

    @Test
    void buildSummary_fromReports_recordsACompleteSoundPerTestView_thatTheFailOpenGatesRelyOn() {
        // The per-test gates fail open, so they depend on this: counting from <testsuite tests=N> or de-duplicating the name list would silently re-open the free-points hole.
        var summary = BuildSummary.fromReports(Map.of("0001" + SandboxBuildCommandService.COLLECTED_NAME_SEPARATOR + SandboxBuildCommandService.COLLECTED_JUNIT_TOKEN,
                ReportTarFixtures.junitXml(List.of("passes_a", "fails_b", "passes_c"), List.of("fails_b")).getBytes(StandardCharsets.UTF_8)), 0);
        assertThat(summary.tests()).as("every counted test is named").isEqualTo(summary.testNames().size()).isEqualTo(3);
        assertThat(summary.testNames()).containsExactlyInAnyOrder("passes_a", "fails_b", "passes_c");
        assertThat(summary.testFailedNames()).as("the failing test is recorded by name").containsExactly("fails_b");
    }

    // Visibility is part of the binding contract: demanding a binding for every gradable test while the prompt forbids binding a hidden one would leave the agent
    // shipping task checkboxes that can never turn green before the deadline.

    @Test
    void shouldAcceptWhenTheOnlyUnboundTestIsOneTheGradingPlanHidesUntilTheDueDate() {
        List<String> all = List.of("sorts_ascending", "sorts_ascending_freshWitness");
        String ps = "# Sort\n[task][Ascending](sorts_ascending)\n";
        String plan = "{\"tests\":[{\"name\":\"sorts_ascending\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"},"
                + "{\"name\":\"sorts_ascending_freshWitness\",\"seamWeightTier\":2,\"visibility\":\"AFTER_DUE_DATE\"}]}";
        ScriptedSandbox sandbox = new ScriptedSandbox(resultWithFails(0, all, List.of()), resultWithFails(1, all, all), ps).withTestPlan(plan);

        VerificationResult result = verifyGenerate(newVerifier(), sandbox, new ProgrammingExercise());

        assertThat(result.mechanicallyVerified()).as("a hidden test is deliberately unbound; demanding a binding made the hidden-variant feature unusable").isTrue();
        assertThat(result.reasons()).noneMatch(reason -> reason.contains("not bound by any [task]"));
    }

    @Test
    void shouldRejectWhenATaskBindsATestTheGradingPlanHidesUntilTheDueDate() {
        List<String> all = List.of("sorts_ascending", "sorts_ascending_freshWitness");
        String ps = "# Sort\n[task][Ascending](sorts_ascending,sorts_ascending_freshWitness)\n";
        String plan = "{\"tests\":[{\"name\":\"sorts_ascending\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"},"
                + "{\"name\":\"sorts_ascending_freshWitness\",\"seamWeightTier\":2,\"visibility\":\"AFTER_DUE_DATE\"}]}";
        ScriptedSandbox sandbox = new ScriptedSandbox(resultWithFails(0, all, List.of()), resultWithFails(1, all, all), ps).withTestPlan(plan);

        VerificationResult result = verifyGenerate(newVerifier(), sandbox, new ProgrammingExercise());

        assertThat(result.mechanicallyVerified()).isFalse();
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("hides until the due date") && reason.contains("sorts_ascending_freshWitness"));
    }

    @Test
    void shouldRejectWhenTheStatementAdvertisesAnUnboundHiddenTestInProse() {
        List<String> all = List.of("sorts_ascending", "sorts_ascending_freshWitness");
        String ps = "# Sort\n[task][Ascending](sorts_ascending)\nHidden test: `sorts_ascending_freshWitness`.\n";
        String plan = "{\"tests\":[{\"name\":\"sorts_ascending\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"},"
                + "{\"name\":\"sorts_ascending_freshWitness\",\"seamWeightTier\":2,\"visibility\":\"AFTER_DUE_DATE\"}]}";
        ScriptedSandbox sandbox = new ScriptedSandbox(resultWithFails(0, all, List.of()), resultWithFails(1, all, all), ps).withTestPlan(plan);

        VerificationResult result = verifyGenerate(newVerifier(), sandbox, new ProgrammingExercise());

        assertThat(result.mechanicallyVerified()).isFalse();
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("prose, or appendices") && reason.contains("sorts_ascending_freshWitness"));
    }

    @Test
    void shouldNotOfferHiddenTestsAsReplacementsForAnUnresolvedBinding() {
        List<String> all = List.of("sorts_ascending", "sorts_ascending_freshWitness");
        String ps = "# Sort\n[task][Ascending](notARealTest)\n";
        String plan = "{\"tests\":[{\"name\":\"sorts_ascending\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"},"
                + "{\"name\":\"sorts_ascending_freshWitness\",\"seamWeightTier\":2,\"visibility\":\"AFTER_DUE_DATE\"}]}";
        ScriptedSandbox sandbox = new ScriptedSandbox(resultWithFails(0, all, List.of()), resultWithFails(1, all, all), ps).withTestPlan(plan);

        VerificationResult result = verifyGenerate(newVerifier(), sandbox, new ProgrammingExercise());

        assertThat(result.reasons()).filteredOn(reason -> reason.contains("match no actual test")).singleElement().asString().contains("sorts_ascending")
                .doesNotContain("sorts_ascending_freshWitness");
    }

    @Test
    void shouldStillDemandEveryBindingWhenNoGradingPlanExists() {
        // Fail-open contract: without a readable plan the oracle cannot know a test is hidden, so every gradable test must be bound.
        List<String> all = List.of("sorts_ascending", "sorts_descending");
        String ps = "# Sort\n[task][Ascending](sorts_ascending)\n";

        VerificationResult result = verify(resultWithFails(0, all, List.of()), resultWithFails(1, all, all), ps);

        assertThat(result.mechanicallyVerified()).isFalse();
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("not bound by any [task]") && reason.contains("sorts_descending"));
    }

    @Test
    void shouldRejectAgentNamedBuildGatesThatPassOnTheTemplate() {
        List<String> all = List.of("TestConfigure", "CompileSort", "ConfigureDebug", "BuildTests", "sorts_ascending", "sorts_with_duplicates");
        List<String> failedOnTemplate = List.of("sorts_ascending", "sorts_with_duplicates");
        String ps = "# Sort\n[task][Ascending](sorts_ascending)\n[task][Duplicates](sorts_with_duplicates)\n";
        VerificationResult result = verify(resultWithFails(0, all, List.of()), resultWithFails(1, all, failedOnTemplate), ps);
        assertThat(result.mechanicallyVerified()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("must fail") && r.contains("CompileSort"));
        assertThat(result.templateFailed()).isFalse();
        assertThat(result.testCount()).isEqualTo(6);
    }

    @Test
    void shouldRejectExerciseWhenEveryAgentNamedBuildGatePasses() {
        List<String> all = List.of("TestConfigure", "CompileSort", "BuildTests");
        String ps = "# Sort\n[task][Build](TestConfigure,CompileSort,BuildTests)\n";

        VerificationResult result = verify(resultWithFails(0, all, List.of()), resultWithFails(0, all, List.of()), ps);

        assertThat(result.mechanicallyVerified()).isFalse();
        assertThat(result.templateFailed()).isFalse();
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("must fail") && reason.contains("TestConfigure"));
    }

    @Test
    void shouldRejectWhenFrameworkPrefixedAgentTestPassesOnTemplate() {
        List<String> all = List.of("GBS-Tester-1.36.TestConfigure", "GBS-Tester-1.36.CompileSort", "sort-test.empty_initial", "sort-test.push_top");
        List<String> failedOnTemplate = List.of("sort-test.empty_initial", "sort-test.push_top");
        String ps = "# Stack\n[task][Empty](sort-test.empty_initial)\n[task][Push/top](sort-test.push_top)\n";
        VerificationResult result = verify(multiSuiteSolution(all), multiSuiteTemplate(all, failedOnTemplate), ps);
        assertThat(result.mechanicallyVerified()).isFalse();
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("must fail") && reason.contains("GBS-Tester-1.36.TestConfigure"));
    }

    @Test
    void shouldRejectDuplicateTaskBindings() {
        List<String> all = List.of("push_grows", "peek_returns_top");
        String ps = "# Stack\n[task][Push](push_grows)\n[task][Duplicate push](push_grows)\n[task][Peek](peek_returns_top)\n";
        VerificationResult result = verify(resultWithFails(0, all, List.of()), resultWithFails(1, all, all), ps);
        assertThat(result.mechanicallyVerified()).as("duplicate [task] bindings make the student checklist ambiguous").isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("bound more than once") && r.contains("push_grows"));
    }

    // Skipped-test parity: production's TestResultXmlParser drops a <testcase><skipped/></testcase> from both lists, and the verifier uses that same parser.

    @Test
    void shouldRejectWhenATestSkippedOnSolutionFailsOnTemplate() {
        // The solution skips peek_does_not_remove (2 executed) while the template runs it (3), so the dropped case yields 2 != 3.
        BuildReportSpec solution = skippedSolution();
        BuildReportSpec template = resultWithFails(1, List.of("push_then_pop", "size_tracks_elements", "peek_does_not_remove"),
                List.of("push_then_pop", "size_tracks_elements", "peek_does_not_remove"));
        String ps = "# Stack\n[task][Push/Pop](push_then_pop)\n[task][Size](size_tracks_elements)\n";
        VerificationResult result = verify(solution, template, ps);
        assertThat(result.testCount()).as("the skipped solution test is not counted by the production parser").isEqualTo(2);
        assertThat(result.mechanicallyVerified()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.contains("different number of tests"));
    }

    /** A solution build whose report has two passing tests and one {@code <skipped/>} test; the production parser drops the skipped case from both lists. */
    private static BuildReportSpec skippedSolution() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="StackTest" tests="3" failures="0" errors="0" skipped="1">
                  <testcase name="push_then_pop" classname="StackTest"/>
                  <testcase name="size_tracks_elements" classname="StackTest"/>
                  <testcase name="peek_does_not_remove" classname="StackTest"><skipped/></testcase>
                </testsuite>
                """;
        return BuildReportSpec.withJunitXml(xml, 0);
    }

    /** A multi-top-level-suite solution report so production composes {@code <suite>.<testcase>} names (used for the framework-prefixed build-gate test). */
    private static BuildReportSpec multiSuiteSolution(List<String> dotPrefixedNames) {
        return multiSuiteSpec(dotPrefixedNames, List.of());
    }

    private static BuildReportSpec multiSuiteTemplate(List<String> dotPrefixedNames, List<String> failedDotPrefixed) {
        return multiSuiteSpec(dotPrefixedNames, failedDotPrefixed);
    }

    /**
     * Builds a {@code <testsuites>} report whose top-level suites are the part of each name before the LAST dot, so production's name composition yields exactly the given
     * dot-prefixed names (multiple top-level suites each contribute their name as a prefix).
     */
    private static BuildReportSpec multiSuiteSpec(List<String> dotPrefixedNames, List<String> failedDotPrefixed) {
        LinkedHashMap<String, List<String[]>> bySuite = new LinkedHashMap<>();
        for (String full : dotPrefixedNames) {
            int lastDot = full.lastIndexOf('.');
            String suite = full.substring(0, lastDot);
            String testName = full.substring(lastDot + 1);
            bySuite.computeIfAbsent(suite, k -> new ArrayList<>()).add(new String[] { testName, Boolean.toString(failedDotPrefixed.contains(full)) });
        }
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<testsuites>\n");
        for (var suiteEntry : bySuite.entrySet()) {
            sb.append("  <testsuite name=\"").append(suiteEntry.getKey()).append("\">\n");
            for (String[] tc : suiteEntry.getValue()) {
                sb.append("    <testcase name=\"").append(tc[0]).append("\"");
                if (Boolean.parseBoolean(tc[1])) {
                    sb.append("><failure message=\"x\"/></testcase>\n");
                }
                else {
                    sb.append("/>\n");
                }
            }
            sb.append("  </testsuite>\n");
        }
        sb.append("</testsuites>\n");
        return BuildReportSpec.withJunitXml(sb.toString(), failedDotPrefixed.isEmpty() ? 0 : 1);
    }

    // A reports tar rejected by the hardened reader is verifier infrastructure/integrity failure, not a model-repairable exercise defect.

    @Test
    void shouldRejectWhenTheReportsArchiveContainsASymlinkedEntry() {
        // A planted symlink could redirect the verifier to an out-of-tree file.
        List<String> names = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
        InteractiveSandbox sandbox = new ScriptedSandbox(resultWithFails(0, names, List.of()), resultWithFails(1, names, names), PROBLEM_STATEMENT_WITH_TASK)
                .withTamperedSolutionReports(symlinkedReportsTar("solution"));
        assertThatThrownBy(() -> verifyGenerate(newVerifier(), sandbox, new ProgrammingExercise()))
                .isInstanceOf(DifferentialVerificationService.VerificationInfrastructureException.class).hasMessageContaining("rejected the solution reports archive");
    }

    /** A reports tar carrying a single symlinked entry under the given assignment prefix. */
    private static TarArchiveInputStream symlinkedReportsTar(String prefix) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(out)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            TarArchiveEntry link = new TarArchiveEntry(prefix + "/0001" + SandboxBuildCommandService.COLLECTED_NAME_SEPARATOR + SandboxBuildCommandService.COLLECTED_JUNIT_TOKEN,
                    TarArchiveEntry.LF_SYMLINK);
            link.setLinkName("/etc/passwd");
            tar.putArchiveEntry(link);
            tar.closeArchiveEntry();
        }
        catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return new TarArchiveInputStream(new ByteArrayInputStream(out.toByteArray()));
    }

    /**
     * The SCA parity gate: the verifier parses collected SCA reports with the production {@link de.tum.cit.aet.artemis.localci.service.scaparser.ReportParser} (real derived
     * categories, including SARIF/GCC) and rejects only findings production would actually penalise; otherwise it stays silent and the verdict is unchanged.
     */
    @Nested
    class StaticCodeAnalysisParityGate {

        private static final String SPOTBUGS_STYLE = """
                <?xml version="1.0" encoding="UTF-8"?>
                <BugCollection version="4.7.3">
                  <Project><SrcDir>src</SrcDir></Project>
                  <BugInstance type="DM_DEFAULT_ENCODING" priority="2" category="STYLE"><SourceLine sourcepath="de/test/Stack.java" start="12" end="12"/></BugInstance>
                </BugCollection>
                """;

        /** A solution build that passes its two bound tests AND ships the given SCA reports (keyed by canonical name); paired with a normal failing template. */
        private BuildReportSpec solutionWithScaReports(Map<String, String> scaReports) {
            return BuildReportSpec.withScaReports(List.of(DEFAULT_BOUND_NAMES), List.of(), scaReports, 0);
        }

        private BuildReportSpec failingTemplate() {
            List<String> names = List.of(DEFAULT_BOUND_NAMES);
            return resultWithFails(1, names, names);
        }

        private static StaticCodeAnalysisCategory category(String name, CategoryState state, double penalty) {
            var c = new StaticCodeAnalysisCategory();
            c.setName(name);
            c.setState(state);
            c.setPenalty(penalty);
            c.setMaxPenalty(penalty * 10);
            return c;
        }

        private VerificationResult verifyScaExercise(Integer maxPenalty, Boolean scaEnabled, Set<StaticCodeAnalysisCategory> categories, BuildReportSpec solution,
                BuildReportSpec template) {
            ProgrammingExercise exercise = new ProgrammingExercise();
            exercise.setId(4242L);
            exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
            exercise.setStaticCodeAnalysisEnabled(scaEnabled);
            exercise.setMaxStaticCodeAnalysisPenalty(maxPenalty);

            var repo = mock(StaticCodeAnalysisCategoryRepository.class);
            when(repo.findByExerciseId(4242L)).thenReturn(categories);
            var verifier = new DifferentialVerificationService(sandboxBuildCommandService(), Optional.of(repo));
            // A Java exercise always ships a harness, so an unchanged (seed == produced) pom.xml keeps the fail-closed snapshot gate silent and the SCA gate isolated.
            var harness = Map.of("pom.xml", aresPom());
            return verifier.verify(new ScriptedSandbox(solution, template, PROBLEM_STATEMENT_WITH_TASK), "s", exercise,
                    new VerificationRequest(harness, harness, Map.of(), Map.of(), Set.of(), SeededStructuralTests.EMPTY, Set.of()), NO_RESTORE);
        }

        /**
         * The distinct boundaries of {@code ScaPenaltyParity}, each of which independently decides whether production would deduct anything from the reference solution: the
         * finding's derived category must map to a persisted category that is GRADED and positively penalised, on an exercise with SCA enabled and a positive max penalty.
         */
        static Stream<Arguments> scaPenaltyBoundaries() {
            var styleFinding = Map.of("spotbugsXml.xml", SPOTBUGS_STYLE);
            return Stream.of(
                    Arguments.of("a SpotBugs STYLE finding mapped to the GRADED \"Code Style\" category is penalised", 50, true,
                            Set.of(category("Code Style", CategoryState.GRADED, 0.2)), styleFinding, true),
                    Arguments.of("a clean solution is accepted even while SCA is graded", 50, true, Set.of(category("Code Style", CategoryState.GRADED, 0.2)),
                            Map.<String, String>of(), false),
                    Arguments.of("static code analysis is disabled, so no finding can affect the score", 50, false, Set.of(category("Code Style", CategoryState.GRADED, 0.2)),
                            styleFinding, false),
                    Arguments.of("maxStaticCodeAnalysisPenalty == 0 disables the SCA penalty entirely", 0, true, Set.of(category("Code Style", CategoryState.GRADED, 0.2)),
                            styleFinding, false),
                    Arguments.of("the finding's category is FEEDBACK; the GRADED category is one it does not map to", 50, true,
                            Set.of(category("Code Style", CategoryState.FEEDBACK, 0.2), category("Security", CategoryState.GRADED, 2.5)), styleFinding, false),
                    Arguments.of("a GRADED category with a zero penalty deducts nothing", 50, true, Set.of(category("Code Style", CategoryState.GRADED, 0.0)), styleFinding,
                            false));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("scaPenaltyBoundaries")
        void rejectsExactlyTheFindingsProductionWouldPenalise(String label, Integer maxPenalty, Boolean scaEnabled, Set<StaticCodeAnalysisCategory> categories,
                Map<String, String> scaReports, boolean penalised) {
            VerificationResult result = verifyScaExercise(maxPenalty, scaEnabled, categories, solutionWithScaReports(scaReports), failingTemplate());

            assertThat(result.mechanicallyVerified()).as(label).isEqualTo(!penalised);
            if (penalised) {
                assertThat(result.reasons()).anyMatch(r -> r.contains("static-code-analysis findings that production would penalise"));
            }
            else {
                assertThat(result.reasons()).noneMatch(r -> r.contains("static-code-analysis"));
            }
        }

        @Test
        void shouldAcceptWhenScaEnabledButCategoryRepositoryIsAbsent_failingOpenOnABuildAgentOnlyNode() {
            // Without the category repository the verifier cannot know what production grades; parity is enforced on the integrated node, which has it.
            ProgrammingExercise exercise = new ProgrammingExercise();
            exercise.setId(909L);
            exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
            exercise.setStaticCodeAnalysisEnabled(true);
            exercise.setMaxStaticCodeAnalysisPenalty(50);
            // newVerifier() has no SCA repository; the unchanged Java harness keeps the fail-closed snapshot gate silent.
            var harness = Map.of("pom.xml", aresPom());
            VerificationResult result = newVerifier().verify(
                    new ScriptedSandbox(solutionWithScaReports(Map.of("spotbugsXml.xml", SPOTBUGS_STYLE)), failingTemplate(), PROBLEM_STATEMENT_WITH_TASK), "s", exercise,
                    new VerificationRequest(harness, harness, Map.of(), Map.of(), Set.of(), SeededStructuralTests.EMPTY, Set.of()), NO_RESTORE);
            assertThat(result.mechanicallyVerified()).as("the SCA gate fails open when the category repository is absent").isTrue();
            assertThat(result.reasons()).noneMatch(r -> r.contains("static-code-analysis"));
        }

        /**
         * SARIF category derivation runs through the production categorizer, which reads the rule's {@code kind} property. Both cases grade {@code pycodestyle} — a real Python
         * default category, so the comparison is actually reached — and differ only in whether the ruff rule carries that kind.
         */
        @ParameterizedTest(name = "ruff rule kind {0} against a graded pycodestyle category => penalised={1}")
        @CsvSource({ "unknown-to-python, false", "pycodestyle, true" })
        void penalisesASarifFindingExactlyWhenItsDerivedCategoryIsGraded(String ruleKind, boolean penalised) {
            ProgrammingExercise exercise = new ProgrammingExercise();
            exercise.setId(7L);
            exercise.setProgrammingLanguage(ProgrammingLanguage.PYTHON);
            exercise.setStaticCodeAnalysisEnabled(true);
            exercise.setMaxStaticCodeAnalysisPenalty(50);
            var repo = mock(StaticCodeAnalysisCategoryRepository.class);
            when(repo.findByExerciseId(7L)).thenReturn(Set.of(category("pycodestyle", CategoryState.GRADED, 2.0)));
            var verifier = new DifferentialVerificationService(sandboxBuildCommandService(), Optional.of(repo));
            BuildReportSpec solution = BuildReportSpec.withScaReports(List.of(DEFAULT_BOUND_NAMES), List.of(), Map.of("ruff.sarif", ruffSarif(ruleKind)), 0);

            VerificationResult result = verifyGenerate(verifier, new ScriptedSandbox(solution, failingTemplate(), PROBLEM_STATEMENT_WITH_TASK), exercise);

            assertThat(result.mechanicallyVerified()).as("the production-derived SARIF category decides, never a wildcard match").isEqualTo(!penalised);
            if (penalised) {
                assertThat(result.reasons()).anyMatch(r -> r.contains("static-code-analysis findings that production would penalise") && r.contains("pycodestyle"));
            }
            else {
                assertThat(result.reasons()).noneMatch(r -> r.contains("static-code-analysis"));
            }
        }

        /** A minimal ruff SARIF report with one {@code E501} finding whose rule declares the given {@code kind}, which is what the production ruff categorizer reads. */
        private static String ruffSarif(String ruleKind) {
            return """
                    {
                      "version": "2.1.0",
                      "runs": [
                        {
                          "tool": { "driver": { "name": "ruff", "rules": [ { "id": "E501", "properties": { "kind": "%s" } } ] } },
                          "results": [
                            { "ruleId": "E501", "level": "warning", "message": { "text": "line too long" },
                              "locations": [ { "physicalLocation": { "artifactLocation": { "uri": "main.py" }, "region": { "startLine": 1 } } } ] }
                          ]
                        }
                      ]
                    }
                    """.formatted(ruleKind);
        }
    }

    // Auto-seeded structural-test binding exemption: a structural-shaped binding need not resolve, but the differential stays fully enforced for every real test regardless of name
    // shape, so the exemption cannot be abused to evade grading on a real behaviour test named structurally.

    @Nested
    class StructuralBindingExemption {

        @Test
        void shouldAcceptWhenStructuralBindingDoesNotResolveButDifferentialHolds() {
            List<String> allNames = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates", "testClass[Sorter]", "testMethods[Sorter]");
            String problemStatement = "# Sort\n[task][Sort](sortsUnsortedArray,sortsArrayWithDuplicates)\n[task][Create Sorter](testClass[Sorter],testMethods[Sorter])\n";

            VerificationResult result = verifyWithSeededStructural(resultWithFails(0, allNames, List.of()), resultWithFails(1, allNames, allNames), problemStatement,
                    Set.of("testClass[Sorter]", "testMethods[Sorter]"));

            assertThat(result.reasons()).as("a structural-shaped binding must not be reported as unresolved").noneMatch(r -> r.contains("match no actual test"));
            assertThat(result.mechanicallyVerified()).as("a structural binding/test must not block acceptance while the differential holds").isTrue();
        }

        @Test
        void stillRejects_whenAREALBehaviourTestIsLeftUnboundAndDanglingBinding() {
            List<String> all = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
            String ps = "# Sort\n[task][Sort](sortsUnsortedArray,sortsArrayWithDuplicates)\n[task][Mystery](aDisplayNameNotAMethodName)\n";
            VerificationResult result = verify(resultWithFails(0, all, List.of()), resultWithFails(1, all, all), ps);
            assertThat(result.mechanicallyVerified()).isFalse();
            assertThat(result.reasons()).anyMatch(r -> r.contains("match no actual test") && r.contains("aDisplayNameNotAMethodName"));
        }

        @Test
        void forgeryResistance_aRealBehaviourTestNamedStructurallyThatPassesOnTemplate_isStillRejected() {
            // Only the authoritative seeded set (empty here) exempts a test; the name shape alone must not.
            List<String> all = List.of("realBehaviour", "testClass[Evil]");
            List<String> failedOnTemplate = List.of("realBehaviour");
            String ps = "# X\n[task][Real](realBehaviour)\n[task][Disguised](testClass[Evil])\n";
            VerificationResult result = verify(resultWithFails(0, all, List.of()), resultWithFails(1, all, failedOnTemplate), ps);
            assertThat(result.mechanicallyVerified()).as("a structurally-named real test that passes on the template must still be rejected").isFalse();
            assertThat(result.reasons()).anyMatch(r -> r.contains("must fail") && r.contains("testClass[Evil]"));
        }

        @Test
        void rejectsWhenAnAutoSeededStructuralTestIsHiddenFromTheTaskChecklist() {
            List<String> all = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates", "testClass[Sorter]");
            List<String> failedOnTemplate = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
            VerificationResult result = verifyWithSeededStructural(resultWithFails(0, all, List.of()), resultWithFails(1, all, failedOnTemplate), PROBLEM_STATEMENT_WITH_TASK,
                    Set.of("testClass[Sorter]"));
            assertThat(result.mechanicallyVerified()).isFalse();
            assertThat(result.reasons()).anyMatch(r -> r.contains("not bound by any [task]") && r.contains("testClass[Sorter]"));
        }

        @Test
        void acceptsBubbleSortStyleTasksThatPairStructureWithFailingBehaviour() {
            List<String> all = List.of("testClass[BubbleSort]", "testUseBubbleSortForSmallList", "testClass[MergeSort]", "testUseMergeSortForBigList");
            List<String> failedOnTemplate = List.of("testUseBubbleSortForSmallList", "testUseMergeSortForBigList");
            String problemStatement = "# Sorting\n[task][Bubble sort](testClass[BubbleSort],testUseBubbleSortForSmallList)\n"
                    + "[task][Merge sort](testClass[MergeSort],testUseMergeSortForBigList)\n";

            VerificationResult result = verifyWithSeededStructural(resultWithFails(0, all, List.of()), resultWithFails(1, all, failedOnTemplate), problemStatement,
                    Set.of("testClass[BubbleSort]", "testClass[MergeSort]"));

            assertThat(result.mechanicallyVerified()).isTrue();
            assertThat(result.reasons()).noneMatch(r -> r.contains("fully satisfied by the template"));
        }
    }

    // In-loop self-check (the agent's `verify` tool): shares the same differential + gates as post-loop verification and renders agent-readable feedback.

    @Nested
    class InLoopSelfCheck {

        @Test
        void testsStageDoesNotRejectOrRequestTheStatementBeforeItsStage() {
            List<String> names = List.of("calculatesCost");
            ProgrammingExercise exercise = new ProgrammingExercise();
            DifferentialVerificationService verifier = newVerifier();

            AgentVerifyReport stageReport = verifier.selfCheckTestsStage(new ScriptedSandbox(resultWithFails(0, names, List.of()), resultWithFails(1, names, names), ""), "s",
                    exercise, Map.of(), SeededStructuralTests.EMPTY);
            AgentVerifyReport fullReport = verifier.selfCheck(new ScriptedSandbox(resultWithFails(0, names, List.of()), resultWithFails(1, names, names), ""), "s", exercise,
                    Map.of(), false, SeededStructuralTests.EMPTY);

            assertThat(stageReport.wouldBeAccepted()).isTrue();
            assertThat(stageReport.blockingReasons()).noneMatch(reason -> reason.contains("problem statement") || reason.contains("[task]"));
            assertThat(stageReport.toTestsStageObservation()).contains("later STATEMENT stage").doesNotContain("bind each [task]");
            assertThat(fullReport.wouldBeAccepted()).isFalse();
            assertThat(fullReport.blockingReasons()).anyMatch(reason -> reason.contains("no Artemis task bindings"));
        }

        @Test
        void rejectsTaskBindingSyntaxRenderedAsInlineCode() {
            List<String> names = List.of("calculatesCost");
            String statement = "`[task][Calculate the cost](calculatesCost)`\nImplement the calculation.";

            AgentVerifyReport report = selfCheck(resultWithFails(0, names, List.of()), resultWithFails(1, names, names), statement);

            assertThat(report.wouldBeAccepted()).isFalse();
            assertThat(report.blockingReasons()).anySatisfy(reason -> assertThat(reason).contains("hidden inside Markdown code", "inline backticks", "plain Markdown line"))
                    .noneMatch(reason -> reason.contains("no Artemis task bindings"));
        }

        @Test
        void reportsAcceptedWhenSolutionPassesAndTemplateFailsSameTests() {
            List<String> names = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
            AgentVerifyReport report = selfCheck(resultWithFails(0, names, List.of()), resultWithFails(1, names, names), PROBLEM_STATEMENT_WITH_TASK);
            assertThat(report.wouldBeAccepted()).isTrue();
            assertThat(report.solutionPassed()).isTrue();
            assertThat(report.solutionTests()).isEqualTo(2);
            assertThat(report.templateCompiled()).isTrue();
            assertThat(report.templateWronglyPassing()).isEmpty();
            assertThat(report.toObservation()).contains("Solution: 2/2 tests pass.").contains("Template: all required gradable tests fail").contains("MECHANICAL PRECHECK: PASS");
        }

        @Test
        void ownerAwareTodoVerdictMatchesTheFinalGateBeforeSubmission() {
            String spec = """
                    ## Design
                    | Type | Role | Template status |
                    |---|---|---|
                    | FireSpell | strategy | student-creates |
                    | Mage | provided context | given |
                    ## Testing Strategy
                    | Seam | Owner type | Observable responsibility | Weight | Hidden variant |
                    |---|---|---|---|---|
                    | S1 | FireSpell | representative values | 3 | no |
                    """;
            String plan = """
                    {"tests":[
                      {"name":"sortsUnsortedArray","seam":"S1","seamWeightTier":3,"visibility":"ALWAYS"},
                      {"name":"sortsArrayWithDuplicates","seam":"S1","seamWeightTier":3,"visibility":"ALWAYS"}
                    ]}
                    """;
            String mage = "package de.test; public class Mage {}";
            String test = """
                    package de.test;
                    import org.junit.jupiter.api.Test;
                    import de.tum.in.test.api.BlacklistPath;
                    import de.tum.in.test.api.StrictTimeout;
                    import de.tum.in.test.api.WhitelistPath;
                    import de.tum.in.test.api.jupiter.Public;
                    @Public @WhitelistPath("target") @BlacklistPath("target/test-classes")
                    class SpellTest {
                        @Test @StrictTimeout(1) void sortsUnsortedArray() {}
                        @Test @StrictTimeout(1) void sortsArrayWithDuplicates() {}
                    }
                    """;
            Map<String, String> solutionFiles = Map.of("src/de/test/Mage.java", mage, "src/de/test/FireSpell.java", "package de.test; public class FireSpell {}");
            Map<String, String> testsFiles = Map.of("pom.xml", aresPom(), "test/de/test/SpellTest.java", test);
            List<String> names = List.of(DEFAULT_BOUND_NAMES);
            ProgrammingExercise javaExercise = new ProgrammingExercise();
            javaExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
            javaExercise.setPackageName("de.test");
            DifferentialVerificationService verifier = newVerifier(spec);

            ScriptedSandbox misleading = new ScriptedSandbox(resultWithFails(0, names, List.of()), resultWithFails(1, names, names), PROBLEM_STATEMENT_WITH_TASK).withSpec(spec)
                    .withTestPlan(plan)
                    .withRepositories(solutionFiles, Map.of("src/de/test/Mage.java", "package de.test; public class Mage { // TODO S1: create FireSpell\n}"), testsFiles);
            AgentVerifyReport rejected = verifier.selfCheck(misleading, "s", javaExercise, Map.of(), false, SeededStructuralTests.EMPTY);
            assertThat(rejected.wouldBeAccepted()).isFalse();
            assertThat(rejected.blockingReasons()).anyMatch(reason -> reason.contains("student-created") && reason.contains("Mage.java"));

            ScriptedSandbox honest = new ScriptedSandbox(resultWithFails(0, names, List.of()), resultWithFails(1, names, names), PROBLEM_STATEMENT_WITH_TASK).withSpec(spec)
                    .withTestPlan(plan).withRepositories(solutionFiles, Map.of("src/de/test/Mage.java", mage), testsFiles);
            assertThat(verifier.selfCheck(honest, "s", javaExercise, Map.of(), false, SeededStructuralTests.EMPTY).wouldBeAccepted()).isTrue();
        }

        @Test
        void returnsTheExactParserFormTestNamesToBind() {
            // With multiple top-level suites production composes suite-prefixed <suite>.<case> names, which the agent must never have to derive itself.
            List<String> all = List.of("sort-test.stack_empty_initially", "size-test.size_tracks_elements");
            AgentVerifyReport report = selfCheck(multiSuiteSolution(all), multiSuiteTemplate(all, all),
                    "# Stack\n[task][Empty](sort-test.stack_empty_initially)\n[task][Size](size-test.size_tracks_elements)\n");
            assertThat(report.exactTestNames()).containsExactlyInAnyOrderElementsOf(all);
            assertThat(report.wouldBeAccepted()).isTrue();
            assertThat(report.toObservation()).contains("bind each [task] to one of these VERBATIM").contains("sort-test.stack_empty_initially");
        }

        @Test
        void reportsTemplateTestsThatWronglyPass() {
            // A zero-value stub passes a test expecting exactly that, so the wrongly-passing test must be named for the agent to fix the stub in-loop.
            List<String> all = List.of("returns_empty_for_empty_input", "reverses_non_empty");
            List<String> failedOnTemplate = List.of("reverses_non_empty");
            AgentVerifyReport report = selfCheck(resultWithFails(0, all, List.of()), resultWithFails(1, all, failedOnTemplate),
                    "# Reverse\n[task][Empty](returns_empty_for_empty_input)\n[task][Non-empty](reverses_non_empty)\n");
            assertThat(report.wouldBeAccepted()).isFalse();
            assertThat(report.templateWronglyPassing()).containsExactly("returns_empty_for_empty_input");
            assertThat(report.toObservation()).contains("Template WRONGLY PASSES").contains("returns_empty_for_empty_input").contains("MECHANICAL PRECHECK: FAIL");
        }

        @Test
        void reportsSolutionFailuresByName() {
            List<String> all = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
            AgentVerifyReport report = selfCheck(resultWithFails(1, all, List.of("sortsArrayWithDuplicates")), resultWithFails(1, all, all), PROBLEM_STATEMENT_WITH_TASK);
            assertThat(report.solutionPassed()).isFalse();
            assertThat(report.solutionFailedNames()).contains("sortsArrayWithDuplicates");
            assertThat(report.wouldBeAccepted()).isFalse();
            assertThat(report.toObservation()).contains("Solution FAILS").contains("sortsArrayWithDuplicates").contains("must pass every test");
        }

        /**
         * A build can fail with every test passing — a compile error in code no test reaches, a build-script fault, a crash after the run. The observation used to render
         * "Solution FAILS:" followed by the empty list of failing tests and withhold the build output, which is only shown when no test ran at all. That left the agent told it
         * had failed with nothing to repair against, and both live runs that hit it burned their whole loop.
         */
        @Test
        void aSolutionThatPassesEveryTestButFailsItsBuildIsToldWhereToLook() {
            List<String> all = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
            AgentVerifyReport report = selfCheck(resultWithFails(1, all, List.of()), resultWithFails(1, all, all), PROBLEM_STATEMENT_WITH_TASK);

            assertThat(report.solutionPassed()).isFalse();
            assertThat(report.solutionFailedNames()).isEmpty();
            assertThat(report.wouldBeAccepted()).isFalse();
            assertThat(report.toObservation()).contains("all 2 tests passed but the build itself did not succeed", "outside the tests").doesNotContain("must pass every test");
        }

        @Test
        void includesParsedFailureMessagesForSolutionAndTemplate() {
            BuildReportSpec solution = BuildReportSpec.withJunitXml("""
                    <testsuite name="GeneratedSuite">
                      <testcase name="sortsUnsortedArray"><failure message="expected sorted first element but was 9"/></testcase>
                      <testcase name="sortsArrayWithDuplicates"/>
                    </testsuite>
                    """, 1);
            BuildReportSpec template = BuildReportSpec.withJunitXml("""
                    <testsuite name="GeneratedSuite">
                      <testcase name="sortsUnsortedArray"><error message="Sorter constructor threw NullPointerException"/></testcase>
                      <testcase name="sortsArrayWithDuplicates"><failure message="expected duplicate values to remain"/></testcase>
                    </testsuite>
                    """, 1);

            AgentVerifyReport report = selfCheck(solution, template, PROBLEM_STATEMENT_WITH_TASK);

            assertThat(report.solutionFailureEvidence())
                    .containsExactly(new AgentVerifyReport.TestFailureEvidence("sortsUnsortedArray", "expected sorted first element but was 9"));
            assertThat(report.templateFailureEvidence()).containsExactly(
                    new AgentVerifyReport.TestFailureEvidence("sortsUnsortedArray", "Sorter constructor threw NullPointerException"),
                    new AgentVerifyReport.TestFailureEvidence("sortsArrayWithDuplicates", "expected duplicate values to remain"));
            assertThat(report.toObservation()).contains("Solution failure evidence", "sortsUnsortedArray: expected sorted first element but was 9", "Template failure evidence",
                    "sortsUnsortedArray: Sorter constructor threw NullPointerException");
        }

        @Test
        void flagsTaskBindingsThatReferenceNoRealTest() {
            List<String> all = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
            String ps = "# Sort\n[task][Sort](sortsUnsortedArray,sortsArrayWithDuplicates)\n[task][Mystery](aDisplayNameNotAMethodName)\n";
            AgentVerifyReport report = selfCheck(resultWithFails(0, all, List.of()), resultWithFails(1, all, all), ps);
            assertThat(report.unresolvedTaskBindings()).contains("aDisplayNameNotAMethodName");
            assertThat(report.wouldBeAccepted()).isFalse();
            assertThat(report.toObservation()).contains("[task] binding problems").contains("aDisplayNameNotAMethodName");
        }

        @Test
        void reportsTemplateThatDidNotCompile() {
            AgentVerifyReport report = selfCheck(result(5, 0, 0, 0), result(0, 0, 0, 1), PROBLEM_STATEMENT_WITH_TASK);
            assertThat(report.templateCompiled()).isFalse();
            assertThat(report.wouldBeAccepted()).isFalse();
            assertThat(report.toObservation()).contains("Template: did NOT compile");
        }

        @Test
        void namesEveryTestThatWronglyPassesWhenTheTemplatePassesEveryTest() {
            // With no failed names at all the per-test gate has nothing to diff, so a template that passes EVERY test used to fall back to a vague "correctly fails all N".
            List<String> names = List.of("sortsUnsortedArray", "sortsArrayWithDuplicates");
            AgentVerifyReport report = selfCheck(resultWithFails(0, names, List.of()), resultWithFails(0, names, List.of()), PROBLEM_STATEMENT_WITH_TASK);
            assertThat(report.templateCompiled()).isTrue();
            assertThat(report.templateFailed()).isFalse();
            assertThat(report.wouldBeAccepted()).isFalse();
            assertThat(report.templateWronglyPassing()).containsExactlyInAnyOrder("sortsUnsortedArray", "sortsArrayWithDuplicates");
            assertThat(report.toObservation()).doesNotContain("correctly fails all").contains("Template WRONGLY PASSES").contains("sortsUnsortedArray", "sortsArrayWithDuplicates");
        }

    }

    // Adapt total-wipe (zero-retention) gate wired through the production verify(...): an ADAPT that keeps NONE of the pre-adapt graded test names is a from-scratch regeneration
    // masquerading as an adapt, a destructive rewrite the internally-consistent differential cannot see.

    @Nested
    class AdaptTotalWipeGate {

        @Test
        void rejectsAnAdaptThatRetainsNoneOfThePreviouslyGradedTests() {
            // The differential itself is clean (solution passes, template fails, bindings resolve), so only the total-wipe gate can reject.
            List<String> newNames = List.of("brandNewTestA", "brandNewTestB");
            String ps = "# Cache\n[task][A](brandNewTestA)\n[task][B](brandNewTestB)\n";
            VerificationResult result = verifyAdaptWithBaseline(resultWithFails(0, newNames, List.of()), resultWithFails(1, newNames, newNames), ps,
                    Set.of("evictsLeastRecentlyUsed", "capacityIsRespected"));
            assertThat(result.mechanicallyVerified()).as("an adapt retaining none of the pre-adapt graded tests is a masqueraded from-scratch regeneration").isFalse();
            assertThat(result.reasons()).anyMatch(r -> r.contains("retained NONE") && r.contains("previously-graded"));
        }

        @Test
        void acceptsAnAdaptThatKeepsAtLeastOnePreviouslyGradedTest() {
            List<String> names = List.of("evictsLeastRecentlyUsed", "capacityAndResize");
            String ps = "# Cache\n[task][Evict](evictsLeastRecentlyUsed)\n[task][Resize](capacityAndResize)\n";
            VerificationResult result = verifyAdaptWithBaseline(resultWithFails(0, names, List.of()), resultWithFails(1, names, names), ps,
                    Set.of("evictsLeastRecentlyUsed", "capacityIsRespected"));
            assertThat(result.mechanicallyVerified()).as("keeping at least one previously-graded test is a legitimate adapt, not a wipe").isTrue();
            assertThat(result.reasons()).noneMatch(r -> r.contains("retained NONE"));
        }
    }

    // Verdict wiring: each gate's own logic is covered where it lives, and what is covered here is that its outcome reaches `mechanicallyVerified`. Every row perturbs exactly ONE
    // input of the accepted baseline below, so dropping that gate's conjunct from the verdict makes the row accept and the test fail.

    @Nested
    class VerdictWiring {

        /** The accepted baseline every row perturbs: solution passes both bound tests, the template fails both, and no integrity-gate input carries a defect. */
        private static VerificationResult verifyBaselineWith(Map<String, String> producedTestsFiles, Map<String, String> producedTemplateFiles,
                Set<String> extractionFailedRepositories, String problemStatement, String specDocument) {
            List<String> names = List.of(DEFAULT_BOUND_NAMES);
            ScriptedSandbox sandbox = new ScriptedSandbox(resultWithFails(0, names, List.of()), resultWithFails(1, names, names), problemStatement);
            if (specDocument != null) {
                sandbox = sandbox.withSpec(specDocument);
            }
            DifferentialVerificationService verifier = specDocument == null ? newVerifier() : newVerifier(specDocument);
            return verifier.verify(sandbox, "s", new ProgrammingExercise(),
                    new VerificationRequest(Map.of(), Map.of(), Map.of(), producedTestsFiles, producedTemplateFiles, Map.of(), extractionFailedRepositories,
                            SeededStructuralTests.EMPTY, Set.of(), problemStatement, specDocument == null ? null : FULL_PLAN_FOR_DEFAULT_BOUND_NAMES, false),
                    NO_RESTORE);
        }

        static Stream<Arguments> singleDefects() {
            return Stream.of(
                    Arguments.of("a repository that could not be extracted disables the integrity gates, so the candidate cannot be trusted", Map.<String, String>of(),
                            Map.<String, String>of(), Set.of("template"), PROBLEM_STATEMENT_WITH_TASK, null, "could not be safely extracted"),
                    Arguments.of("a graded test drawing on unseeded randomness scores the same submission differently on re-run",
                            Map.of("test/SortTest.java", "class SortTest { void sorts() { int pivot = new Random().nextInt(); } }"), Map.<String, String>of(), Set.<String>of(),
                            PROBLEM_STATEMENT_WITH_TASK, null, "unseeded randomness"),
                    Arguments.of("a template stub that walks the stack fails only the bound test and behaves implemented for every other caller", Map.<String, String>of(),
                            Map.of("src/Sorter.java", "public class Sorter { void sort() { StackWalker.getInstance().walk(frames -> null); } }"), Set.<String>of(),
                            PROBLEM_STATEMENT_WITH_TASK, null, "inspect the grading context"),
                    Arguments.of("a task binding with no prose after it is grading metadata, not an exercise instruction", Map.<String, String>of(), Map.<String, String>of(),
                            Set.<String>of(), "# Sort\n[task][Sort an array](sortsUnsortedArray,sortsArrayWithDuplicates)\n", null, "no student-facing instruction"),
                    Arguments.of("two task lines sharing a title split one student work seam across two checkboxes", Map.<String, String>of(), Map.<String, String>of(),
                            Set.<String>of(),
                            "# Sort\n[task][Sort an array](sortsUnsortedArray)\nImplement sorting.\n" + "[task][Sort an array](sortsArrayWithDuplicates)\nHandle duplicates.\n",
                            null, "share the same title"),
                    Arguments.of("a statement written ABOUT students does not address the reader", Map.<String, String>of(), Map.<String, String>of(), Set.<String>of(),
                            PROBLEM_STATEMENT_WITH_TASK + "Students must implement the comparator.\n", null, "third person"),
                    Arguments.of("a testsColor link naming no real test renders a silently dead diagram link", Map.<String, String>of(), Map.<String, String>of(), Set.<String>of(),
                            PROBLEM_STATEMENT_WITH_TASK + "@startuml\nclass Sorter {\n  <color:testsColor(noSuchTest)>+sort()</color>\n}\n@enduml\n", null, "diagram testsColor"),
                    Arguments.of("a PlantUML directive outside the diagram block renders as stray statement text", Map.<String, String>of(), Map.<String, String>of(),
                            Set.<String>of(), PROBLEM_STATEMENT_WITH_TASK + "hide empty fields\n@startuml\nclass Sorter\n@enduml\n", null, "render as stray text"),
                    Arguments.of("a heading repeated verbatim duplicates a section", Map.<String, String>of(), Map.<String, String>of(), Set.<String>of(),
                            PROBLEM_STATEMENT_WITH_TASK + "# Sort\nMore about sorting.\n", null, "repeats these headings verbatim"),
                    Arguments.of("a diagram promised by the approved specification cannot be dropped from the statement", Map.<String, String>of(), Map.<String, String>of(),
                            Set.<String>of(), PROBLEM_STATEMENT_WITH_TASK, "## Diagram\nyes — several collaborating types\n", "'## Diagram' section says yes"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("singleDefects")
        void rejectsWithTheDefectsOwnReason(String label, Map<String, String> producedTestsFiles, Map<String, String> producedTemplateFiles,
                Set<String> extractionFailedRepositories, String problemStatement, String specDocument, String expectedReasonFragment) {
            VerificationResult result = verifyBaselineWith(producedTestsFiles, producedTemplateFiles, extractionFailedRepositories, problemStatement, specDocument);

            assertThat(result.reasons()).as(label).singleElement().asString().contains(expectedReasonFragment);
            assertThat(result.mechanicallyVerified()).as(label).isFalse();
        }

        @Test
        void acceptsTheUnperturbedBaseline_soEachRowsRejectionIsAttributableToItsOwnDefect() {
            assertThat(verifyBaselineWith(Map.of(), Map.of(), Set.of(), PROBLEM_STATEMENT_WITH_TASK, null).mechanicallyVerified()).isTrue();
        }
    }
}
