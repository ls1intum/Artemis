package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.localci.service.BuildPhasesTemplateService;
import de.tum.cit.aet.artemis.localci.service.BuildScriptProviderService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.service.RepositoryCheckoutService;

/**
 * Produces the single build recipe — {@code verify.sh} — that both the agent (to check its work) and the {@link AuthoritativeVerificationService} (to decide the verdict) run.
 * Having one
 * script rather than two divergent command strings guarantees the agent's view of "does it build?" is byte-for-byte the same as the grader's.
 * <p>
 * The script faithfully reproduces the real Artemis CI layout ({@code templates/localci/<lang>/build_and_run_tests.sh}): it assembles a fresh, hermetic build tree, copies the
 * tests into it, copies the chosen assignment ({@code solution/} or {@code template/}) into an {@code assignment/} directory next to the tests (so the test project's
 * {@code sourceDirectory} resolves against the assignment exactly as in CI), runs the exercise's real per-language build phases ({@link BuildPhasesTemplateService}), and then
 * emits a machine-readable {@code HYPERION_RESULT} line aggregated from the JUnit XML reports. Parsing the XML rather than scraping the build log makes the verdict
 * build-tool-agnostic and immune to log-format changes, and lets the oracle distinguish "compiled but tests failed" from "did not compile" (no reports at all).
 */
@Lazy
@Service
@Conditional(HyperionEnabled.class)
public class SandboxBuildCommandService {

    private static final Logger log = LoggerFactory.getLogger(SandboxBuildCommandService.class);

    static final String VERIFY_SCRIPT_NAME = "verify.sh";

    private static final String VERIFY_SCRIPT_PATH = GenerationWorkspaceService.WORKSPACE + "/" + VERIFY_SCRIPT_NAME;

    /**
     * Verifier-owned directory OUTSIDE {@code /workspace} where the authoritative verifier re-seeds a pristine {@code verify.sh} immediately before each verification run. The
     * agent
     * can write anywhere under {@code /workspace} (and overwrite the {@code /workspace/verify.sh} self-check copy), but the agent tools only ever resolve paths relative to
     * {@code /workspace} ({@link SandboxAgentTools#workspaceRelativePath}), so a path here is unreachable through the tools. The grader therefore runs a script the agent never
     * touched, which is what makes the verdict non-forgeable.
     */
    static final String PRISTINE_VERIFY_DIR = "/opt/hyperion";

    /** Absolute path of the pristine, verifier-controlled {@code verify.sh} the authoritative verifier runs (never the agent's {@code /workspace} copy). */
    static final String PRISTINE_VERIFY_PATH = PRISTINE_VERIFY_DIR + "/" + VERIFY_SCRIPT_NAME;

    /** The prefix of the machine-readable summary line {@code verify.sh} prints; the verifier parses the line that starts with this token. */
    static final String RESULT_MARKER = "HYPERION_RESULT";

    /** The prefix of the per-test-name lines {@code verify.sh} prints (one per JUnit testcase), so the verifier can confirm every [task] binding resolves to a real test name. */
    static final String TESTNAME_MARKER = "HYPERION_TESTNAME";

    /**
     * The prefix of the per-test lines {@code verify.sh} prints for every {@code <testcase>} that carries a {@code <failure>}/{@code <error>} child (one per failing/erroring
     * testcase). The verifier uses the TEMPLATE run's set to require that every {@code [task]}-bound test the solution passes actually FAILS on the template — so a template that
     * accidentally satisfies a graded test (e.g. {@code fibonacci(0)==0} for a {@code return 0} stub) is rejected rather than shipped as a free-points exercise.
     */
    static final String TESTFAIL_MARKER = "HYPERION_TESTFAIL";

    /**
     * The prefix of the per-static-code-analysis-finding lines {@code verify.sh} prints (one per parsed SCA issue) when the exercise has static code analysis ENABLED. Each line
     * carries the producing tool and the issue's raw category as {@code <TOOL>|<rawCategory>} (or {@code <TOOL>|*} for the SARIF/GCC/etc. tools whose category requires the
     * heavyweight production categorizer, which is not reimplemented in POSIX). The {@link AuthoritativeVerificationService} maps each {@code (tool, rawCategory)} through the real
     * production category configuration ({@code StaticCodeAnalysisConfigurer} + the exercise's persisted categories) to decide whether the finding falls in a {@code GRADED}
     * category with a positive penalty — i.e. whether production's {@code calculateTotalPenalty} would dock the solution for it. The marker is emitted only when SCA is enabled, so
     * the verdict for a non-SCA exercise is byte-for-byte unchanged.
     */
    static final String SCA_MARKER = "HYPERION_SCA";

    /** Sentinel raw-category for SCA findings whose real category is not cheaply derivable in POSIX (SARIF/GCC/etc.); the oracle treats it as "any category of this tool". */
    static final String SCA_UNKNOWN_CATEGORY = "*";

    /**
     * JUnit-XML report locations that cover the languages Artemis ships, independent of any phase-declared paths (surefire/failsafe for Maven, Gradle's test-results, and the
     * test-reports/ directory pytest, the C harness and OCaml write to). The phase's own resultPaths are added on top so any language-specific location is also covered.
     */
    private static final List<String> DEFAULT_REPORT_GLOBS = List.of("surefire-reports/*.xml", "failsafe-reports/*.xml", "test-results/*.xml", "test-results/*/*.xml",
            "test-reports/*.xml", "test-results.xml");

    // Both services are present only on nodes that run the LocalCI orchestration (profile localci). On a core-only node they are absent; generation requires a co-located build
    // agent anyway, so the dependencies are optional and their absence is reported clearly at call time rather than preventing the core node from starting.
    private final Optional<BuildPhasesTemplateService> buildPhasesTemplateService;

    private final Optional<BuildScriptProviderService> buildScriptProviderService;

    public SandboxBuildCommandService(Optional<BuildPhasesTemplateService> buildPhasesTemplateService, Optional<BuildScriptProviderService> buildScriptProviderService) {
        this.buildPhasesTemplateService = buildPhasesTemplateService;
        this.buildScriptProviderService = buildScriptProviderService;
    }

    /**
     * @return the command that runs the verification with the solution as the assignment (expected to pass)
     */
    public String solutionBuildCommand() {
        return verifyInvocation(GenerationWorkspaceService.directoryFor(RepositoryType.SOLUTION));
    }

    /**
     * @return the command that runs the verification with the template as the assignment (expected to fail)
     */
    public String templateBuildCommand() {
        return verifyInvocation(GenerationWorkspaceService.directoryFor(RepositoryType.TEMPLATE));
    }

    /**
     * @param nonce the per-run anti-forgery nonce the pristine script stamps onto every {@code HYPERION_*} marker line; the verifier accepts only marker lines bearing this exact
     *                  nonce, so a line the agent's test code prints to stdout (which never carries this freshly-generated, unguessable token) is ignored
     * @return the command that runs the PRISTINE (verifier-controlled, outside {@code /workspace}) verification with the solution as the assignment
     */
    public String pristineSolutionBuildCommand(String nonce) {
        return pristineVerifyInvocation(GenerationWorkspaceService.directoryFor(RepositoryType.SOLUTION), nonce);
    }

    /**
     * @param nonce the per-run anti-forgery nonce (see {@link #pristineSolutionBuildCommand})
     * @return the command that runs the PRISTINE (verifier-controlled, outside {@code /workspace}) verification with the template as the assignment
     */
    public String pristineTemplateBuildCommand(String nonce) {
        return pristineVerifyInvocation(GenerationWorkspaceService.directoryFor(RepositoryType.TEMPLATE), nonce);
    }

    private static String verifyInvocation(String assignmentDirectory) {
        return "sh " + VERIFY_SCRIPT_PATH + " " + assignmentDirectory;
    }

    private static String pristineVerifyInvocation(String assignmentDirectory, String nonce) {
        // The nonce is the script's second argument; verify.sh stamps it onto every emitted marker so the verifier can reject any HYPERION_* line a build phase (the agent's test
        // code) printed to stdout — such a line cannot carry the per-run nonce. Quote it defensively even though it is hex.
        return "sh " + PRISTINE_VERIFY_PATH + " " + assignmentDirectory + " '" + nonce + "'";
    }

    /**
     * Builds the content of the {@code verify.sh} script seeded into the workspace.
     *
     * @param exercise the exercise whose per-language build phases the script runs
     * @return the full shell script (POSIX {@code sh}); it takes one argument, {@code solution} or {@code template}
     */
    public String verifyScriptContent(ProgrammingExercise exercise) {
        BuildRecipe recipe = resolveBuildRecipe(exercise);
        String findExpression = buildFindExpression(recipe.reportGlobs());
        // Materialize the tests at the language's real test checkout path (root for Java/Python, a "tests/" subdir for C/Go/OCaml/…) so phase scripts that `cd` into it resolve.
        String testDestination = recipe.testDir().isEmpty() ? "$BUILD_DIR" : "$BUILD_DIR/" + recipe.testDir();
        String phaseSection = buildPhaseSection(recipe.phases());
        // The script is intentionally plain POSIX sh so it runs in any of the ~20 language images (some have no bash). It must not abort before printing HYPERION_RESULT, so each
        // phase runs in its own subshell (re-rooted at the build dir, like real CI) and a non-zero exit is recorded rather than aborting the script.
        return """
                #!/bin/sh
                # Generated by Artemis Hyperion. Assembles the CI build layout and runs the exercise's real build phases for one assignment (solution or template),
                # then prints a machine-readable result line. Used by both the generation agent (to self-check) and the authoritative verifier (to decide acceptance).
                ASSIGNMENT="$1"
                if [ "$ASSIGNMENT" != "solution" ] && [ "$ASSIGNMENT" != "template" ]; then
                    echo "usage: verify.sh <solution|template> [nonce]" >&2
                    exit 64
                fi
                # Anti-forgery nonce (second argument). The authoritative verifier passes a fresh, unguessable token per run and stamps it onto every HYPERION_* marker below, then
                # accepts ONLY marker lines bearing this exact token. A HYPERION_TESTFAIL/HYPERION_RESULT line the agent's test code prints to stdout (interleaved with the phase
                # output the verifier scrapes) cannot carry this token, so it is ignored. The agent's own /workspace self-check passes no nonce (the markers are then unprefixed),
                # which is fine — that copy's verdict is never trusted. A trailing space keeps the marker shape "MARKER<sp>tests=" identical when no nonce is supplied.
                NONCE="$2"
                if [ -n "$NONCE" ]; then MARK_SUFFIX=" $NONCE"; else MARK_SUFFIX=""; fi
                WORKSPACE="%s"
                BUILD_DIR=$(mktemp -d /tmp/hyperion-verify.XXXXXX) || exit 70
                trap 'rm -rf "$BUILD_DIR"' EXIT
                # Materialize the CI checkout layout: the tests at the language's test checkout path, the chosen assignment in assignment/ (-a preserves exec bits and binaries).
                TEST_DEST="%s"
                mkdir -p "$TEST_DEST"
                cp -a "$WORKSPACE/tests/." "$TEST_DEST"/ 2>/dev/null || true
                mkdir -p "$BUILD_DIR/assignment"
                cp -a "$WORKSPACE/$ASSIGNMENT/." "$BUILD_DIR/assignment"/ 2>/dev/null || true
                # Substitute the CI directory placeholders inside the COPIED test harness/build files, exactly as ProgrammingExerciseRepositoryService.replacePlaceholders bakes them
                # in at real exercise-creation time, so a seeded harness (e.g. Haskell's test.cabal hs-source-dirs, a tsconfig project reference) resolves against THIS build tree
                # without the agent ever having to edit it. The chosen assignment is always copied into assignment/, so both the student parent and the solution working directory
                # map to "assignment" here (the graded test executable builds against the submission = the copied assignment in both runs); the test working directory is the build
                # root ("."). This is a build-tree copy only; the seeded source files the agent edits and the verifier reads back are untouched.
                find "$TEST_DEST" -type f 2>/dev/null | while IFS= read -r f; do
                    sed -e 's#${studentWorkingDirectory}#/assignment/src#g' \\
                        -e 's#${studentParentWorkingDirectoryName}#assignment#g' \\
                        -e 's#${solutionWorkingDirectory}#assignment#g' \\
                        -e 's#${testWorkingDirectory}#.#g' "$f" > "$f.hyp" 2>/dev/null && mv "$f.hyp" "$f" 2>/dev/null || rm -f "$f.hyp" 2>/dev/null
                done
                # Anti-forgery: the agent can plant fake JUnit report XML into its tests/ tree, and `cp -a` above preserves that file's old mtime in the build tree. Delete EVERY
                # report XML that already exists in the assembled build tree BEFORE the phases run, so only reports the phases write THIS run are aggregated. The same report globs the
                # aggregation step searches are deleted here, so a planted surefire-reports/foo.xml (or any declared report path) cannot survive into the count.
                find "$BUILD_DIR" -type f \\( %s \\) -delete 2>/dev/null || true
                # A reference marker stamped immediately before the phases; the aggregation below counts only reports NEWER than it, so a planted report that somehow escaped the
                # delete (or one re-copied by a phase) still cannot be summed unless the build itself produced it this run.
                BUILD_START_MARKER="$BUILD_DIR/.hyperion-build-start"
                : > "$BUILD_START_MARKER"
                # Run the exercise's real build phases, each from the build root. A non-zero exit (failing tests or a compile error) is expected for the template and is the verdict.
                rc=0
                run_phase() {
                    ( cd "$BUILD_DIR" || exit 70; set -e; eval "$1" )
                    phase_rc=$?
                    if [ "$phase_rc" -ne 0 ] && [ "$rc" -eq 0 ]; then rc=$phase_rc; fi
                }
                %s
                # Aggregate the JUnit XML the runner wrote (the language's declared report paths plus the common per-language locations), summing the testsuite counters across all
                # reports. Only reports written AFTER the build-start marker count, so a planted report can never inflate the result. Absent reports => 0 (e.g. a compile error means
                # the runner never wrote any).
                xml=$(find "$BUILD_DIR" -type f -newer "$BUILD_START_MARKER" \\( %s \\) 2>/dev/null)
                # Anti-forgery (report-text): the name/count/failure extraction below uses grep/awk, NOT a real XML parser, so a graded test that genuinely PASSES could otherwise
                # forge a FAILED-looking verdict by printing markup-looking text — a phantom <testcase>, a forged failures="N", a stray HYPERION_TESTFAIL — into its OWN captured
                # <system-out>/<failure>/CDATA/comment, none of which production's Jackson XmlMapper (de.tum.cit.aet.artemis.buildagent.service.parser.TestResultXmlParser) treats as
                # structure (it records <failure>/<error>/<system-out> bodies as text, never as elements). Before any grep/awk reads a report, run a small POSIX-awk lexer over each
                # one that DELETES the bytes that are character data rather than markup: <!-- ... --> comments, <![CDATA[ ... ]]> sections, and the text content (body) of
                # <system-out>/<system-err>/<failure>/<error> elements — while KEEPING those elements' own start/end tags, so a real <failure>/<error> still flags its testcase and a
                # real <testcase>'s attributes (name=, message= on the kept failure) are untouched. CDATA and comments are consumed as units even inside a suppressed body, so a
                # literal </failure> hidden in CDATA cannot prematurely end suppression. Namespace prefixes are stripped when matching (ns:failure), attributes appear in any order,
                # self-closing tags (<failure .../>) carry no body, and a tag is recognized only at a real "<" — so the scrubbed counts/names agree with what TestResultXmlParser
                # records from the well-formed XML. The downstream count_testcases/sum_attr/emit_test_lines read the scrubbed copies via the reassigned $xml, so the hardening covers
                # every consumer with no change to their logic. Absent reports (compile error) => $xml empty => no scrub, and the consumers already short-circuit on empty $xml.
                if [ -n "$xml" ]; then
                    SCRUB_DIR=$(mktemp -d /tmp/hyperion-scrub.XXXXXX) || exit 70
                    scrubbed_xml=""
                    scrub_n=0
                    for report in $xml; do
                        scrub_n=$((scrub_n + 1))
                        scrubbed_report="$SCRUB_DIR/report-$scrub_n.xml"
                        # Lexer: walk the report byte-by-byte. suppress holds the local name of the element whose body we are dropping ("" = not suppressing). We keep markup and
                        # delete only character data, so the grep/awk below see exactly the element structure TestResultXmlParser sees.
                        awk '
                        { buf = buf $0 "\\n" }
                        END {
                            n = length(buf); out = ""; i = 1; suppress = ""
                            while (i <= n) {
                                if (substr(buf, i, 4) == "<!--") { e = index(substr(buf, i + 4), "-->"); if (e == 0) { i = n + 1; continue } i = i + 4 + e + 2; continue }
                                if (substr(buf, i, 9) == "<![CDATA[") { e = index(substr(buf, i + 9), "]]>"); if (e == 0) { i = n + 1; continue } i = i + 9 + e + 2; continue }
                                c = substr(buf, i, 1)
                                if (suppress != "") {
                                    if (c == "<" && substr(buf, i + 1, 1) == "/") {
                                        if (match(substr(buf, i + 2), /^[A-Za-z_:][-A-Za-z0-9_:.]*/)) {
                                            tname = substr(buf, i + 2, RLENGTH); sub(/^[^:]*:/, "", tname)
                                            if (tname == suppress) {
                                                e = index(substr(buf, i), ">"); if (e == 0) { i = n + 1; continue }
                                                out = out substr(buf, i, e); i = i + e; suppress = ""; continue
                                            }
                                        }
                                    }
                                    i = i + 1; continue
                                }
                                if (c == "<") {
                                    if (match(substr(buf, i + 1), /^[A-Za-z_:][-A-Za-z0-9_:.]*/)) {
                                        base = substr(buf, i + 1, RLENGTH); sub(/^[^:]*:/, "", base)
                                        if (base == "system-out" || base == "system-err" || base == "failure" || base == "error") {
                                            e = index(substr(buf, i), ">"); if (e == 0) { i = n + 1; continue }
                                            starttag = substr(buf, i, e); out = out starttag; i = i + e
                                            if (substr(starttag, length(starttag) - 1, 1) != "/") { suppress = base }
                                            continue
                                        }
                                    }
                                }
                                out = out c; i = i + 1
                            }
                            printf "%%s", out
                        }' "$report" > "$scrubbed_report" 2>/dev/null || cp "$report" "$scrubbed_report" 2>/dev/null
                        scrubbed_xml="$scrubbed_xml $scrubbed_report"
                    done
                    xml="$scrubbed_xml"
                fi
                sum_attr() {
                    if [ -z "$xml" ]; then echo 0; return; fi
                    grep -ho "$1=\\"[0-9]*\\"" $xml 2>/dev/null | tr -dc '0-9\\n' | awk '{ s += $1 } END { print s + 0 }'
                }
                # The test count is the number of <testcase> ELEMENTS, not the testsuite tests="N" attribute. For every framework Artemis ships these agree, EXCEPT Catch2 (C++),
                # whose JUnit reporter sets tests="N" to the assertion count, not the test-case count, and (because REQUIRE is fatal) evaluates fewer assertions in a failing template
                # than in a passing solution — which made the attribute sum disagree between the two and falsely tripped the "different number of tests" gate. Counting <testcase>
                # elements (the same unit Artemis grades by, and the same pattern used for the test-name lines below) is the test count both runs share. Absent reports => 0.
                count_testcases() {
                    if [ -z "$xml" ]; then echo 0; return; fi
                    grep -ho '<testcase[^>]*>' $xml 2>/dev/null | wc -l | tr -dc '0-9'
                }
                # A <testcase> carrying a <skipped> child is graded by production (TestResultXmlParser) as NOT EXECUTED: it is placed in NEITHER the successful nor the failed set. The
                # oracle must mirror that exactly, otherwise a test SKIPPED on the solution (but present/failing on the template) is wrongly counted as a passing solution test and the
                # exercise is accepted even though that test scores 0 for a real student. So count skipped cases (one <skipped> element per skipped case) and exclude them from the
                # executed test count below and from the emitted names. Counting <skipped> ELEMENTS (not the testsuite skipped="N" attribute) is reliable across frameworks that omit it.
                count_skipped_cases() {
                    if [ -z "$xml" ]; then echo 0; return; fi
                    grep -ho '<skipped[ />]' $xml 2>/dev/null | wc -l | tr -dc '0-9'
                }
                all_testcases=$(count_testcases)
                skipped=$(count_skipped_cases)
                tests=$((all_testcases - skipped))
                failures=$(sum_attr failures)
                errors=$(sum_attr errors)
                # Emit, for every JUnit <testcase>, the EXACT name Artemis records it under (the identifier [task] bindings match against), and a fail line for every testcase that
                # carries a <failure>/<error>. The name is composed the SAME way de.tum.cit.aet.artemis.buildagent.service.parser.TestResultXmlParser does: the dot-joined names of the
                # enclosing <testsuite> elements are PREPENDED to the <testcase name>, EXCEPT that a SINGULAR top-level testsuite contributes no prefix (root <testsuite>, or the sole
                # child of a root <testsuites>). The exception drops only the SINGULAR TOP-LEVEL suite's name; any suite nested BELOW it is always prefixed. So a SINGLE Dart test file
                # whose tojunit report is just one top-level <testsuite name="test.palindrome"> drops that name and yields the BARE "reverseString reverse_non_empty" (NOT a
                # "test.palindrome."-prefixed name); but a file-suite nested under an outer suite (or a report with multiple top-level suites) keeps its own name as a prefix
                # ("test.palindrome.reverseString reverse_non_empty"). Likewise Rust's nextest singular top-level suite yields the bare "test_x". Each report file is parsed
                # independently (the file is passed to awk twice: pass 1 counts top-level suites to apply the singular-suite exception, pass 2 emits). awk over the XML keeps the script
                # POSIX and build-tool-agnostic.
                emit_test_lines() {
                    [ -z "$xml" ] && return
                    for report in $xml; do
                        awk -v MARK="%s$MARK_SUFFIX" -v FAILMARK="%s$MARK_SUFFIX" '
                        BEGIN { RS = "<"; sdepth = 0; topcount = 0 }
                        function attrName(s,   v) {
                            if (match(s, /[ \\t\\r\\n]name="[^"]*"/)) { v = substr(s, RSTART + 7, RLENGTH - 8); return v }
                            return ""
                        }
                        function selfClosing(s) { return (s ~ /\\/>[ \\t\\r\\n]*$/) }
                        {
                            tag = $0; pass1 = (NR == FNR)
                            if (tag ~ /^testsuite[ \\t\\r\\n>\\/]/ || tag ~ /^testsuite$/) {
                                if (pass1) { if (sdepth == 0) topcount++ } else { sname[sdepth] = attrName(tag) }
                                if (!selfClosing(tag)) sdepth++
                                next
                            }
                            if (tag ~ /^\\/testsuite[ \\t\\r\\n>]/ || tag ~ /^\\/testsuite$/) { if (sdepth > 0) sdepth--; next }
                            if (pass1) next
                            if (tag ~ /^testcase[ \\t\\r\\n>\\/]/ || tag ~ /^testcase$/) {
                                name = attrName(tag); prefix = ""; start = (topcount == 1) ? 1 : 0
                                for (i = start; i < sdepth; i++) { if (sname[i] != "") prefix = prefix sname[i] "." }
                                composed = prefix name
                                # Defer emitting the name until the verdict is known: a self-closing <testcase/> has no children so it passed; otherwise wait for a <skipped>, a
                                # <failure>/<error>, or </testcase>. A SKIPPED case emits NOTHING (production records it as neither successful nor failed), so it never looks like a
                                # passing solution test.
                                if (selfClosing(tag)) { print MARK " " composed; curName = "" } else { curName = composed }
                                next
                            }
                            if (tag ~ /^skipped[ \\t\\r\\n>\\/]/ || tag ~ /^skipped$/) { curName = ""; next }
                            if (tag ~ /^failure[ \\t\\r\\n>\\/]/ || tag ~ /^failure$/ || tag ~ /^error[ \\t\\r\\n>\\/]/ || tag ~ /^error$/) {
                                if (curName != "") { print MARK " " curName; print FAILMARK " " curName; curName = "" }
                                next
                            }
                            if (tag ~ /^\\/testcase[ \\t\\r\\n>]/ || tag ~ /^\\/testcase$/) { if (curName != "") { print MARK " " curName; curName = "" } next }
                        }
                        ' "$report" "$report" 2>/dev/null
                    done
                }
                emit_test_lines
                %s
                echo "%s$MARK_SUFFIX tests=$tests failures=$failures errors=$errors skipped=$skipped exit=$rc"
                exit $rc
                """
                .formatted(GenerationWorkspaceService.WORKSPACE, testDestination, findExpression, phaseSection, findExpression, TESTNAME_MARKER, TESTFAIL_MARKER,
                        buildScaSection(recipe.scaReportFiles()), RESULT_MARKER);
    }

    /**
     * Renders each build phase as a {@code run_phase '<script>'} call. Single quotes inside a phase are escaped for the POSIX single-quoted string so the phase body is passed to
     * {@code eval} verbatim. Running one phase per {@code run_phase} (each re-rooted at the build dir) mirrors how real CI resets the working directory before every phase.
     */
    private static String buildPhaseSection(List<String> phases) {
        return phases.stream().map(phase -> "run_phase '" + phase.replace("'", "'\\''") + "'").collect(Collectors.joining("\n"));
    }

    /**
     * Builds the POSIX-{@code sh} static-code-analysis emission block, or the empty string when SCA is disabled for the exercise ({@code scaReportFiles} empty) — in which case the
     * generated {@code verify.sh} is byte-for-byte identical to the non-SCA script, so a non-SCA exercise's verdict cannot change.
     * <p>
     * When SCA is enabled, the block locates each tool's report file in the assembled build tree (restricted to reports written THIS run, {@code -newer "$BUILD_START_MARKER"}, so
     * a
     * planted stale report cannot fabricate an SCA finding) and emits one {@code HYPERION_SCA <nonce> <TOOL>|<rawCategory>} line per parsed issue:
     * <ul>
     * <li><b>SpotBugs</b> ({@code spotbugsXml.xml}) — {@code <BugInstance ... category="X" ...>} ⇒ {@code SPOTBUGS|X} (the category attribute, exactly what {@code SpotbugsParser}
     * reads).</li>
     * <li><b>Checkstyle</b> ({@code checkstyle-result.xml}) — {@code <error ... source="...checks.<cat>.XCheck">} ⇒ {@code CHECKSTYLE|<cat>} (the package segment after
     * {@code .checks.}; {@code miscellaneous} when the rule sits directly under {@code checks}), mirroring {@code CheckstyleParser.extractCategory}.</li>
     * <li><b>PMD</b> ({@code pmd.xml}) — {@code <violation ... ruleset="X" ...>} ⇒ {@code PMD|X}; <b>PMD-CPD</b> ({@code cpd.xml}) — {@code <duplication>} ⇒
     * {@code PMD_CPD|Copy/Paste Detection}.</li>
     * <li><b>All other tools</b> (SARIF — ruff/clippy/eslint/clang-tidy/dart/rubocop/lintr — and GCC) — these derive the issue category through the heavyweight production rule
     * categorizer (hundreds of rule⇒category entries) that is deliberately NOT reimplemented in POSIX; so the block emits {@code <TOOL>|*} once per finding, and the oracle treats
     * {@code *} as "any category of this tool" (a finding counts iff the tool has a GRADED, positively-penalised category in the exercise config). This is the documented,
     * conservative residual: for these tools the oracle may reject a solution whose lone finding lies in a non-graded category of the same tool — a sound over-rejection that
     * simply
     * demands a lint-clean reference solution when SCA is graded, never an unsound accept.</li>
     * </ul>
     * Categories carrying {@code |} would corrupt the {@code <TOOL>|<category>} shape; no real SpotBugs/Checkstyle/PMD category contains a pipe, but the oracle splits on the FIRST
     * {@code |} so a category is preserved verbatim regardless.
     */
    private static String buildScaSection(List<String> scaReportFiles) {
        if (scaReportFiles.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        // A helper that finds reports written THIS run matching one file name and runs the given awk program over each, stamping the nonce-bearing SCA marker via the awk -v MARK.
        sb.append("emit_sca_for() {\n");
        sb.append("    sca_name=\"$1\"; sca_prog=\"$2\"\n");
        sb.append("    sca_files=$(find \"$BUILD_DIR\" -type f -name \"$sca_name\" -newer \"$BUILD_START_MARKER\" 2>/dev/null)\n");
        sb.append("    [ -z \"$sca_files\" ] && return\n");
        sb.append("    for sca_report in $sca_files; do\n");
        sb.append("        awk -v MARK=\"").append(SCA_MARKER).append("$MARK_SUFFIX\" \"$sca_prog\" \"$sca_report\" 2>/dev/null || true\n");
        sb.append("    done\n");
        sb.append("}\n");
        for (String fileName : scaReportFiles) {
            StaticCodeAnalysisTool tool = StaticCodeAnalysisTool.getToolByFilePattern(fileName).orElse(StaticCodeAnalysisTool.OTHER);
            String awkProg = scaAwkProgram(tool);
            // The awk program is passed as a double-quoted sh argument; it contains no $ (so no sh expansion) and no embedded double quotes (we use single-quoted awk string
            // literals throughout), so it is safe. The file name is a fixed, known token (no shell metacharacters).
            sb.append("emit_sca_for '").append(fileName).append("' '").append(awkProg).append("'\n");
        }
        return sb.toString();
    }

    /**
     * The awk program (record-per-{@code <} tag, like {@code emit_test_lines}) that, for one SCA tool's report, prints {@code MARK <TOOL>|<rawCategory>} per issue. SpotBugs,
     * Checkstyle, PMD and PMD-CPD extract the real raw category; every other tool emits {@code <TOOL>|*} per finding (see {@link #buildScaSection}). The returned program is
     * embedded
     * inside a single-quoted sh string in {@link #buildScaSection}, so it must contain NO single quote — all string literals here use awk regex/`substr` rather than quoted
     * strings,
     * except where a double-quoted awk literal is used (awk literals are double-quoted, which is fine inside the sh single-quoted wrapper).
     */
    private static String scaAwkProgram(StaticCodeAnalysisTool tool) {
        return switch (tool) {
            // SpotBugs: one marker per <BugInstance ...> using its category="..." attribute.
            case SPOTBUGS -> "BEGIN { RS = \"<\" } /^BugInstance[ \\t\\r\\n>\\/]/ { c = \"\"; "
                    + "if (match($0, /[ \\t\\r\\n]category=\"[^\"]*\"/)) { c = substr($0, RSTART + 11, RLENGTH - 12) } if (c == \"\") { c = \"*\" } print MARK \" SPOTBUGS|\" c }";
            // Checkstyle: one marker per <error ...>, category = the second-to-last segment of source=\"...\" (miscellaneous when that segment is literally \"checks\", i.e. the
            // rule
            // sits directly under the checks package), and \"Unknown\" when the source has fewer than two segments — byte-for-byte CheckstyleParser.extractCategory.
            case CHECKSTYLE -> "BEGIN { RS = \"<\" } /^error[ \\t\\r\\n>\\/]/ { src = \"\"; "
                    + "if (match($0, /[ \\t\\r\\n]source=\"[^\"]*\"/)) { src = substr($0, RSTART + 9, RLENGTH - 10) } " + "n = split(src, parts, \".\"); cat = \"Unknown\"; "
                    + "if (n >= 2) { cat = parts[n - 1]; if (cat == \"checks\") { cat = \"miscellaneous\" } } " + "print MARK \" CHECKSTYLE|\" cat }";
            // PMD: one marker per <violation ...> using its ruleset="..." attribute.
            case PMD -> "BEGIN { RS = \"<\" } /^violation[ \\t\\r\\n>\\/]/ { c = \"\"; "
                    + "if (match($0, /[ \\t\\r\\n]ruleset=\"[^\"]*\"/)) { c = substr($0, RSTART + 10, RLENGTH - 11) } if (c == \"\") { c = \"*\" } print MARK \" PMD|\" c }";
            // PMD-CPD: one marker per <duplication ...> (the single Copy/Paste Detection category).
            case PMD_CPD -> "BEGIN { RS = \"<\" } /^duplication[ \\t\\r\\n>\\/]/ { print MARK \" PMD_CPD|Copy/Paste Detection\" }";
            // All other tools (SARIF + GCC): category requires the production categorizer, not reimplemented in POSIX. Emit <TOOL>|* once per finding (presence is what the oracle
            // needs). SARIF: one per "ruleId" result entry; GCC: one per file:line:col: warning/error line wrapped in the report. We approximate "a finding" per tool below.
            default -> scaGenericAwkProgram(tool);
        };
    }

    /**
     * The generic {@code <TOOL>|*} emitter for tools without a cheap POSIX category extraction. SARIF reports (ruff/clippy/eslint/clang-tidy/dart/rubocop/lintr) carry one
     * {@code "ruleId"} per result; GCC's XML wraps {@code file:line:col: warning|error: ...} lines. We emit one {@code <TOOL>|*} per such finding so the oracle sees the tool
     * produced findings; an over-count is harmless (the oracle only checks presence).
     */
    private static String scaGenericAwkProgram(StaticCodeAnalysisTool tool) {
        String toolTag = tool.name();
        if (tool == StaticCodeAnalysisTool.GCC) {
            // GCC report: text lines (file:line:col: warning|error: msg) wrapped in <root>...</root>. Match each warning/error line.
            return "/[0-9]+:[0-9]+:[ \\t]*(warning|error):/ { print MARK \" " + toolTag + "|*\" }";
        }
        // SARIF JSON: one finding per \"ruleId\" occurrence (each result references a rule). Count ruleId occurrences across the file.
        return "{ s = $0; while (match(s, /\"ruleId\"[ \\t]*:/)) { print MARK \" " + toolTag + "|*\"; s = substr(s, RSTART + RLENGTH) } }";
    }

    /**
     * Builds the {@code find} predicate that locates the test-report XML files: each report glob becomes a {@code -path} clause anchored to match anywhere under the hermetic
     * build tree, OR-ed together. The globs are normalized (globstars dropped, any leading {@code ./} or {@code /} stripped).
     */
    private static String buildFindExpression(List<String> reportGlobs) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String glob : reportGlobs) {
            String normalized = glob.trim().replace("**/", "").replace("**", "*");
            while (normalized.startsWith("./") || normalized.startsWith("/")) {
                normalized = normalized.startsWith("./") ? normalized.substring(2) : normalized.substring(1);
            }
            if (!normalized.isBlank()) {
                tokens.add("-path '*/" + normalized + "'");
            }
        }
        return String.join(" -o ", tokens);
    }

    /**
     * The build phases (each already placeholder-substituted), the JUnit report locations, the test checkout directory ({@code ""} = build root), and — when static code analysis
     * is
     * ENABLED for the exercise — the SCA tool report file names ({@code spotbugsXml.xml}, {@code ruff.sarif}, …) the SCA emission scans. The SCA list is EMPTY when SCA is
     * disabled,
     * so the generated script emits no SCA section and a non-SCA exercise's verdict is byte-for-byte unchanged.
     */
    private record BuildRecipe(List<String> phases, List<String> reportGlobs, String testDir, List<String> scaReportFiles) {
    }

    /**
     * Resolves the per-language build recipe. Uses the exact LocalCI build phases when available so the verification toolchain matches real CI for every supported language, and
     * applies the same placeholder substitution real CI does ({@code ${testWorkingDirectory}}, {@code ${studentParentWorkingDirectoryName}}, …) mapped to the language's real
     * checkout layout: the assignment is checked out into {@code assignment/} and the tests into the language's test checkout path (the build root for Java/Python, a
     * {@code tests/} subdirectory for C/Go/OCaml/…). Falls back to the conventional Gradle/Maven commands when the phase template cannot be resolved.
     */
    private BuildRecipe resolveBuildRecipe(ProgrammingExercise exercise) {
        String assignmentDir = checkoutPath(RepositoryCheckoutService.RepositoryCheckoutPath.ASSIGNMENT, exercise,
                exercise.getBuildConfig() != null ? exercise.getBuildConfig().getAssignmentCheckoutPath() : null, "assignment");
        String testDir = checkoutPath(RepositoryCheckoutService.RepositoryCheckoutPath.TEST, exercise,
                exercise.getBuildConfig() != null ? exercise.getBuildConfig().getTestCheckoutPath() : null, "");

        List<BuildPhaseDTO> phases = List.of();
        if (buildPhasesTemplateService.isPresent()) {
            try {
                // getDefaultBuildPlanPhasesFor may return null (not just throw) when the phase template cannot be resolved, so guard against both.
                List<BuildPhaseDTO> resolved = buildPhasesTemplateService.get().getDefaultBuildPlanPhasesFor(exercise);
                if (resolved != null) {
                    phases = resolved;
                }
            }
            catch (RuntimeException e) {
                log.warn("Could not resolve build phases for exercise {} ({}); falling back to a generic build.", exercise.getId(), e.getMessage());
            }
        }

        List<String> reportGlobs = new ArrayList<>(DEFAULT_REPORT_GLOBS);
        phases.stream().filter(p -> p.resultPaths() != null).flatMap(p -> p.resultPaths().stream()).map(path -> substitute(path, assignmentDir, testDir))
                .filter(s -> s != null && !s.isBlank()).forEach(reportGlobs::add);

        // SCA report files scanned by the SCA emission, ONLY when SCA is enabled (else empty -> no SCA section -> non-SCA verdict unchanged). These are the canonical per-tool
        // report
        // file names production reads (StaticCodeAnalysisTool.getFileName); the static build phase writes them (e.g. java/plain_maven_static.yaml emits target/spotbugsXml.xml), so
        // they already appear in the build tree this run. We scan them independently of resultPaths so the SCA signal does not depend on the SCA report being declared a JUnit
        // glob.
        List<String> scaReportFiles = scaReportFileNames(exercise);

        List<String> phaseScripts = phases.stream().map(BuildPhaseDTO::script).filter(s -> s != null && !s.isBlank()).map(s -> substitute(s, assignmentDir, testDir)).toList();
        if (!phaseScripts.isEmpty()) {
            return new BuildRecipe(phaseScripts, reportGlobs, testDir, scaReportFiles);
        }
        // Generic fallback mirroring build_and_run_tests.sh: prefer a Gradle wrapper, then Maven, then a system Gradle.
        String fallback = """
                if [ -x ./gradlew ]; then ./gradlew clean test --no-daemon;
                elif [ -f pom.xml ]; then mvn clean test;
                elif [ -f build.gradle ]; then gradle clean test --no-daemon;
                else echo 'No recognized build system' >&2; exit 2; fi""";
        return new BuildRecipe(List.of(fallback), reportGlobs, testDir, scaReportFiles);
    }

    /**
     * The canonical SCA report file names for the exercise's language ({@code spotbugsXml.xml}, {@code checkstyle-result.xml}, {@code ruff.sarif}, …) — but ONLY when static code
     * analysis is enabled. Returns an empty list when SCA is disabled (or the language has no SCA tools), so {@link #buildScaSection} emits nothing and the script for a non-SCA
     * exercise is identical to before this feature.
     */
    private static List<String> scaReportFileNames(ProgrammingExercise exercise) {
        if (!Boolean.TRUE.equals(exercise.isStaticCodeAnalysisEnabled()) || exercise.getProgrammingLanguage() == null) {
            return List.of();
        }
        return StaticCodeAnalysisTool.getToolsForProgrammingLanguage(exercise.getProgrammingLanguage()).stream().map(StaticCodeAnalysisTool::getFileName)
                .filter(name -> name != null && !name.isBlank()).distinct().toList();
    }

    /**
     * Resolves a repository checkout subdirectory for the exercise's language: the instructor-configured path if set, otherwise the language default from
     * {@link RepositoryCheckoutService.RepositoryCheckoutPath}. Falls back to {@code defaultPath} if the language is unsupported by the enum.
     */
    private String checkoutPath(RepositoryCheckoutService.RepositoryCheckoutPath kind, ProgrammingExercise exercise, String configured, String defaultPath) {
        if (configured != null && !configured.isBlank()) {
            return configured.startsWith("/") ? configured.substring(1) : configured;
        }
        if (exercise.getProgrammingLanguage() == null) {
            return defaultPath;
        }
        try {
            return kind.forProgrammingLanguage(exercise.getProgrammingLanguage());
        }
        catch (RuntimeException e) {
            return defaultPath;
        }
    }

    /**
     * Substitutes the CI directory placeholders ({@code ${studentParentWorkingDirectoryName}}, {@code ${testWorkingDirectory}}, …) to the resolved checkout layout, reusing the
     * exact substitution real CI applies. An empty test directory (tests at the build root) is substituted as {@code .} so a phase's {@code cd ${testWorkingDirectory}} stays put
     * rather than {@code cd} with no argument. Returns the input unchanged when the LocalCI service is absent (a core-only node, where generation does not run anyway).
     */
    private String substitute(String script, String assignmentDir, String testDir) {
        String testRepo = testDir.isEmpty() ? "." : testDir;
        return buildScriptProviderService.map(service -> service.replacePlaceholders(script, assignmentDir, RepositoryType.SOLUTION.toString(), testRepo)).orElse(script);
    }
}
