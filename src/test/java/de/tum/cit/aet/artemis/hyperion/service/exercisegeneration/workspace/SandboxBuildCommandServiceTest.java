package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.condition.OS.LINUX;
import static org.junit.jupiter.api.condition.OS.MAC;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.buildagent.dto.LocalCITestJobDTO;
import de.tum.cit.aet.artemis.buildagent.service.parser.TestResultXmlParser;
import de.tum.cit.aet.artemis.localci.service.BuildPhasesTemplateService;
import de.tum.cit.aet.artemis.localci.service.BuildScriptProviderService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;

/**
 * Deterministic unit test for the generated {@code verify.sh}: it must embed the exercise's real build phases with the CI placeholders substituted to the hermetic layout, and
 * collect the build-fresh test/SCA reports into the verifier-owned dir from both the phase result paths and the common per-language locations. The OS-gated nested classes drive
 * the live collect step under a real {@code sh} and feed the collected report into the production {@code TestResultXmlParser}, proving collection covers exactly what the verifier
 * parses.
 */
class SandboxBuildCommandServiceTest {

    private static SandboxBuildCommandService factoryWithPhases(List<BuildPhaseDTO> phases) {
        BuildPhasesTemplateService phasesService = mock(BuildPhasesTemplateService.class);
        when(phasesService.getDefaultBuildPlanPhasesFor(any())).thenReturn(phases);
        return new SandboxBuildCommandService(Optional.of(phasesService), Optional.of(new BuildScriptProviderService()));
    }

    @Test
    void verifyScript_substitutesCiPlaceholders_andSearchesPhaseResultPaths() {
        // A Python-like phase that cd's into the (placeholder) test working directory and writes its report under test-reports/.
        BuildPhaseDTO phase = new BuildPhaseDTO("test", "cd ${testWorkingDirectory}\npytest --junitxml=test-reports/results.xml", null, false,
                List.of("test-reports/*results.xml"));
        String script = factoryWithPhases(List.of(phase)).verifyScriptContent(new ProgrammingExercise());

        // The PHASE form of ${testWorkingDirectory} is substituted to "." (the seeded-harness sed stanza keeps the literal placeholder — a separate mechanism — so assert only the
        // phase).
        assertThat(script).doesNotContain("cd ${testWorkingDirectory}").contains("cd .");
        assertThat(script).contains("test-reports");
        // Only the non-authoritative liveness line is printed; no stdout-scraped verdict markers.
        assertThat(script).contains(SandboxBuildCommandService.COLLECTED_MARKER + " tests=$collected_tests").doesNotContain("HYPERION_RESULT").doesNotContain("HYPERION_TESTNAME");
    }

    @Test
    void verifyScript_collectsReportsIntoTheVerifierOwnedDir_regularFilesOnly_mtimeGated() {
        BuildPhaseDTO phase = new BuildPhaseDTO("test", "echo run", null, false, List.of());
        String script = factoryWithPhases(List.of(phase)).verifyScriptContent(new ProgrammingExercise());
        // Per-assignment subdir of the verifier-owned reports dir, re-seeded empty each run.
        assertThat(script).contains("REPORTS_DIR=\"" + SandboxBuildCommandService.REPORTS_DIR + "/$ASSIGNMENT\"").contains("rm -rf \"$REPORTS_DIR\"")
                .contains("mkdir -p \"$REPORTS_DIR\"");
        // Only build-fresh regular files (find -type f, -newer marker excludes planted stale reports), renamed to <seq>__<canonical> with the JUnit token.
        assertThat(script).contains("find \"$BUILD_DIR\" -type f -newer \"$BUILD_START_MARKER\"").contains("cp -P")
                .contains(SandboxBuildCommandService.COLLECTED_NAME_SEPARATOR + "$canonical").contains(SandboxBuildCommandService.COLLECTED_JUNIT_TOKEN);
    }

    @Test
    void verifyScript_java_searchesTheBuildDirDirectlyForJunitReports_noRedirect() {
        // Maven Surefire's reportsDirectory parameter has no CLI-settable property binding, so a -Dsurefire.reportsDirectory=... flag is silently ignored and Surefire always
        // writes to ${project.build.directory}/surefire-reports. The collection step must therefore search $BUILD_DIR with the same globs as every other language, not a
        // redirect directory Maven never writes to.
        ProgrammingExercise java = new ProgrammingExercise();
        java.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        String script = new SandboxBuildCommandService(Optional.empty(), Optional.empty()).verifyScriptContent(java);

        assertThat(script)
                .contains("find \"$BUILD_DIR\" -type f -newer \"$BUILD_START_MARKER\" \\( -path '*/target/surefire-reports/*.xml' -o -path '*/target/failsafe-reports/*.xml'")
                .doesNotContain("RAW_MAVEN_REPORTS_DIR").doesNotContain("surefire.reportsDirectory").doesNotContain("failsafe.reportsDirectory");
    }

    @Test
    void verifyScript_neverLeavesAnUnsubstitutedTemplateToken() {
        // Regression guard for tokens like @@REPORT_FIND@@, which must be computed/interpolated and registered in the final .replace(...) chain, so the shell script never
        // silently carries the literal placeholder text instead of the real value.
        ProgrammingExercise defaultJava = new ProgrammingExercise();
        defaultJava.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        String defaultScript = new SandboxBuildCommandService(Optional.empty(), Optional.empty()).verifyScriptContent(defaultJava);
        assertThat(defaultScript).doesNotContain("@@");

        ProgrammingExercise java = new ProgrammingExercise();
        java.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        java.setProjectType(ProjectType.MAVEN_MAVEN);
        String javaScript = new SandboxBuildCommandService(Optional.empty(), Optional.empty()).verifyScriptContent(java);
        assertThat(javaScript).doesNotContain("@@");
    }

    @Test
    void verifyScript_escapesSingleQuotesInReportGlobs_soAQuotedCheckoutPathCannotBreakTheShell() {
        // A report glob derived from an instructor-configured checkout path can contain a single quote. Escape it with the POSIX '\'' idiom rather than letting it close the
        // single-quoted `find -path '...'` predicate and inject shell.
        BuildPhaseDTO phase = new BuildPhaseDTO("test", "echo run", null, false, List.of("o'dir/results.xml"));
        String script = factoryWithPhases(List.of(phase)).verifyScriptContent(new ProgrammingExercise());

        assertThat(script).contains("-path '*/o'\\''dir/results.xml'").doesNotContain("-path '*/o'dir/results.xml'");
    }

    @EnabledOnOs({ LINUX, MAC })
    @Test
    void verifyScript_withAQuotedReportGlob_isStillValidPosixShell(@TempDir Path tempDir) throws Exception {
        BuildPhaseDTO phase = new BuildPhaseDTO("test", "echo run", null, false, List.of("o'dir/results.xml"));
        String script = factoryWithPhases(List.of(phase)).verifyScriptContent(new ProgrammingExercise());
        Path scriptFile = tempDir.resolve("quoted-glob.sh");
        VerifyScriptTestHarness.writeString(scriptFile, script);

        Process process = new ProcessBuilder("sh", "-n", scriptFile.toString()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("sh -n did not finish in time");
        }
        assertThat(process.exitValue()).as("a report glob with an embedded single quote keeps the script valid POSIX sh (sh -n: %s)", output).isZero();
    }

    @Test
    void verifyScript_materializesTestsInSubdir_forLanguagesThatCheckOutTestsThere() {
        ProgrammingExercise go = new ProgrammingExercise();
        go.setProgrammingLanguage(ProgrammingLanguage.GO);
        BuildPhaseDTO phase = new BuildPhaseDTO("test", "cd ${testWorkingDirectory}\ngo test ./...", null, false, List.of("${testWorkingDirectory}/test-results.xml"));
        String script = factoryWithPhases(List.of(phase)).verifyScriptContent(go);

        assertThat(script).contains("TEST_DEST=\"$BUILD_DIR/tests\"");
        assertThat(script).doesNotContain("cd ${testWorkingDirectory}").contains("cd tests");
    }

    @Test
    void verifyScript_materializesAssignmentAtConfiguredCheckoutPath() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setAssignmentCheckoutPath("student-code");
        exercise.setBuildConfig(buildConfig);

        String script = factoryWithPhases(List.of()).verifyScriptContent(exercise);

        assertThat(script).contains("ASSIGNMENT_DEST=\"$BUILD_DIR/student-code\"").contains("s#${studentWorkingDirectory}#/student-code/src#g")
                .contains("s#${studentParentWorkingDirectoryName}#student-code#g");
    }

    @Test
    void verifyScript_fallsBackToConventionalBuild_whenNoPhases() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        SandboxBuildCommandService factory = new SandboxBuildCommandService(Optional.empty(), Optional.empty());
        String script = factory.verifyScriptContent(exercise);
        assertThat(script).contains("mvn -B clean compile").contains("mvn -B test").contains("surefire-reports").contains("test-reports");
    }

    @Test
    void verifyScript_fallbackSupportsSequentialMavenHarness() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setProjectType(ProjectType.MAVEN_MAVEN);
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setSequentialTestRuns(true);
        exercise.setBuildConfig(buildConfig);

        String script = new SandboxBuildCommandService(Optional.empty(), Optional.empty()).verifyScriptContent(exercise);

        assertThat(script).contains("cd structural\nmvn -B clean compile", "cd behavior\nmvn -B clean compile", "cd structural\nmvn -B test", "cd behavior\nmvn -B test");
    }

    @Test
    void verifyScript_fallbackUsesTheGenericBuildDetector_forConfigurationsOutsideJavaMaven() {
        // LanguageGenerationProfile admits only Java/Maven, so a Java/Gradle exercise cannot reach the fallback in production; if one ever does, it gets generic best-effort
        // build detection rather than Gradle-specific commands.
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setProjectType(ProjectType.GRADLE_GRADLE);

        String script = new SandboxBuildCommandService(Optional.empty(), Optional.empty()).verifyScriptContent(exercise);

        assertThat(script).contains("if [ -f pom.xml ]; then mvn clean test;").contains("elif [ -f ./gradlew ]; then chmod +x ./gradlew && ./gradlew clean test --no-daemon;");
    }

    @Test
    void verifyScript_setsJavaSecurityManagerAllowForForkedJavaTestRunners() {
        ProgrammingExercise java = new ProgrammingExercise();
        java.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        String script = new SandboxBuildCommandService(Optional.empty(), Optional.empty()).verifyScriptContent(java);

        assertThat(script).contains("export JAVA_TOOL_OPTIONS=\"${JAVA_TOOL_OPTIONS:-} -Djava.security.manager=allow\"")
                .contains("export MAVEN_OPTS=\"${MAVEN_OPTS:-} -Djava.security.manager=allow\"").contains("export GRADLE_OPTS=\"${GRADLE_OPTS:-} -Djava.security.manager=allow\"");
    }

    @Test
    void pristineBuildCommands_targetTheVerifierOwnedScript() {
        SandboxBuildCommandService factory = new SandboxBuildCommandService(Optional.empty(), Optional.empty());
        // The verifier runs the pristine copy outside /workspace, which the agent's tools cannot reach.
        assertThat(factory.pristineSolutionBuildCommand()).isEqualTo("sh /opt/hyperion/verify.sh solution");
        assertThat(factory.pristineTemplateBuildCommand()).isEqualTo("sh /opt/hyperion/verify.sh template");
        assertThat(factory.behavioralSolutionBuildCommand()).isEqualTo("sh /opt/hyperion/verify.sh solution behavior-isolated");
        assertThat(factory.behavioralTemplateBuildCommand()).isEqualTo("sh /opt/hyperion/verify.sh template behavior-isolated");
        assertThat(factory.trustedStructuralSolutionBuildCommand()).isEqualTo("sh /opt/hyperion/verify.sh solution trusted-structural");
        assertThat(factory.trustedStructuralTemplateBuildCommand()).isEqualTo("sh /opt/hyperion/verify.sh template trusted-structural");
        assertThat(SandboxBuildCommandService.reportsDirectoryFor("solution")).isEqualTo("/opt/hyperion/reports/solution");
    }

    @Test
    void authoritativeJavaBuild_executesCompiledTestsWithoutAnyGeneratedSource() {
        ProgrammingExercise java = new ProgrammingExercise();
        java.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        String script = new SandboxBuildCommandService(Optional.empty(), Optional.empty()).verifyScriptContent(java);

        assertThat(script).contains("mvn -B clean compile", "mvn -B test-compile -DskipTests", "find \"$BUILD_DIR\" \"$WORKSPACE\" -type f -name '*.java' -delete",
                "mvn -B surefire:test");
        assertThat(script.indexOf("test-compile -DskipTests")).isLessThan(script.indexOf("-name '*.java' -delete"));
        assertThat(script.indexOf("-name '*.java' -delete")).isLessThan(script.indexOf("mvn -B surefire:test"));
    }

    @EnabledOnOs({ LINUX, MAC })
    @Test
    void authoritativeJavaBuild_removesGeneratedSourcesBeforeTheTestProcessStarts(@TempDir Path tempDir) throws Exception {
        Path workspace = Files.createDirectories(tempDir.resolve("workspace"));
        Path tests = Files.createDirectories(workspace.resolve("tests/test/example"));
        Path solution = Files.createDirectories(workspace.resolve("solution/src/example"));
        Files.createDirectories(workspace.resolve("template/src/example"));
        VerifyScriptTestHarness.writeString(workspace.resolve("tests/pom.xml"), "<project/>");
        VerifyScriptTestHarness.writeString(tests.resolve("SourceProbeTest.java"), "class SourceProbeTest {}");
        VerifyScriptTestHarness.writeString(solution.resolve("Answer.java"), "class Answer {}");

        Path fakeBin = Files.createDirectories(tempDir.resolve("bin"));
        Path fakeMaven = fakeBin.resolve("mvn");
        VerifyScriptTestHarness.writeString(fakeMaven, """
                #!/bin/sh
                case "$*" in
                  *test-compile*) mkdir -p target/test-classes; exit 0 ;;
                  *surefire:test*)
                    if find "$HYPERION_TEST_WORKSPACE" . -type f -name '*.java' -print -quit | grep -q .; then
                      echo "generated source remained visible to the test process" >&2
                      exit 91
                    fi
                    mkdir -p target/surefire-reports
                    printf '%s\n' '<testsuite name="Isolation" tests="1"><testcase name="sourceIsUnavailable"/></testsuite>' > target/surefire-reports/TEST-Isolation.xml
                    exit 0 ;;
                  *) exit 0 ;;
                esac
                """);
        assertThat(fakeMaven.toFile().setExecutable(true)).isTrue();

        ProgrammingExercise java = new ProgrammingExercise();
        java.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        String reports = tempDir.resolve("reports").toString();
        String script = new SandboxBuildCommandService(Optional.empty(), Optional.empty()).verifyScriptContent(java)
                .replace("WORKSPACE=\"/workspace\"", "WORKSPACE=\"" + workspace + "\"")
                .replace("REPORTS_DIR=\"/opt/hyperion/reports/$ASSIGNMENT\"", "REPORTS_DIR=\"" + reports + "/$ASSIGNMENT\"");
        Path scriptFile = tempDir.resolve("verify.sh");
        VerifyScriptTestHarness.writeString(scriptFile, script);

        ProcessBuilder processBuilder = new ProcessBuilder("sh", scriptFile.toString(), "solution", "behavior-isolated").redirectErrorStream(true);
        processBuilder.environment().put("PATH", fakeBin + ":" + processBuilder.environment().get("PATH"));
        processBuilder.environment().put("HYPERION_TEST_WORKSPACE", workspace.toString());
        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).as(output).isTrue();

        assertThat(process.exitValue()).as(output).isZero();
        assertThat(Files.exists(Path.of(reports, "solution", "0001__junit.xml"))).isTrue();
        try (var generatedSources = Files.find(workspace, Integer.MAX_VALUE, (path, attributes) -> attributes.isRegularFile() && path.toString().endsWith(".java"))) {
            assertThat(generatedSources).isEmpty();
        }
    }

    @EnabledOnOs({ LINUX, MAC })
    @Test
    void trustedStructuralLaneRunsOnlyTheServerBundleWithAssignmentSource(@TempDir Path tempDir) throws Exception {
        Path workspace = Files.createDirectories(tempDir.resolve("workspace"));
        Path candidateTests = Files.createDirectories(workspace.resolve("tests/test/example"));
        Path solution = Files.createDirectories(workspace.resolve("solution/src/example"));
        Files.createDirectories(workspace.resolve("template/src/example"));
        VerifyScriptTestHarness.writeString(workspace.resolve("tests/pom.xml"), "<project/>");
        VerifyScriptTestHarness.writeString(candidateTests.resolve("CandidateBehaviorTest.java"), "class CandidateBehaviorTest {}");
        VerifyScriptTestHarness.writeString(solution.resolve("Answer.java"), "class Answer {}");

        Path trusted = Files.createDirectories(tempDir.resolve("trusted/test/example"));
        VerifyScriptTestHarness.writeString(trusted.resolve("TrustedStructuralTest.java"), "class TrustedStructuralTest {}");
        Path fakeBin = Files.createDirectories(tempDir.resolve("bin"));
        Path fakeMaven = fakeBin.resolve("mvn");
        VerifyScriptTestHarness.writeString(fakeMaven, """
                #!/bin/sh
                case "$*" in
                  *test*)
                    if find . -type f -name 'CandidateBehaviorTest.java' -print -quit | grep -q .; then
                      echo "candidate behavior test entered the trusted lane" >&2
                      exit 91
                    fi
                    find . -type f -name 'TrustedStructuralTest.java' -print -quit | grep -q . || exit 92
                    find . -type f -name 'Answer.java' -print -quit | grep -q . || exit 93
                    mkdir -p target/surefire-reports
                    printf '%s\n' '<testsuite name="Structural" tests="1"><testcase name="testClass[Answer]"/></testsuite>' \
                      > target/surefire-reports/TEST-Structural.xml
                    exit 0 ;;
                  *) exit 0 ;;
                esac
                """);
        assertThat(fakeMaven.toFile().setExecutable(true)).isTrue();

        ProgrammingExercise java = new ProgrammingExercise();
        java.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        Path reports = tempDir.resolve("reports");
        String script = new SandboxBuildCommandService(Optional.empty(), Optional.empty()).verifyScriptContent(java)
                .replace("WORKSPACE=\"/workspace\"", "WORKSPACE=\"" + workspace + "\"")
                .replace("REPORTS_DIR=\"/opt/hyperion/reports/$ASSIGNMENT\"", "REPORTS_DIR=\"" + reports + "/$ASSIGNMENT\"")
                .replace(SandboxBuildCommandService.TRUSTED_STRUCTURAL_DIR, trusted.getParent().getParent().toString());
        Path scriptFile = tempDir.resolve("verify.sh");
        VerifyScriptTestHarness.writeString(scriptFile, script);

        ProcessBuilder processBuilder = new ProcessBuilder("sh", scriptFile.toString(), "solution", "trusted-structural").redirectErrorStream(true);
        processBuilder.environment().put("PATH", fakeBin + ":" + processBuilder.environment().get("PATH"));
        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).as(output).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        assertThat(Files.exists(reports.resolve("solution/0001__junit.xml"))).isTrue();
    }

    @Test
    void ordinaryAgentBuild_keepsTheProductionPhasesWhileIsolationIsExplicit() {
        BuildPhaseDTO phase = new BuildPhaseDTO("test", "echo exact-production-phase", null, false, List.of());
        String script = factoryWithPhases(List.of(phase)).verifyScriptContent(new ProgrammingExercise());

        assertThat(script).contains("if [ \"$LANE\" = \"behavior-isolated\" ]", "echo exact-production-phase");
        assertThat(new SandboxBuildCommandService(Optional.empty(), Optional.empty()).pristineSolutionBuildCommand()).doesNotContain("behavior-isolated");
    }

    @Test
    void authoritativeJavaBuild_derivesBothHalvesFromTheResolvedMavenPhase() {
        ProgrammingExercise java = new ProgrammingExercise();
        java.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        BuildPhaseDTO compile = new BuildPhaseDTO("compile", "mvn -B -Pcourse clean compile", null, false, List.of());
        BuildPhaseDTO test = new BuildPhaseDTO("test", "mvn -B -Pcourse test -Dstudent.profile=true", null, false, List.of());
        String script = factoryWithPhases(List.of(compile, test)).verifyScriptContent(java);

        assertThat(script).contains("mvn -B -Pcourse clean compile", "mvn -B -Pcourse test-compile -DskipTests -Dstudent.profile=true",
                "mvn -B -Pcourse surefire:test -Dstudent.profile=true");
    }

    @Test
    void authoritativeJavaBuild_preservesTheResolvedStaticAnalysisPhaseBeforeSourceRemoval() {
        ProgrammingExercise java = new ProgrammingExercise();
        java.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        java.setStaticCodeAnalysisEnabled(true);
        BuildPhaseDTO compile = new BuildPhaseDTO("compile", "mvn -B clean compile", null, false, List.of());
        BuildPhaseDTO test = new BuildPhaseDTO("test", "mvn -B test", null, false, List.of());
        String scaCommand = "mvn -B spotbugs:spotbugs checkstyle:checkstyle pmd:pmd pmd:cpd";
        BuildPhaseDTO sca = new BuildPhaseDTO("static_code_analysis", scaCommand, null, true, List.of());
        String script = factoryWithPhases(List.of(compile, test, sca)).verifyScriptContent(java);

        assertThat(script.split(Pattern.quote(scaCommand), -1)).hasSize(3);
        assertThat(script.indexOf("test-compile -DskipTests")).isLessThan(script.indexOf(scaCommand));
        assertThat(script.indexOf(scaCommand)).isLessThan(script.indexOf("-name '*.java' -delete"));
        assertThat(script.indexOf("-name '*.java' -delete")).isLessThan(script.indexOf("surefire:test"));
    }

    @Test
    void authoritativeJavaBuild_failsClosedWhenTheResolvedRecipeCannotBeSplitSafely() {
        ProgrammingExercise java = new ProgrammingExercise();
        java.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        BuildPhaseDTO combined = new BuildPhaseDTO("test", "mvn -B test && echo after", null, false, List.of());
        String script = factoryWithPhases(List.of(combined)).verifyScriptContent(java);

        assertThat(script).contains("Source-isolated verification requires a standalone Maven test phase", "exit 65");
    }

    @Test
    void authoritativeJavaBuild_rewritesOnlyTheLifecycleGoalNotAnOptionValue() {
        ProgrammingExercise java = new ProgrammingExercise();
        java.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        BuildPhaseDTO test = new BuildPhaseDTO("test", "mvn -B -Dstage=test test", null, false, List.of());
        String script = factoryWithPhases(List.of(test)).verifyScriptContent(java);

        assertThat(script).contains("mvn -B -Dstage=test test-compile -DskipTests", "mvn -B -Dstage=test surefire:test").doesNotContain("-Dstage=test-compile");
    }

    @Test
    void authoritativeJavaBuild_rejectsQuotedOptionValuesAndTrailingLifecycleGoals() {
        ProgrammingExercise java = new ProgrammingExercise();
        java.setProgrammingLanguage(ProgrammingLanguage.JAVA);

        String quoted = factoryWithPhases(List.of(new BuildPhaseDTO("test", "mvn -B -DargLine=\"unit test mode\" test", null, false, List.of()))).verifyScriptContent(java);
        String trailingGoal = factoryWithPhases(List.of(new BuildPhaseDTO("test", "mvn -B test verify", null, false, List.of()))).verifyScriptContent(java);

        assertThat(quoted).contains("Source-isolated verification requires a standalone Maven test phase", "exit 65");
        assertThat(trailingGoal).contains("Source-isolated verification requires a standalone Maven test phase", "exit 65");
    }

    @Test
    void verifyScript_isolatesTheCanonicalFixtureOnlyInThePristineReadinessVariant() {
        SandboxBuildCommandService service = new SandboxBuildCommandService(Optional.empty(), Optional.empty());
        ProgrammingExercise java = new ProgrammingExercise();
        java.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        String agentScript = service.verifyScriptContent(java);
        String readinessScript = service.readinessVerifyScriptContent(java);

        assertThat(agentScript).doesNotContain("hyperion-readiness-fixture", "[reference]");
        assertThat(readinessScript).contains("rm -rf \"$TEST_DEST/test\" \"$TEST_DEST/structural/test\" \"$TEST_DEST/behavior/test\" \"$ASSIGNMENT_DEST\"",
                "cp -a \"/opt/hyperion-readiness-fixture/tests/.\" \"$TEST_DEST\"/", "cp -a \"/opt/hyperion-readiness-fixture/solution/.\" \"$ASSIGNMENT_DEST\"/",
                "find \"/opt/hyperion-readiness-fixture\" -mindepth 1 -delete");
    }

    @Test
    void verifyScript_substitutesCiPlaceholdersInsideTheCopiedTestHarness_soTheAgentNeverNeedsToEditIt() {
        ProgrammingExercise java = new ProgrammingExercise();
        java.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        String script = new SandboxBuildCommandService(Optional.empty(), Optional.empty()).verifyScriptContent(java);
        assertThat(script).contains("s#${studentParentWorkingDirectoryName}#assignment#g").contains("s#${solutionWorkingDirectory}#assignment#g")
                .contains("s#${studentWorkingDirectory}#/assignment/src#g").contains("s#${testWorkingDirectory}#.#g");
    }

    @Test
    void verifyScript_removesTeamscalePluginOnlyFromTheDisposableOfflineBuildCopy() {
        ProgrammingExercise java = new ProgrammingExercise();
        java.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        String script = new SandboxBuildCommandService(Optional.empty(), Optional.empty()).verifyScriptContent(java);

        assertThat(script).contains("for build_file in \"$TEST_DEST/build.gradle\" \"$TEST_DEST/build.gradle.kts\"").contains("grep -vF \"id 'com.teamscale' version\"")
                .contains("grep -vF 'id(\"com.teamscale\") version'");
    }

    @EnabledOnOs({ LINUX, MAC })
    @Test
    void verifyScript_removesTeamscalePluginDeclarationsButKeepsOtherGradlePlugins(@TempDir Path tempDir) throws Exception {
        Path testsDir = Files.createDirectories(tempDir.resolve("tests"));
        Path groovyBuild = testsDir.resolve("build.gradle");
        Path kotlinBuild = testsDir.resolve("build.gradle.kts");
        VerifyScriptTestHarness.writeString(groovyBuild, "plugins {\n    id 'java'\n    id 'com.teamscale' version '34.2.1'\n}\n");
        VerifyScriptTestHarness.writeString(kotlinBuild, "plugins {\n    java\n    id(\"com.teamscale\") version \"34.2.1\"\n}\n");

        String fullScript = VerifyScriptTestHarness.verifyScript();
        String removal = VerifyScriptTestHarness.slice(fullScript, "for build_file in", "done");
        Path scriptFile = tempDir.resolve("remove-teamscale.sh");
        VerifyScriptTestHarness.writeString(scriptFile, "TEST_DEST='" + testsDir + "'\n" + removal + "\n");

        VerifyScriptTestHarness.runSh(scriptFile);

        assertThat(Files.readString(groovyBuild)).contains("id 'java'").doesNotContain("com.teamscale");
        assertThat(Files.readString(kotlinBuild)).contains("java").doesNotContain("com.teamscale");
    }

    /**
     * Drives the live placeholder-substitution stanza against a fixture Haskell {@code test.cabal} under a real {@code sh}, confirming raw {@code ${...}} placeholders resolve to
     * the assignment/ layout.
     */
    @Nested
    @EnabledOnOs({ LINUX, MAC })
    class HarnessPlaceholderSubstitution {

        @Test
        void substitutesSeededCabalPlaceholders_toTheAssignmentLayout(@TempDir Path tempDir) throws Exception {
            Path testDest = Files.createDirectories(tempDir.resolve("tests"));
            String seededCabal = """
                    library submission
                      hs-source-dirs: ${studentParentWorkingDirectoryName}/src
                    library solution
                      hs-source-dirs: ${solutionWorkingDirectory}/src
                    """;
            VerifyScriptTestHarness.writeString(testDest.resolve("test.cabal"), seededCabal);

            String stanza = substitutionStanza();
            String script = "TEST_DEST='" + testDest + "'\n" + stanza + "\n";
            Path scriptFile = tempDir.resolve("subst.sh");
            VerifyScriptTestHarness.writeString(scriptFile, script);
            Process process = new ProcessBuilder("sh", scriptFile.toString()).redirectErrorStream(true).start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("substitution stanza did not finish in time");
            }
            String produced = Files.readString(testDest.resolve("test.cabal"), StandardCharsets.UTF_8);
            assertThat(produced).doesNotContain("${").contains("hs-source-dirs: assignment/src");
            assertThat(produced.split("hs-source-dirs: assignment/src", -1)).hasSizeGreaterThanOrEqualTo(3);
        }

        private String substitutionStanza() {
            String fullScript = VerifyScriptTestHarness.verifyScript();
            return VerifyScriptTestHarness.slice(fullScript, "find \"$TEST_DEST\" -type f 2>/dev/null | while", "done");
        }
    }

    /**
     * Runs the live collect step against fixture JUnit XML under a real {@code sh} and feeds the result into the production {@code TestResultXmlParser}, proving the script
     * collects
     * exactly what the verifier parses and that the planted-report mitigation ({@code -newer} mtime gate) holds.
     */
    @Nested
    @EnabledOnOs({ LINUX, MAC })
    class ReportCollection {

        private SandboxBuildCommandService factory() {
            BuildPhasesTemplateService phasesService = mock(BuildPhasesTemplateService.class);
            when(phasesService.getDefaultBuildPlanPhasesFor(any())).thenReturn(List.of(new BuildPhaseDTO("test", "echo run", null, false, List.of())));
            return new SandboxBuildCommandService(Optional.of(phasesService), Optional.of(new BuildScriptProviderService()));
        }

        private static final String SUREFIRE = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="StackTest" tests="3" failures="1" errors="0" skipped="0">
                  <testcase name="stack_initially_empty" classname="StackTest"/>
                  <testcase name="push_then_pop" classname="StackTest"/>
                  <testcase name="size_tracks_elements" classname="StackTest"><failure message="x"/></testcase>
                </testsuite>
                """;

        @Test
        void collectsTheJUnitReport_andProductionParserSeesTheRightTests(@TempDir Path tempDir) throws Exception {
            Map<String, String> collected = VerifyScriptTestHarness.collect(factory(), new ProgrammingExercise(), tempDir, "junit",
                    Map.of("target/surefire-reports/TEST-StackTest.xml", SUREFIRE));
            assertThat(collected).hasSize(1);
            String collectedXml = collected.values().iterator().next();
            assertThat(collected.keySet().iterator().next()).endsWith(SandboxBuildCommandService.COLLECTED_NAME_SEPARATOR + SandboxBuildCommandService.COLLECTED_JUNIT_TOKEN);

            // The production parser sees exactly the two passing + one failing testcase.
            List<LocalCITestJobDTO> failed = new ArrayList<>();
            List<LocalCITestJobDTO> ok = new ArrayList<>();
            TestResultXmlParser.processTestResultFile(collectedXml, failed, ok);
            assertThat(ok.stream().map(LocalCITestJobDTO::name)).containsExactlyInAnyOrder("stack_initially_empty", "push_then_pop");
            assertThat(failed.stream().map(LocalCITestJobDTO::name)).containsExactly("size_tracks_elements");
        }

        @Test
        void collectsJUnitReportWithWhitespaceInPath(@TempDir Path tempDir) throws Exception {
            Map<String, String> collected = VerifyScriptTestHarness.collect(factory(), new ProgrammingExercise(), tempDir, "junit spaces",
                    Map.of("target/surefire-reports/TEST StackTest.xml", SUREFIRE));

            assertThat(collected).hasSize(1);
            assertThat(collected.keySet().iterator().next()).endsWith(SandboxBuildCommandService.COLLECTED_NAME_SEPARATOR + SandboxBuildCommandService.COLLECTED_JUNIT_TOKEN);
        }

        @Test
        void collectsMavenReportsFromTheDefaultSurefireLocation_forJavaExercises(@TempDir Path tempDir) throws Exception {
            // Surefire always writes to its default ${project.build.directory}/surefire-reports regardless of any -D flag, so the collect snippet must find the report there,
            // directly under $BUILD_DIR, exactly like every other language.
            ProgrammingExercise java = new ProgrammingExercise();
            java.setProgrammingLanguage(ProgrammingLanguage.JAVA);
            Map<String, String> collected = VerifyScriptTestHarness.collect(factory(), java, tempDir, "java-maven", Map.of("target/surefire-reports/TEST-StackTest.xml", SUREFIRE));

            assertThat(collected).hasSize(1);
            assertThat(collected.keySet().iterator().next()).endsWith(SandboxBuildCommandService.COLLECTED_NAME_SEPARATOR + SandboxBuildCommandService.COLLECTED_JUNIT_TOKEN);
            String collectedXml = collected.values().iterator().next();
            List<LocalCITestJobDTO> failed = new ArrayList<>();
            List<LocalCITestJobDTO> ok = new ArrayList<>();
            TestResultXmlParser.processTestResultFile(collectedXml, failed, ok);
            assertThat(ok.stream().map(LocalCITestJobDTO::name)).containsExactlyInAnyOrder("stack_initially_empty", "push_then_pop");
            assertThat(failed.stream().map(LocalCITestJobDTO::name)).containsExactly("size_tracks_elements");
        }

        @Test
        void doesNotCollectAReportPlantedBeforeTheBuildStart(@TempDir Path tempDir) throws Exception {
            // A report whose mtime predates the build-start marker must NOT be collected (anti-forgery): pre-write it and back-date it.
            Path buildDir = Files.createDirectories(tempDir.resolve("planted").resolve("build"));
            Path reportsDir = tempDir.resolve("planted").resolve("reports-root").resolve("solution");
            Path marker = buildDir.resolve(".hyperion-build-start");
            VerifyScriptTestHarness.writeString(marker, "");
            Path reportFile = buildDir.resolve("surefire-reports").resolve("planted.xml");
            VerifyScriptTestHarness.writeString(reportFile, SUREFIRE);
            Files.setLastModifiedTime(reportFile, FileTime.from(Instant.now().minusSeconds(7200))); // older than the marker
            Files.setLastModifiedTime(marker, FileTime.from(Instant.now().minusSeconds(3600)));

            String fullScript = factory().verifyScriptContent(new ProgrammingExercise());
            String collectSnippet = VerifyScriptTestHarness.slice(fullScript, "rm -rf \"$REPORTS_DIR\"", "echo \"" + SandboxBuildCommandService.COLLECTED_MARKER);
            String script = "BUILD_DIR='" + buildDir + "'\nBUILD_START_MARKER='" + marker + "'\nREPORTS_DIR='" + reportsDir + "'\nrc=0\nseq=0\n" + collectSnippet + "\n";
            Path scriptFile = tempDir.resolve("planted-collect.sh");
            VerifyScriptTestHarness.writeString(scriptFile, script);
            VerifyScriptTestHarness.runSh(scriptFile);

            assertThat(Files.isDirectory(reportsDir) ? Files.list(reportsDir).count() : 0L).as("a stale planted report is not collected").isZero();
        }
    }

    /**
     * The static-code-analysis collection. SCA disabled: no SCA reports collected; SCA enabled: each tool's canonical report collected by name (keeping the name so the verifier's
     * production {@code ReportParser} routes it). The live tests slice the collect block out of the generated script and run it under a real {@code sh}.
     */
    @Nested
    class StaticCodeAnalysisCollection {

        private SandboxBuildCommandService factory() {
            BuildPhasesTemplateService phasesService = mock(BuildPhasesTemplateService.class);
            when(phasesService.getDefaultBuildPlanPhasesFor(any())).thenReturn(List.of(new BuildPhaseDTO("build", "echo build", null, false, List.of())));
            return new SandboxBuildCommandService(Optional.of(phasesService), Optional.of(new BuildScriptProviderService()));
        }

        private ProgrammingExercise exercise(ProgrammingLanguage language, boolean scaEnabled) {
            ProgrammingExercise exercise = new ProgrammingExercise();
            exercise.setProgrammingLanguage(language);
            exercise.setStaticCodeAnalysisEnabled(scaEnabled);
            return exercise;
        }

        @Test
        void scaDisabled_collectsNoScaReports() {
            String script = factory().verifyScriptContent(exercise(ProgrammingLanguage.JAVA, false));
            assertThat(script).doesNotContain("spotbugsXml.xml").doesNotContain("collected_sca=$((collected_sca + 1))");
        }

        @Test
        void scaEnabled_collectsTheLanguageToolReports() {
            String script = factory().verifyScriptContent(exercise(ProgrammingLanguage.JAVA, true));
            // SpotBugs/Checkstyle/PMD/CPD by canonical name, build-fresh only.
            assertThat(script).contains("-name 'spotbugsXml.xml'").contains("-name 'checkstyle-result.xml'").contains("-name 'pmd.xml'").contains("-name 'cpd.xml'")
                    .contains("-newer \"$BUILD_START_MARKER\"").contains("collected_sca=$((collected_sca + 1))");
        }

        @Test
        void scaEnabled_python_collectsRuffSarif() {
            String script = factory().verifyScriptContent(exercise(ProgrammingLanguage.PYTHON, true));
            assertThat(script).contains("-name 'ruff.sarif'");
        }

        @EnabledOnOs({ LINUX, MAC })
        @Test
        void scriptIsValidPosixShell_forEveryScaCapableLanguage(@TempDir Path tempDir) throws Exception {
            for (ProgrammingLanguage language : List.of(ProgrammingLanguage.C, ProgrammingLanguage.C_PLUS_PLUS, ProgrammingLanguage.DART, ProgrammingLanguage.JAVA,
                    ProgrammingLanguage.JAVASCRIPT, ProgrammingLanguage.PYTHON, ProgrammingLanguage.R, ProgrammingLanguage.RUBY, ProgrammingLanguage.RUST,
                    ProgrammingLanguage.TYPESCRIPT)) {
                String fullScript = factory().verifyScriptContent(exercise(language, true));
                Path scriptFile = tempDir.resolve("full-" + language.name().toLowerCase() + ".sh");
                VerifyScriptTestHarness.writeString(scriptFile, fullScript);
                Process process = new ProcessBuilder("sh", "-n", scriptFile.toString()).redirectErrorStream(true).start();
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (!process.waitFor(30, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    throw new IllegalStateException("sh -n did not finish in time for " + language);
                }
                assertThat(process.exitValue()).as("the generated script for %s is valid POSIX sh (sh -n: %s)", language, output).isZero();
            }
        }

        @EnabledOnOs({ LINUX, MAC })
        @Test
        void liveCollect_collectsSpotbugsAndCheckstyleReports_forProductionParsing(@TempDir Path tempDir) throws Exception {
            Map<String, String> collected = VerifyScriptTestHarness.collect(factory(), exercise(ProgrammingLanguage.JAVA, true), tempDir, "java-sca",
                    Map.of("test-results/results.xml", "<testsuite name=\"T\" tests=\"0\"/>", "spotbugsXml.xml", """
                            <?xml version="1.0" encoding="UTF-8"?>
                            <BugCollection version="4.7.3"><BugInstance type="DM_DEFAULT_ENCODING" category="STYLE"><SourceLine start="12" end="12"/></BugInstance></BugCollection>
                            """, "checkstyle-result.xml", """
                            <?xml version="1.0" encoding="UTF-8"?>
                            <checkstyle version="10.3"><file name="Stack.java">
                              <error line="3" severity="warning" message="x" source="com.puppycrawl.tools.checkstyle.checks.javadoc.JavadocTypeCheck"/></file></checkstyle>
                            """));
            // Both SCA reports are collected under their canonical names so the verifier's production ReportParser can route them.
            assertThat(collected.keySet()).anyMatch(n -> n.endsWith("spotbugsXml.xml")).anyMatch(n -> n.endsWith("checkstyle-result.xml"));
        }
    }
}
