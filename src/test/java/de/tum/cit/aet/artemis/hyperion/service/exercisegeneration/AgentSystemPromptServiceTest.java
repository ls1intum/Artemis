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
 * Unit test for the agent system prompt's spec-mode branching and the per-language generation profiles. The most error-prone, language-divergent thing a profile must encode is the
 * [task]-binding identifier (it differs sharply per test framework), so the profile coverage is asserted here without a live build.
 */
class AgentSystemPromptServiceTest {

    // No LocalCI services -> describeBuildContext resolves the generic build fallback, which is enough to assert the build-context section renders its phases/reports/SCA
    // structure.
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
        // A default report glob is joined into the section (proves the glob list is actually rendered, not just the header).
        assertThat(prompt).contains("surefire-reports/*.xml");
        // SCA is off by default, so its clause is absent; enabling it makes the clause appear.
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

    // ---- resolvePrompt: an explicit prompt always wins; otherwise the default is mode-aware (spec mode when a non-trivial statement is present, from-scratch otherwise)
    // ----------

    @Test
    void resolvePrompt_explicitPrompt_fromScratch_isHonouredVerbatim() {
        // No reviewed spec yet (empty statement) -> the brief is the whole instruction (the lean AI create flow).
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO("Make it about graph traversal.");
        ProgrammingExercise exercise = exerciseWithStatement("");

        String prompt = systemPromptService.resolvePrompt(request, exercise);

        assertThat(prompt).isEqualTo("Make it about graph traversal.");
    }

    @Test
    void resolvePrompt_briefIsSubordinateToAReviewedSpec_soThePlanReviewBinds() {
        // A reviewed problem statement exists AND a brief is supplied (the staged "draft a plan -> Generate" flow, where the create form always threads the brief). The reviewed
        // statement
        // must stay authoritative; the brief is applied as a refinement, never returned alone (the old behaviour that made plan review non-binding).
        ExerciseGenerationRequestDTO request = new ExerciseGenerationRequestDTO("Make it about graph traversal.");
        ProgrammingExercise exercise = exerciseWithStatement("Implement a stack with push, pop and peek operations for integers.");

        String prompt = systemPromptService.resolvePrompt(request, exercise);

        assertThat(prompt).contains(SPEC_MODE_MARKER).contains("Make it about graph traversal.").doesNotContain(FROM_SCRATCH_MARKER);
        assertThat(prompt).isNotEqualTo("Make it about graph traversal.");
    }

    @Test
    void resolvePrompt_blankPrompt_fallsBackToModeAwareDefault() {
        // A blank (whitespace-only) prompt must be treated as "no prompt" and fall back to the from-scratch default for an empty exercise.
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
    void build_specMode_whenStatementPresent_tellsAgentToMatchIt() {
        // Behavioural contract: a present statement selects spec mode (not from-scratch). Pin via the stable spec-mode marker, not the exact prose.
        String prompt = systemPromptService.build(exerciseWithStatement("Implement an LRU cache with get/put returning -1 on a miss and evicting the least recently used key."));
        assertThat(prompt).contains(SPEC_MODE_MARKER).doesNotContain("you write it");
    }

    @Test
    void build_fromScratch_whenStatementEmpty_tellsAgentToAuthorIt() {
        // Behavioural contract: an empty statement selects from-scratch mode. Pin via the stable "you write it" token, not the exact prose.
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
        // Pin the branch token plus the one non-obvious instruction nothing else covers: the template need not be lint-clean (only the solution must).
        assertThat(prompt).contains("STATIC CODE ANALYSIS IS ENABLED").contains("template need not be lint-clean");
    }

    @Test
    void build_taskBindingGuidance_isFrameworkAwareAndJvmProfileBindsToMethodNamesWithAresAnnotations() {
        String prompt = systemPromptService.build(exerciseWith(ProgrammingLanguage.JAVA, ""));
        // The generic section must point at the language profile for the exact identifier, and the JVM profile must bind to method names and carry the Ares security annotations.
        assertThat(prompt).contains("test identifiers EXACTLY as this language's test runner").contains("the test METHOD name").contains("@WhitelistPath(\"target\")")
                .contains("@BlacklistPath(\"target/test-classes\")");
    }

    @Test
    void build_requiresStudentFacingTestFeedbackAndNonDegenerateWitnessTests() {
        // Ego-death audit fix: bare assertEquals/assertThrows give a failing student no diagnostic, and a universal "regardless of depth" promise was only witnessed at depth 2.
        // Pin both.
        String prompt = systemPromptService.build(exerciseWith(ProgrammingLanguage.JAVA, ""));
        assertThat(prompt).contains("human-readable failure message").contains("@DisplayName").contains("NON-DEGENERATE").contains("depth-3-or-deeper");
    }

    @Test
    void build_forbidsTypographyInSourceCodeAndExampleReproductionOfGradedInputs() {
        String prompt = systemPromptService.build(exerciseWith(ProgrammingLanguage.JAVA, ""));
        // The ASCII rule must reach source code (comments/strings/exception messages), and worked examples must not hand over a graded test's exact composite input.
        assertThat(prompt).contains("all authored SOURCE CODE").contains("exception message").contains("NEVER reproduce a graded test's exact composite input");
    }

    @Test
    void build_enumeratesTheRealToolSurfaceAndForbidsApplyPatch() {
        // Regression guard: the agent repeatedly hallucinated a Codex-style apply_patch tool (and an ls tool), wasting turns and — via a silent bash apply_patch no-op — corrupting
        // its
        // model of the file state. The prompt must name the exact tool set and explicitly rule out apply_patch in both its tool-call and bash-command forms.
        String prompt = systemPromptService.build(exerciseWith(ProgrammingLanguage.JAVA, ""));
        assertThat(prompt).contains("Your ONLY tools are bash, read_file, write_file, edit_file, verify, and submit").contains("NO apply_patch tool")
                .contains("Never call apply_patch").contains("do NOT re-read a file whose contents you have already seen");
    }

    @Test
    void build_makesVerifyThePrimarySelfCheckAndTheAuthoritativeSourceOfTestNames() {
        // The per-language [task] naming rules are guides; the authoritative source is now the `verify` tool, which lists the EXACT parser-form test names the agent must copy
        // verbatim into [task]s and reports the differential VERDICT the acceptance gate will conclude. This is what lets the agent self-correct when a framework's reported name
        // differs from the profile's described rule (e.g. Dart group+test space-joining) and never guess a name.
        String prompt = systemPromptService.build(exerciseWith(ProgrammingLanguage.DART, ""));
        // Pin the two contract phrases (no other test covers them): verify is the PRIMARY self-check, and its names are copied VERBATIM. Surrounding prose is not pinned.
        assertThat(prompt).contains("PRIMARY self-check").contains("VERBATIM");
    }

    @Test
    void build_encodesAPlusQualityRules() {
        // Pin the two enrichments that map to a real verifier gate: test EVERY promised contract (the untested-promise axis), and clean up now-unused dependencies before
        // submitting
        // (the dead-dependency axis). The prose-quality phrasings around them are not contracts and are intentionally not pinned, so a reword does not break this test.
        String prompt = systemPromptService.build(exerciseWith(ProgrammingLanguage.JAVA, ""));
        assertThat(prompt).contains("EVERY promise you make").contains("unused dependencies");
    }

    @Test
    void build_encodesProblemStatementQualityRules() {
        // Each substring pins a rubric-graded authoring contract a real generation defect required: a worked example, student-facing-only prose (the Java meta-note leak), a real
        // behavioural test not a build-gate aggregate (C++), the exact singular [task] keyword (the C++ [tasks] typo), a bounded input domain, a stated float tolerance,
        // plain-ASCII
        // prose, no ungraded Big-O/complexity prose, and grounding domain/error claims in the reference solution. Dropping any is a regression a reword must not silently make.
        String prompt = systemPromptService.build(exerciseWith(ProgrammingLanguage.JAVA, ""));
        assertThat(prompt).contains("WORKED EXAMPLE").contains("STUDENT-FACING ONLY").contains("build-gate").contains("INPUT DOMAIN")
                .contains("EXACT five-character singular keyword `[task]`").contains("NEVER the vague \"practise\"").contains("within 1e-6").contains("not implementation")
                .contains("typographic").contains("Do NOT state any complexity").contains("Design note (not graded)").contains("exact equality")
                .contains("the message text is not graded").contains("CONCRETE FENCED trace");
    }

    @Test
    void build_forbidsStatingDomainGuaranteesTheSolutionDoesNotEnforce() {
        // Loop-4 regression: the "declare NaN/Infinity in/out of domain" hardening backfired — the model invented a "NaN throws IllegalArgumentException" rule a `< 0` guard
        // provably
        // does NOT enforce in IEEE-754, shipping a contract that is both untested and false. The PROMISE->SOLUTION-CODE check must require grounding every domain/error guarantee
        // in
        // what the reference solution actually does.
        String prompt = systemPromptService.build(exerciseWith(ProgrammingLanguage.JAVA, ""));
        assertThat(prompt).contains("the REFERENCE SOLUTION actually enforces").contains("does NOT reject NaN");
    }

    @Test
    void build_forbidsAddingOrStrippingParensOnTestNames() {
        // The binding resolves by exact string match against the framework-reported test name, so "tidying" a bare name to name() (or vice versa) silently grades it 0. This is the
        // root cause of the user-reported "()" confusion, so pin the explicit do-not-touch-parens instruction.
        String prompt = systemPromptService.build(exerciseWith(ProgrammingLanguage.JAVA, ""));
        assertThat(prompt).contains("do NOT add, remove").contains("grades it 0");
    }

    @Test
    void build_specMode_instructsToStripMetaNotesAndMeetQualityBar() {
        // The Java meta-leak entered through the ADAPT/spec path (a placeholder problem statement carried internal notes the agent kept). Spec mode must now tell the agent to
        // delete
        // such notes and lift the statement to the quality bar without changing the task.
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
        // Pin one token per axis the audit found (no other test covers them): the student-facing template ("scratchpad") and the coverage self-check ("re-read your tests…").
        String prompt = systemPromptService.build(exerciseWith(ProgrammingLanguage.JAVA, ""));
        assertThat(prompt).contains("scratchpad").contains("re-read your tests against the problem statement");
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
        // Every creatable language gets a profile, and every profile must teach how a [task] binds for its framework (the most error-prone, divergent part).
        assertThat(guidance).as("guidance for %s", language).isNotBlank();
        assertThat(guidance).as("%s profile explains [task] bindings", language).contains("[task]");
    }

    @Test
    void profiles_encodeEachFrameworksDivergentTaskNamingRule() {
        // These are the per-framework rules the agent most often gets wrong; pin the salient phrase of each so a regression in the profile is caught.
        assertThat(profile(ProgrammingLanguage.PYTHON)).contains("test METHOD name");
        assertThat(profile(ProgrammingLanguage.JAVASCRIPT)).contains("underscore");
        assertThat(profile(ProgrammingLanguage.TYPESCRIPT)).contains("underscore");
        assertThat(profile(ProgrammingLanguage.RUBY)).contains("test METHOD name");
        assertThat(profile(ProgrammingLanguage.R)).contains("DESCRIPTION STRING");
        // Dart: the package:test full name is the space-joined group+test (group('reverseString')+test('reverse_non_empty') -> "reverseString reverse_non_empty"). Production
        // prepends the
        // dot test-FILE suite prefix (test.palindrome) ONLY with >=2 test files; with a SINGLE test file (the common case, one top-level suite) the prefix is DROPPED and the name
        // is bare.
        // The profile must teach the singular-suite exception (an earlier version wrongly prefixed single-file names, which would bind to nothing in production).
        assertThat(profile(ProgrammingLanguage.DART)).contains("joined by SINGLE SPACES").contains("reverseString reverse_non_empty").contains("DROPPED")
                .contains("TWO OR MORE test files");
        assertThat(profile(ProgrammingLanguage.GO)).contains("TestXxx");
        assertThat(profile(ProgrammingLanguage.RUST)).contains("#[test] fn");
        // Catch2 reports a nested SECTION as the slash-joined path TEST_CASE/SECTION; the profile must teach both the flat-TEST_CASE name and the slash rule.
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
        // MAVEN_BLACKBOX is a DejaGnu (expect/stdin-stdout) harness with NO JUnit tests, so its guidance must NOT carry the Ares/JUnit conventions and must instead teach the
        // black-box specifics and the dejagnu[<step>] [task] binding.
        ProgrammingExercise blackbox = exerciseWith(ProgrammingLanguage.JAVA, "");
        blackbox.setProjectType(ProjectType.MAVEN_BLACKBOX);
        String blackboxGuidance = LanguageGenerationProfile.guidanceFor(blackbox);
        assertThat(blackboxGuidance).contains("MAVEN_BLACKBOX").contains("DejaGnu").contains("dejagnu[public]").contains("dejagnu[advanced]").contains("dejagnu[secret]")
                .contains("Tests.txt").contains("STDIN").contains("[task]");
        // It must NOT carry the jvm()/Ares instruction surface — the annotation-usage example and the method-name binding rule are the distinctive markers of the JUnit profile.
        // (The black-box text may NAME @Public/@StrictTimeout only to forbid them, so assert on the instructional phrases, not the bare tokens.)
        assertThat(blackboxGuidance).doesNotContain("@WhitelistPath(\"target\")").doesNotContain("@BlacklistPath(\"target/test-classes\")")
                .doesNotContain("every test class MUST be").doesNotContain("The [task] binding uses the test METHOD name");

        // A normal Java project type (e.g. PLAIN_MAVEN) and the null default both keep the JUnit/Ares jvm() profile.
        ProgrammingExercise plainMaven = exerciseWith(ProgrammingLanguage.JAVA, "");
        plainMaven.setProjectType(ProjectType.PLAIN_MAVEN);
        assertThat(LanguageGenerationProfile.guidanceFor(plainMaven)).contains("@Public").contains("@StrictTimeout");
        assertThat(profile(ProgrammingLanguage.JAVA)).contains("@Public").contains("@StrictTimeout");
    }

    // ---- The single server source of truth for the one-click whole-exercise generation offer ------------------------------------------------------------------------------------

    @Test
    void supportedGenerationLanguages_pinsTheOracleVerifiableSet() {
        // Pin the exact set so a drift on the server (the single source of truth the client now consumes) is caught.
        assertThat(systemPromptService.supportedGenerationLanguages()).containsExactlyInAnyOrder(ProgrammingLanguage.JAVA, ProgrammingLanguage.KOTLIN, ProgrammingLanguage.PYTHON,
                ProgrammingLanguage.JAVASCRIPT, ProgrammingLanguage.TYPESCRIPT, ProgrammingLanguage.GO, ProgrammingLanguage.RUST, ProgrammingLanguage.C_PLUS_PLUS,
                ProgrammingLanguage.C_SHARP, ProgrammingLanguage.DART, ProgrammingLanguage.RUBY, ProgrammingLanguage.R, ProgrammingLanguage.HASKELL, ProgrammingLanguage.SWIFT);
    }

    @Test
    void isGenerationSupported_acceptsOfferSet_rejectsBestEffortAndUntemplated() {
        // 3 representative arms (the exact offer set is already pinned above); full-enum iteration over Set.contains adds no signal.
        assertThat(systemPromptService.isGenerationSupported(ProgrammingLanguage.JAVA)).as("offered").isTrue();
        assertThat(systemPromptService.isGenerationSupported(ProgrammingLanguage.OCAML)).as("best-effort excluded").isFalse();
        assertThat(systemPromptService.isGenerationSupported(ProgrammingLanguage.SQL)).as("untemplated excluded").isFalse();
    }

    @Test
    void isGenerationSupported_rejectsNullLanguage() {
        assertThat(systemPromptService.isGenerationSupported(null)).isFalse();
    }
}
