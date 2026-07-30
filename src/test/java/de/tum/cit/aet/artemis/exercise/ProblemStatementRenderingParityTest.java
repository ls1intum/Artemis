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
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
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

    // Mirrors the client's `taskRegex` (programming-exercise-task.extension.ts) and the server renderer's own
    // TASK_PATTERN (ProblemStatementRenderingService), not a simplified copy: a looser `[^)]*(?:\([^()]*\)[^)]*)*`
    // group mis-parses a reference that is exactly "name()". The greedy `[^)]*` swallows up to the first ')'
    // before the nested-parens alternative ever gets a chance to run, truncating the capture to "name(" and
    // leaving the real closing ')' as stray text. The comma-separated, one-level-of-parens grammar below is the
    // one actually used to extract "tests" in production and must be used here too.
    private static final Pattern TASK_PATTERN = Pattern.compile("\\[task]\\[([^\\[\\]]+)]\\(((?:[^(),]+(?:\\([^()]*\\)[^(),]*)?(?:,[^(),]+(?:\\([^()]*\\)[^(),]*)?)*)?)\\)");

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
    }

    private static Stream<Path> corpus() throws IOException {
        try (var files = Files.list(CORPUS_DIRECTORY)) {
            // README.md documents the corpus but is not itself corpus content; it also happens to contain the
            // literal task-syntax example `[task][name](refs)` in prose, which would otherwise be picked up and
            // fail the "must contain at least one task reference" / "must resolve to success" assertions.
            return files.filter(path -> path.toString().endsWith(".md") && !path.getFileName().toString().equals("README.md")).toList().stream();
        }
    }

    @ParameterizedTest
    @MethodSource("corpus")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void everyTaskResolvesWhenFeedbackIsProvided(Path corpusFile) throws Exception {
        String markdown = Files.readString(corpusFile, StandardCharsets.UTF_8);

        // One feedback per DISTINCT reference, numbered in first-appearance order. Deduplication is required for two
        // reasons: a test referenced by several tasks would otherwise trip the duplicate-name 422 rule, and the
        // client-side half of this harness rebuilds the same numbering.
        Map<String, Long> idsByReference = new LinkedHashMap<>();
        Matcher matcher = TASK_PATTERN.matcher(markdown);
        while (matcher.find()) {
            for (String reference : TestReferenceParser.splitTestReferences(matcher.group(2))) {
                idsByReference.computeIfAbsent(reference, ignored -> (long) (idsByReference.size() + 1));
            }
        }
        List<TestFeedbackInputDTO> feedbacks = idsByReference.entrySet().stream().map(entry -> new TestFeedbackInputDTO(entry.getValue(), entry.getKey(), true, null, null))
                .toList();
        assertThat(feedbacks).as("corpus file %s must contain at least one task reference", corpusFile).isNotEmpty();

        // includeCss=false: the client-side half of this harness only parses structure and statuses, so the
        // embedded CSS (problem-statement-css/embedded.css plus the KaTeX stylesheet link) carries no signal.
        // Including it would bloat every fixture and couple them to unrelated styling changes.
        var body = new ProblemStatementRenderRequestDTO(markdown, feedbacks, null, "en", false, false, false, null);
        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).as("all tasks in %s must resolve to success", corpusFile).contains("data-test-status=\"success\"");
        assertThat(result.html()).as("no task in %s may be unresolved", corpusFile).doesNotContain("data-test-status=\"not-executed\"");

        // Emit the server fixture for the client-side diff. A normal run compares against the committed fixture and
        // never rewrites it, so a rendering regression cannot silently overwrite its own baseline. Regeneration is
        // an explicit, opt-in action via -Dartemis.regenerateProblemStatementFixtures=true.
        Path fixture = FIXTURE_DIRECTORY.resolve(corpusFile.getFileName().toString().replace(".md", ".html"));
        if (Boolean.getBoolean("artemis.regenerateProblemStatementFixtures")) {
            Files.createDirectories(fixture.getParent());
            // Written with a trailing newline: .editorconfig requires insert_final_newline = true for every file in
            // this repo, so a fixture without one invites an editor or formatter to "fix" it later. The compare path
            // below strips exactly one trailing newline back off before comparing, so that eventual fix-up can never
            // break the parity gate for a reason unrelated to actual rendering differences.
            Files.writeString(fixture, result.html() + "\n", StandardCharsets.UTF_8);
            return;
        }
        assertThat(fixture).as("fixture missing - regenerate with -Dartemis.regenerateProblemStatementFixtures=true").exists();
        String fixtureContent = Files.readString(fixture, StandardCharsets.UTF_8);
        // Strip exactly one trailing newline, matching what the write path above adds. This is not a general
        // whitespace normalization: any other difference, including additional trailing newlines, still fails.
        String normalizedFixtureContent = fixtureContent.endsWith("\n") ? fixtureContent.substring(0, fixtureContent.length() - 1) : fixtureContent;
        assertThat(normalizedFixtureContent).isEqualTo(result.html());
    }
}
