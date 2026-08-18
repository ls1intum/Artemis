package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.exercise.dto.ProblemStatementRenderRequestDTO;
import de.tum.cit.aet.artemis.exercise.dto.RenderedProblemStatementDTO;
import de.tum.cit.aet.artemis.exercise.dto.TestFeedbackInputDTO;
import de.tum.cit.aet.artemis.exercise.service.TestReferenceParser;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;

/**
 * Guardrail for the SSR problem-statement migration: every task in the corpus must resolve to a real status when the
 * corresponding feedback is supplied. A regression in reference resolution (for example dropping name-based
 * resolution) turns these into "not-executed" and fails the build.
 * <p>
 * This is also half of the differential parity harness: it emits the server's rendered HTML as a fixture per corpus
 * file so {@code problem-statement-parity.spec.ts} can diff the legacy client pipeline against it. See
 * {@code src/test/resources/test-data/problem-statements/README.md} for the corpus authoring rules.
 */
class ProblemStatementRenderingParityTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "psparity";

    private static final String POST_URL = "/api/exercise/problem-statement/render";

    private static final Path CORPUS_DIRECTORY = Path.of("src/test/resources/test-data/problem-statements");

    private static final Path FIXTURE_DIRECTORY = CORPUS_DIRECTORY.resolve("rendered");

    // Mirrors the client's `taskRegex` (programming-exercise-task.extension.ts), the pipeline this harness diffs the
    // server against, and not a simplified copy: a looser `[^)]*(?:\([^()]*\)[^)]*)*` group mis-parses a reference
    // that is exactly "name()". The greedy `[^)]*` swallows up to the first ')' before the nested-parens alternative
    // ever gets a chance to run, truncating the capture to "name(" and leaving the real closing ')' as stray text.
    // The server's own TASK_PATTERN (ProblemStatementRenderingService) has since been rewritten as a bounded, unrolled
    // loop to keep a pathological task list from overflowing the matcher's stack. It accepts a superset of the grammar
    // below, so every task this harness extracts is still one the server renders as a task.
    private static final Pattern TASK_PATTERN = Pattern.compile("\\[task]\\[([^\\[\\]]+)]\\(((?:[^(),]+(?:\\([^()]*\\)[^(),]*)?(?:,[^(),]+(?:\\([^()]*\\)[^(),]*)?)*)?)\\)");

    private static final Pattern TASK_STATUS_PATTERN = Pattern.compile("data-test-status=\"([^\"]+)\"");

    /**
     * The inline PlantUML SVG inside a diagram container. Group 1 is the container's opening tag, group 2 the SVG.
     * Deliberately scoped to {@code div.artemis-diagram}: the GitHub-alert octicons are inline SVGs too, but their
     * path data is a constant in {@code GitHubAlertExtension} and stays byte-exact in the comparison.
     */
    private static final Pattern DIAGRAM_SVG_PATTERN = Pattern.compile("(<div class=\"artemis-diagram\"[^>]*>\\s*)(<svg\\b.*?</svg>)", Pattern.DOTALL);

    /** A colour PlantUML painted into the diagram, the part of the SVG that carries the test outcome. */
    private static final Pattern SVG_FILL_PATTERN = Pattern.compile("fill=\"(#[0-9A-Fa-f]{6})\"");

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
    }

    private static Stream<Path> corpus() throws IOException {
        List<Path> corpusFiles;
        try (var files = Files.list(CORPUS_DIRECTORY)) {
            // README.md documents the corpus but is not itself corpus content; it also happens to contain the
            // literal task-syntax example `[task][name](refs)` in prose, which would otherwise be picked up and
            // fail the "must contain at least one task reference" / "must resolve to success" assertions.
            corpusFiles = files.filter(path -> path.toString().endsWith(".md") && !path.getFileName().toString().equals("README.md")).toList();
        }
        // An empty corpus yields zero parameterized tests, which a build reports as green. Fail loudly instead: the
        // whole point of this gate is that it runs. The client half asserts the same thing on its side.
        assertThat(corpusFiles).as("corpus directory %s must contain at least one .md file", CORPUS_DIRECTORY).isNotEmpty();
        return corpusFiles.stream();
    }

    @ParameterizedTest
    @MethodSource("corpus")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void everyTaskResolvesWhenFeedbackIsProvided(Path corpusFile) throws Exception {
        String markdown = Files.readString(corpusFile, StandardCharsets.UTF_8);
        List<TestFeedbackInputDTO> feedbacks = feedbacksFor(markdown, true);
        assertThat(feedbacks).as("corpus file %s must contain at least one task reference", corpusFile).isNotEmpty();

        String html = render(markdown, feedbacks);

        // Every task, not merely one of them: a document holding both a success and a fail would satisfy a plain
        // `contains`, and the `doesNotContain` next to it only ruled out one of the three non-success statuses.
        assertThat(taskStatuses(html)).as("all tasks in %s must resolve to success", corpusFile).isNotEmpty().containsOnly("success");

        // Emit the server fixture for the client-side diff. A normal run compares against the committed fixture and
        // never rewrites it, so a rendering regression cannot silently overwrite its own baseline. Regeneration is
        // an explicit, opt-in action via -Dartemis.regenerateProblemStatementFixtures=true.
        String fixtureHtml = canonicalizeDiagramSvgs(html);
        Path fixture = FIXTURE_DIRECTORY.resolve(corpusFile.getFileName().toString().replace(".md", ".html"));
        if (Boolean.getBoolean("artemis.regenerateProblemStatementFixtures")) {
            // Written with a trailing newline: .editorconfig requires insert_final_newline = true for every file in
            // this repo, so a fixture without one invites an editor or formatter to "fix" it later. The compare path
            // below strips exactly one trailing newline back off before comparing, so that eventual fix-up can never
            // break the parity gate for a reason unrelated to actual rendering differences.
            // FileUtils rather than Files.writeString: it creates the missing fixture directory on a first
            // regeneration run, which Files does not (see ArchitectureTest.testFileWriteUsage).
            FileUtils.writeStringToFile(fixture.toFile(), fixtureHtml + "\n", StandardCharsets.UTF_8);
            return;
        }
        assertThat(fixture).as("fixture missing - regenerate with -Dartemis.regenerateProblemStatementFixtures=true").exists();
        String fixtureContent = Files.readString(fixture, StandardCharsets.UTF_8);
        // Strip exactly one trailing newline, matching what the write path above adds. This is not a general
        // whitespace normalization: any other difference, including additional trailing newlines, still fails.
        String normalizedFixtureContent = fixtureContent.endsWith("\n") ? fixtureContent.substring(0, fixtureContent.length() - 1) : fixtureContent;
        assertThat(normalizedFixtureContent).isEqualTo(fixtureHtml);
    }

    /**
     * Canonicalizer "diagram": replaces the inline PlantUML SVG with the colours it painted, keeping its container and
     * position exact.
     * <p>
     * PlantUML measures its layout through AWT font metrics, so the same source renders to different geometry
     * depending on which fonts the machine has: the runner produces {@code viewBox="0 0 170 60"} where macOS produces
     * {@code 165}, down to different glyph path data. Pinning those bytes made the fixture a record of the machine it
     * was generated on rather than of the renderer, and the gate could not pass on both.
     * <p>
     * The colours survive because they are the part of the diagram this feature actually drives: they come from
     * {@code testsColor(...)} resolution against the test outcomes, which happens on the PlantUML *source* before
     * layout and is pinned per case by {@code PlantUmlTaskColorResolverTest}. Everything else about the diagram, that
     * it exists, how many there are, where it sits and which id it carries, still compares byte-exact. The client half
     * of the harness ({@code problem-statement-parity.spec.ts}) drops the whole container in its own "diagram"
     * canonicalizer, so nothing it asserts depends on the payload either.
     *
     * @param html the rendered document
     * @return the document with every diagram SVG replaced by a placeholder listing its distinct fill colours
     */
    private static String canonicalizeDiagramSvgs(String html) {
        Matcher matcher = DIAGRAM_SVG_PATTERN.matcher(html);
        StringBuilder canonicalized = new StringBuilder();
        while (matcher.find()) {
            String fills = SVG_FILL_PATTERN.matcher(matcher.group(2)).results().map(fill -> fill.group(1)).distinct().sorted().collect(Collectors.joining(","));
            matcher.appendReplacement(canonicalized, Matcher.quoteReplacement(matcher.group(1) + "<!--plantuml-svg fills=" + fills + "-->"));
        }
        matcher.appendTail(canonicalized);
        return canonicalized.toString();
    }

    /**
     * The all-passing case above cannot expose a drift in the fail / not-executed arms of the status engine, which is
     * the arm most likely to change. Rendering the same corpus with all-failing and with all-unexecuted feedback pins
     * both. The client-side half ({@code problem-statement-parity.spec.ts}) drives the legacy engine over the same
     * corpus with the same two scenarios and asserts the same expectation per task, so a drift in either engine turns
     * one of the two red.
     */
    @ParameterizedTest
    @MethodSource("corpus")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void everyTaskReflectsTheOutcomeOfItsTests(Path corpusFile) throws Exception {
        String markdown = Files.readString(corpusFile, StandardCharsets.UTF_8);

        List<String> failing = taskStatuses(render(markdown, feedbacksFor(markdown, false)));
        assertThat(failing).as("every task in %s must report fail when all of its tests failed", corpusFile).isNotEmpty().containsOnly("fail");

        // passed = null is "the test is known but was not executed", the tri-state the whole migration hinges on.
        List<String> notExecuted = taskStatuses(render(markdown, feedbacksFor(markdown, null)));
        assertThat(notExecuted).as("every task in %s must report not-executed when none of its tests ran", corpusFile).isNotEmpty().containsOnly("not-executed");

        assertThat(notExecuted).as("both scenarios must cover the same tasks of %s", corpusFile).hasSameSizeAs(failing);
    }

    /**
     * One feedback per DISTINCT reference, numbered in first-appearance order. Deduplication is required because a
     * test referenced by several tasks must resolve to a single id, matching the numbering the client-side half of
     * this harness rebuilds independently; without it, the same test name would spuriously look ambiguous.
     */
    private static List<TestFeedbackInputDTO> feedbacksFor(String markdown, @Nullable Boolean passed) {
        Map<String, Long> idsByReference = new LinkedHashMap<>();
        Matcher matcher = TASK_PATTERN.matcher(markdown);
        while (matcher.find()) {
            for (String reference : TestReferenceParser.splitTestReferences(matcher.group(2))) {
                idsByReference.computeIfAbsent(reference, ignored -> (long) (idsByReference.size() + 1));
            }
        }
        return idsByReference.entrySet().stream().map(entry -> new TestFeedbackInputDTO(entry.getValue(), entry.getKey(), passed, null, null)).toList();
    }

    /**
     * includeCss=false: the client-side half of this harness only parses structure and statuses, so the embedded CSS
     * (problem-statement-css/embedded.css plus the KaTeX stylesheet link) carries no signal. Including it would bloat
     * every fixture and couple them to unrelated styling changes.
     */
    private String render(String markdown, List<TestFeedbackInputDTO> feedbacks) throws Exception {
        var body = new ProblemStatementRenderRequestDTO(markdown, feedbacks, null, "en", false, false, false, null);
        return request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK).html();
    }

    /** The {@code data-test-status} of every rendered task, in document order. */
    private static List<String> taskStatuses(String html) {
        return TASK_STATUS_PATTERN.matcher(html).results().map(match -> match.group(1)).toList();
    }

    /**
     * The diagram canonicalizer is only allowed to hide font-dependent layout, which is the same standard every
     * canonicalizer on the client side is held to. This pins all three halves of that: geometry that differs between
     * machines is hidden, the outcome colours are not, and an inline SVG outside a diagram container (the octicon of a
     * GitHub alert) is left alone.
     */
    @Test
    void diagramCanonicalizerHidesLayoutButNotColours() {
        String macOsLayout = diagramDocument("viewBox=\"0 0 165 60\" width=\"165px\"", "#008000");
        String linuxLayout = diagramDocument("viewBox=\"0 0 170 60\" width=\"170px\"", "#008000");
        String failedOutcome = diagramDocument("viewBox=\"0 0 165 60\" width=\"165px\"", "#FF0000");

        assertThat(canonicalizeDiagramSvgs(linuxLayout)).isEqualTo(canonicalizeDiagramSvgs(macOsLayout));
        assertThat(canonicalizeDiagramSvgs(failedOutcome)).isNotEqualTo(canonicalizeDiagramSvgs(macOsLayout));

        String octicon = "<p><svg class=\"octicon octicon-info mr-2\" viewBox=\"0 0 16 16\"><path d=\"M8 0\"></path></svg></p>";
        assertThat(canonicalizeDiagramSvgs(octicon)).isEqualTo(octicon);
    }

    private static String diagramDocument(String geometry, String fill) {
        return "<div class=\"artemis-diagram\" data-diagram-id=\"uml-0\"><svg " + geometry + "><rect fill=\"" + fill + "\" width=\"142.8047\"></rect></svg></div>";
    }
}
