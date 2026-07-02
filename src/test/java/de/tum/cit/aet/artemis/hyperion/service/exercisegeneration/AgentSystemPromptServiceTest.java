package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRequestDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

/**
 * Unit test for the agent system prompt's spec-mode branching and the per-language generation profiles, especially the [task]-binding identifier each profile must encode (it
 * differs
 * sharply per test framework).
 */
class AgentSystemPromptServiceTest {

    // No LocalCI services -> the generic build fallback, enough to assert the build-context section renders.
    private final AgentSystemPromptService systemPromptService = new AgentSystemPromptService(new SandboxBuildCommandService(Optional.empty(), Optional.empty()));

    /** Marker phrase only present in the spec-mode default instruction. */
    private static final String SPEC_MODE_MARKER = "authoritative specification";

    /** Marker phrase only present in the from-scratch default instruction. */
    private static final String FROM_SCRATCH_MARKER = "Generate a complete, correct programming exercise";

    private static ProgrammingExercise exerciseWithStatement(String problemStatement) {
        return exerciseWith(ProgrammingLanguage.JAVA, problemStatement);
    }

    private static ProgrammingExercise exerciseWith(ProgrammingLanguage language, String problemStatement) {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(language);
        exercise.setProblemStatement(problemStatement);
        return exercise;
    }

    private static String profile(ProgrammingLanguage language) {
        return LanguageGenerationProfile.guidanceFor(exerciseWith(language, ""));
    }

    @Test
    void build_injectsBuildContext_withPhasesReportsAndScaState() {
        ProgrammingExercise exercise = exerciseWith(ProgrammingLanguage.JAVA, "");

        String prompt = systemPromptService.build(exercise);

        assertThat(prompt).contains("THIS EXERCISE'S BUILD CONTEXT");
        assertThat(prompt).contains("Build phases (run in order");
        assertThat(prompt).contains("Test reports the grader reads");
        // A default report glob proves the glob list is rendered, not just the header.
        assertThat(prompt).contains("surefire-reports/*.xml");
        assertThat(prompt).doesNotContain("Static code analysis is ON");
        exercise.setStaticCodeAnalysisEnabled(true);
        assertThat(systemPromptService.build(exercise)).contains("Static code analysis is ON");
    }

    @Test
    void isNonTrivialProblemStatement_distinguishesRealStatementsFromEmptyOrPlaceholder() {
        assertThat(systemPromptService.isNonTrivialProblemStatement(null)).isFalse();
        assertThat(systemPromptService.isNonTrivialProblemStatement("")).isFalse();
        assertThat(systemPromptService.isNonTrivialProblemStatement("   \n  ")).isFalse();
        assertThat(systemPromptService.isNonTrivialProblemStatement("# TODO")).isFalse();
        assertThat(systemPromptService.isNonTrivialProblemStatement("Implement a stack with push, pop and peek operations for integers.")).isTrue();
    }

    // resolvePrompt: an explicit prompt wins; otherwise the default is mode-aware (spec mode when a non-trivial statement is present, from-scratch otherwise).

    @Test
    void resolvePrompt_explicitPrompt_fromScratch_isHonouredVerbatim() {
        // No reviewed spec (empty statement) -> the brief is the whole instruction.
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO("Make it about graph traversal.");
        ProgrammingExercise exercise = exerciseWithStatement("");

        String prompt = systemPromptService.resolvePrompt(request, exercise);

        assertThat(prompt).isEqualTo("Make it about graph traversal.");
    }

    @Test
    void resolvePrompt_briefWithSpec_isAuthoritativeButKeepsTheStatementWhereSilent() {
        // A statement on one topic plus a brief that changes it: the brief governs (so an adaptation can change the task) while the existing statement is still referenced as the
        // starting point, never discarded into a bare from-scratch run.
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO("Make it about graph traversal.");
        ProgrammingExercise exercise = exerciseWithStatement("Implement a stack with push, pop and peek operations for integers.");

        String prompt = systemPromptService.resolvePrompt(request, exercise);

        assertThat(prompt).contains("Make it about graph traversal.").contains("current problem statement").contains("may refine that statement or change the task")
                .doesNotContain(FROM_SCRATCH_MARKER);
        assertThat(prompt).isNotEqualTo("Make it about graph traversal.");
    }

    @Test
    void resolvePrompt_blankPrompt_fallsBackToModeAwareDefault() {
        // A whitespace-only prompt is treated as "no prompt".
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO("   \n  ");
        ProgrammingExercise exercise = exerciseWithStatement("");

        String prompt = systemPromptService.resolvePrompt(request, exercise);

        assertThat(prompt).contains(FROM_SCRATCH_MARKER).doesNotContain(SPEC_MODE_MARKER);
    }

    @Test
    void resolvePrompt_noPrompt_specMode_whenNonTrivialProblemStatement() {
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(null);
        ProgrammingExercise exercise = exerciseWithStatement("Implement an LRU cache with get/put returning -1 on a miss and evicting the least recently used key.");

        String prompt = systemPromptService.resolvePrompt(request, exercise);

        assertThat(prompt).contains(SPEC_MODE_MARKER).doesNotContain(FROM_SCRATCH_MARKER);
    }

    @Test
    void resolvePrompt_noPrompt_boundary_atNonTrivialThreshold() {
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO(null);
        // The threshold is 40 stripped chars. 39 chars -> trivial (from-scratch); 40 chars -> non-trivial (spec mode).
        String just39 = "a".repeat(39);
        String exactly40 = "a".repeat(40);

        String below = systemPromptService.resolvePrompt(request, exerciseWithStatement(just39));
        String atThreshold = systemPromptService.resolvePrompt(request, exerciseWithStatement(exactly40));

        assertThat(below).contains(FROM_SCRATCH_MARKER).doesNotContain(SPEC_MODE_MARKER);
        assertThat(atThreshold).contains(SPEC_MODE_MARKER).doesNotContain(FROM_SCRATCH_MARKER);
    }

    @Test
    void build_specMode_whenStatementPresent_buildsToMatchItButLetsTheBriefChangeTheTask() {
        // A present statement selects spec mode: the agent matches the current statement, but the brief may refine or change it, so the statement is the starting point, not a
        // lock.
        String prompt = systemPromptService.build(exerciseWithStatement("Implement an LRU cache with get/put returning -1 on a miss and evicting the least recently used key."));
        assertThat(prompt).contains("CURRENT problem statement and the starting point").contains("may refine this statement or change the task").doesNotContain("you write it");
    }

    @Test
    void build_fromScratch_whenStatementEmpty_tellsAgentToAuthorIt() {
        // An empty statement selects from-scratch mode.
        String prompt = systemPromptService.build(exerciseWithStatement(""));
        assertThat(prompt).contains("you write it").doesNotContain(SPEC_MODE_MARKER);
    }

    @Test
    void build_scaDisabled_omitsStaticCodeAnalysisGuidance() {
        String prompt = systemPromptService.build(exerciseWith(ProgrammingLanguage.JAVA, ""));
        assertThat(prompt).doesNotContain("STATIC CODE ANALYSIS IS ENABLED");
    }

    @Test
    void build_scaEnabled_tellsAgentTheSolutionMustBeScaClean() {
        ProgrammingExercise exercise = exerciseWith(ProgrammingLanguage.JAVA, "");
        exercise.setStaticCodeAnalysisEnabled(true);
        String prompt = systemPromptService.build(exercise);
        // The non-obvious instruction: only the solution must be lint-clean, not the template.
        assertThat(prompt).contains("STATIC CODE ANALYSIS IS ENABLED").contains("template need not be lint-clean");
    }

    @Test
    void build_taskBindingGuidance_isFrameworkAwareAndJvmProfileBindsToMethodNamesWithAresAnnotations() {
        String prompt = systemPromptService.build(exerciseWith(ProgrammingLanguage.JAVA, ""));
        // Generic section points at the language profile; the JVM profile binds to method names and carries the Ares annotations.
        assertThat(prompt).contains("test identifiers EXACTLY as this language's test runner").contains("the test METHOD name").contains("@WhitelistPath(\"target\")")
                .contains("@BlacklistPath(\"target/test-classes\")");
    }

    @Test
    void build_requiresStudentFacingTestFeedbackAndNonDegenerateWitnessTests() {
        String prompt = systemPromptService.build(exerciseWith(ProgrammingLanguage.JAVA, ""));
        assertThat(prompt).contains("human-readable failure message").contains("NON-DEGENERATE");
        // A @DisplayName would break the [task]<->method-name binding, so the prompt must forbid a display title, not require one.
        assertThat(prompt).contains("do NOT rename the test or add a display title").doesNotContain("Give each JVM test a @DisplayName");
    }

    @Test
    void build_forbidsTypographyInSourceCodeAndExampleReproductionOfGradedInputs() {
        String prompt = systemPromptService.build(exerciseWith(ProgrammingLanguage.JAVA, ""));
        assertThat(prompt).contains("all authored SOURCE CODE").contains("exception message").contains("NEVER reproduce a graded test's exact composite input");
    }

    @Test
    void build_enumeratesTheRealToolSurfaceAndForbidsApplyPatch() {
        // The agent hallucinated an apply_patch tool (and a silent bash apply_patch no-op corrupted its file-state model); the prompt must name the exact tool set and rule out
        // apply_patch in both tool-call and bash forms.
        String prompt = systemPromptService.build(exerciseWith(ProgrammingLanguage.JAVA, ""));
        assertThat(prompt).contains("Your ONLY tools are bash, read_file, write_file, edit_file, verify, and submit").contains("NO apply_patch tool")
                .contains("Never call apply_patch").contains("do NOT re-read a file whose contents you have already seen");
    }

    @Test
    void build_makesVerifyThePrimarySelfCheckAndTheAuthoritativeSourceOfTestNames() {
        // The `verify` tool, not the per-language naming rules, is the authoritative source of the exact parser-form names the agent must copy verbatim.
        String prompt = systemPromptService.build(exerciseWith(ProgrammingLanguage.DART, ""));
        assertThat(prompt).contains("PRIMARY self-check").contains("VERBATIM");
    }

    @Test
    void build_encodesAPlusQualityRules() {
        // The two rules that map to real verifier gates: test every promised contract, and clean up unused dependencies.
        String prompt = systemPromptService.build(exerciseWith(ProgrammingLanguage.JAVA, ""));
        assertThat(prompt).contains("EVERY promise you make").contains("unused dependencies");
    }

    @Test
    void build_encodesProblemStatementQualityRules() {
        // Each token pins one rubric-graded authoring contract a real generation defect required. Single varargs assertion so a failure names every missing token, not just the
        // first.
        String prompt = systemPromptService.build(exerciseWith(ProgrammingLanguage.JAVA, ""));
        assertThat(prompt).contains("WORKED EXAMPLE", "STUDENT-FACING ONLY", "build-gate", "INPUT DOMAIN", "EXACT five-character singular keyword `[task]`", "within 1e-6",
                "Do NOT state any complexity", "Design note (not graded)", "exact equality", "CONCRETE FENCED trace");
    }

    @Test
    void build_forbidsStatingDomainGuaranteesTheSolutionDoesNotEnforce() {
        // The model once invented a "NaN throws" rule a `< 0` guard does not enforce in IEEE-754; every domain/error guarantee must be grounded in what the reference solution
        // does.
        String prompt = systemPromptService.build(exerciseWith(ProgrammingLanguage.JAVA, ""));
        assertThat(prompt).contains("the REFERENCE SOLUTION actually enforces").contains("does NOT reject NaN");
    }

    @Test
    void build_forbidsAddingOrStrippingParensOnTestNames() {
        // The binding resolves by exact string match, so adding/removing the () on a name silently grades it 0.
        String prompt = systemPromptService.build(exerciseWith(ProgrammingLanguage.JAVA, ""));
        assertThat(prompt).contains("do NOT add, remove").contains("grades it 0");
    }

    @Test
    void build_specMode_instructsToStripMetaNotesAndMeetQualityBar() {
        // A placeholder statement once carried internal notes the agent kept; spec mode must tell it to delete them and lift the statement to the quality bar.
        String prompt = systemPromptService.build(exerciseWith(ProgrammingLanguage.JAVA, "Implement a bounded integer stack with push, pop, peek and a fixed capacity."));
        assertThat(prompt).contains("DELETE any internal/meta notes").contains("PROBLEM STATEMENT QUALITY");
    }

    @Test
    void cppProfile_enforcesByteIdenticalHeadersViaDiff() {
        // The shared header is the contract; the C++ profile must make the agent prove identity (diff prints nothing) rather than merely intend it.
        assertThat(profile(ProgrammingLanguage.C_PLUS_PLUS)).contains("diff -r solution/include template/include").contains("byte-identical");
    }

    @Test
    void cSharpProfile_pinsNet8AndUsesThrowingStub() {
        // Audit found the agent downgraded the solution to net6 (sandbox-invisible prod break) and the profile's "empty stub" was uncompilable (CS0161).
        String guidance = profile(ProgrammingLanguage.C_SHARP);
        assertThat(guidance).contains("net8.0").contains("throw new NotImplementedException();").contains("CS0161");
    }

    @Test
    void rustProfile_prunesTheDeadStructuralHarnessAndUnusedDeps() {
        // Audit found Rust shipped a dead syn/proc-macro harness + unused chrono/rand deps; the profile must name the prune + a grep self-check.
        String guidance = profile(ProgrammingLanguage.RUST);
        assertThat(guidance).contains("rust_template_test_macros").contains("grep -rn");
    }

    @Test
    void build_mandatesStudentFacingTemplateAndCoverageSelfCheck() {
        String prompt = systemPromptService.build(exerciseWith(ProgrammingLanguage.JAVA, ""));
        assertThat(prompt).contains("scratchpad").contains("re-read your tests");
    }

    @Test
    void build_kotlin_instructsToWriteEverythingInKotlin() {
        String prompt = systemPromptService.build(exerciseWith(ProgrammingLanguage.KOTLIN, ""));
        assertThat(prompt).contains("KOTLIN exercise").contains(".kt").contains("do NOT write any .java");
    }

    /** Languages present in the enum but with no Artemis exercise templates / not creatable in this deployment — they correctly get no generation profile. */
    private static final java.util.Set<ProgrammingLanguage> LANGUAGES_WITHOUT_TEMPLATES = java.util.EnumSet.of(ProgrammingLanguage.EMPTY, ProgrammingLanguage.SQL,
            ProgrammingLanguage.POWERSHELL, ProgrammingLanguage.ADA, ProgrammingLanguage.PHP);

    @ParameterizedTest
    @EnumSource(ProgrammingLanguage.class)
    void everySupportedLanguageHasGenerationGuidanceThatExplainsTaskBindings(ProgrammingLanguage language) {
        String guidance = profile(language);
        if (LANGUAGES_WITHOUT_TEMPLATES.contains(language)) {
            assertThat(guidance).as("no profile for the untemplated language %s", language).isEmpty();
            return;
        }
        assertThat(guidance).as("guidance for %s", language).isNotBlank();
        assertThat(guidance).as("%s profile explains [task] bindings", language).contains("[task]");
    }

    @Test
    void profiles_encodeEachFrameworksDivergentTaskNamingRule() {
        // The per-framework naming rules the agent most often gets wrong.
        assertThat(profile(ProgrammingLanguage.PYTHON)).contains("test METHOD name");
        assertThat(profile(ProgrammingLanguage.JAVASCRIPT)).contains("underscore");
        assertThat(profile(ProgrammingLanguage.TYPESCRIPT)).contains("underscore");
        assertThat(profile(ProgrammingLanguage.RUBY)).contains("test METHOD name");
        assertThat(profile(ProgrammingLanguage.R)).contains("DESCRIPTION STRING");
        // Dart: the package:test name is the space-joined group+test; production prepends the dot test-FILE suite prefix only with >=2 files, dropping it for a single file (the
        // common case). The profile must teach this singular-suite exception, or single-file names bind to nothing in production.
        assertThat(profile(ProgrammingLanguage.DART)).contains("joined by SINGLE SPACES").contains("reverseString reverse_non_empty").contains("DROPPED")
                .contains("TWO OR MORE test files");
        assertThat(profile(ProgrammingLanguage.GO)).contains("TestXxx");
        assertThat(profile(ProgrammingLanguage.RUST)).contains("#[test] fn");
        // Catch2 reports a nested SECTION as the slash-joined TEST_CASE/SECTION path.
        assertThat(profile(ProgrammingLanguage.C_PLUS_PLUS)).contains("TEST_CASE").contains("SLASH-joined");
        assertThat(profile(ProgrammingLanguage.C_SHARP)).contains("[Test]");
        assertThat(profile(ProgrammingLanguage.SWIFT)).contains("allTests");
        assertThat(profile(ProgrammingLanguage.HASKELL)).contains("DOT-JOINED");
        assertThat(profile(ProgrammingLanguage.BASH)).contains("@test");
    }

    @Test
    void cProfile_dependsOnProjectType() {
        ProgrammingExercise gcc = exerciseWith(ProgrammingLanguage.C, "");
        gcc.setProjectType(ProjectType.GCC);
        assertThat(LanguageGenerationProfile.guidanceFor(gcc)).contains("gcc project type");

        ProgrammingExercise fact = exerciseWith(ProgrammingLanguage.C, "");
        fact.setProjectType(ProjectType.FACT);
        assertThat(LanguageGenerationProfile.guidanceFor(fact)).contains("fact project type");
    }

    @Test
    void javaProfile_dependsOnProjectType_blackboxUsesDejagnuNotAres() {
        // MAVEN_BLACKBOX is a DejaGnu (stdin/stdout) harness with no JUnit tests, so its guidance teaches the black-box specifics and the dejagnu[<step>] binding, not Ares/JUnit.
        ProgrammingExercise blackbox = exerciseWith(ProgrammingLanguage.JAVA, "");
        blackbox.setProjectType(ProjectType.MAVEN_BLACKBOX);
        String blackboxGuidance = LanguageGenerationProfile.guidanceFor(blackbox);
        assertThat(blackboxGuidance).contains("MAVEN_BLACKBOX").contains("DejaGnu").contains("dejagnu[public]").contains("dejagnu[advanced]").contains("dejagnu[secret]")
                .contains("Tests.txt").contains("STDIN").contains("[task]");
        // Assert on the JUnit profile's instructional phrases, not bare tokens (the black-box text may name @Public/@StrictTimeout only to forbid them).
        assertThat(blackboxGuidance).doesNotContain("@WhitelistPath(\"target\")").doesNotContain("@BlacklistPath(\"target/test-classes\")")
                .doesNotContain("every test class MUST be").doesNotContain("The [task] binding uses the test METHOD name");

        // A normal Java project type and the null default both keep the JUnit/Ares jvm() profile.
        ProgrammingExercise plainMaven = exerciseWith(ProgrammingLanguage.JAVA, "");
        plainMaven.setProjectType(ProjectType.PLAIN_MAVEN);
        assertThat(LanguageGenerationProfile.guidanceFor(plainMaven)).contains("@Public").contains("@StrictTimeout");
        assertThat(profile(ProgrammingLanguage.JAVA)).contains("@Public").contains("@StrictTimeout");
    }

    // The single server source of truth for the one-click whole-exercise generation offer.

    @Test
    void supportedGenerationLanguages_pinsTheOracleVerifiableSet() {
        // Pin the exact set so server drift (the source of truth the client consumes) is caught.
        assertThat(systemPromptService.supportedGenerationLanguages()).containsExactlyInAnyOrder(ProgrammingLanguage.JAVA, ProgrammingLanguage.KOTLIN, ProgrammingLanguage.PYTHON,
                ProgrammingLanguage.JAVASCRIPT, ProgrammingLanguage.TYPESCRIPT, ProgrammingLanguage.GO, ProgrammingLanguage.RUST, ProgrammingLanguage.C_PLUS_PLUS,
                ProgrammingLanguage.C_SHARP, ProgrammingLanguage.DART, ProgrammingLanguage.RUBY, ProgrammingLanguage.R, ProgrammingLanguage.HASKELL, ProgrammingLanguage.SWIFT);
    }

    @Test
    void isGenerationSupported_acceptsOfferSet_rejectsBestEffortAndUntemplated() {
        // 3 representative arms; the exact offer set is already pinned above.
        assertThat(systemPromptService.isGenerationSupported(ProgrammingLanguage.JAVA)).as("offered").isTrue();
        assertThat(systemPromptService.isGenerationSupported(ProgrammingLanguage.OCAML)).as("best-effort excluded").isFalse();
        assertThat(systemPromptService.isGenerationSupported(ProgrammingLanguage.SQL)).as("untemplated excluded").isFalse();
    }

    @Test
    void isGenerationSupported_rejectsNullLanguage() {
        assertThat(systemPromptService.isGenerationSupported(null)).isFalse();
    }
}
