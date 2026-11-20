package de.tum.cit.aet.artemis.deimos.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.tum.cit.aet.artemis.deimos.dto.DeimosOmissionReason;
import de.tum.cit.aet.artemis.deimos.service.DeimosDiffBuilder.DiffOmission;
import de.tum.cit.aet.artemis.deimos.service.DeimosDiffBuilder.DiffResult;

/**
 * Pins the exact text and the ordered omissions of the diff sections at every budget boundary, so the payload the model
 * receives cannot change unnoticed.
 */
class DeimosDiffBuilderTest {

    private static final String NL = System.lineSeparator();

    /**
     * The space {@link DeimosDiffBuilder} holds back for its trailing notice.
     */
    private static final int RESERVED = 128;

    /**
     * {@code a.txt} changed from {@code x} to {@code y}: two 12-byte headers, an 18-byte hunk and the separator.
     */
    private static final String A_SECTION = "--- a/a.txt" + NL + "+++ b/a.txt" + NL + "@@ -1 +1 @@\n-x\n+y\n" + NL;

    private static final String B_SECTION = "--- a/b.txt" + NL + "+++ b/b.txt" + NL + "@@ -1 +1 @@\n-x\n+y\n" + NL;

    private static final DeimosDiffBuilder BUILDER = new DeimosDiffBuilder(1000, 1000);

    @Test
    void rendersChangedAddedAndDeletedFilesAndSkipsUnchangedOnes() throws Exception {
        var base = Map.of("a.txt", "x\n", "b.txt", "same\n", "c.txt", "gone\n");
        var target = Map.of("a.txt", "y\n", "b.txt", "same\n", "d.txt", "new\n");

        DiffResult result = BUILDER.buildDiff(base, target, 10_000);

        String expected = A_SECTION + "--- a/c.txt" + NL + "+++ /dev/null" + NL + "@@ -1 +0,0 @@\n-gone\n" + NL + "--- /dev/null" + NL + "+++ b/d.txt" + NL
                + "@@ -0,0 +1 @@\n+new\n" + NL;
        assertThat(result.text()).isEqualTo(expected.stripTrailing());
        assertThat(result.omissions()).isEmpty();
    }

    @Test
    void reservesTheNoticeSpaceOncePerDiffAndCountsTheSeparators() throws Exception {
        var base = Map.of("a.txt", "x\n", "b.txt", "x\n");
        var target = Map.of("a.txt", "y\n", "b.txt", "y\n");
        int bothSections = utf8(A_SECTION) + utf8(B_SECTION);

        DiffResult fits = BUILDER.buildDiff(base, target, RESERVED + bothSections);
        DiffResult oneByteShort = BUILDER.buildDiff(base, target, RESERVED + bothSections - 1L);

        assertThat(fits.text()).isEqualTo((A_SECTION + B_SECTION).stripTrailing());
        assertThat(fits.omissions()).isEmpty();
        assertThat(oneByteShort.text()).isEqualTo(A_SECTION + "[... 1 further changed file(s) omitted to stay within the size limit ...]");
        assertThat(oneByteShort.omissions()).containsExactly(new DiffOmission(DeimosOmissionReason.PAYLOAD_LIMIT, "b.txt"));
    }

    @Test
    void reportsAnExhaustedBudgetBeforeTheInputLimit() throws Exception {
        var builder = new DeimosDiffBuilder(16, 1000);
        var base = Map.of("a.txt", "x\n", "b.txt", "x\n");
        var target = Map.of("a.txt", "y\n", "b.txt", "y".repeat(100) + "\n");

        DiffResult result = builder.buildDiff(base, target, RESERVED + utf8(A_SECTION));

        assertThat(result.text()).isEqualTo(A_SECTION + "[... 1 further changed file(s) omitted to stay within the size limit ...]");
        assertThat(result.omissions()).containsExactly(new DiffOmission(DeimosOmissionReason.PAYLOAD_LIMIT, "b.txt"));
    }

    @Test
    void replacesAnOversizedFileWithANoticeWhileThereIsRoom() throws Exception {
        var builder = new DeimosDiffBuilder(16, 1000);
        var base = Map.of("a.txt", "x\n", "b.txt", "x\n");
        var target = Map.of("a.txt", "y\n", "b.txt", "y".repeat(100) + "\n");

        DiffResult result = builder.buildDiff(base, target, 10_000);

        assertThat(result.text()).isEqualTo(A_SECTION + "--- a/b.txt" + NL + "+++ b/b.txt" + NL + "[file omitted: exceeds the 16 byte analysis size limit]");
        assertThat(result.omissions()).containsExactly(new DiffOmission(DeimosOmissionReason.FILE_INPUT_LIMIT, "b.txt"));
    }

    @Test
    void countsAnOversizedFileAsOmittedWhenItsNoticeDoesNotFitAndRecordsOnlyTheInputLimit() throws Exception {
        var builder = new DeimosDiffBuilder(16, 1000);
        var base = Map.of("a.txt", "x\n", "b.txt", "x\n");
        var target = Map.of("a.txt", "y\n", "b.txt", "y".repeat(100) + "\n");

        DiffResult result = builder.buildDiff(base, target, RESERVED + utf8(A_SECTION) + 10L);

        assertThat(result.text()).isEqualTo(A_SECTION + "[... 1 further changed file(s) omitted to stay within the size limit ...]");
        assertThat(result.omissions()).containsExactly(new DiffOmission(DeimosOmissionReason.FILE_INPUT_LIMIT, "b.txt"));
    }

    @Test
    void keepsALaterSmallFileAfterAFileWhoseHeadersAloneDoNotFit() throws Exception {
        String longPath = "x".repeat(60) + ".txt";
        var base = Map.of(longPath, "x\n", "z.txt", "x\n");
        var target = Map.of(longPath, "y\n", "z.txt", "y\n");
        String zSection = "--- a/z.txt" + NL + "+++ b/z.txt" + NL + "@@ -1 +1 @@\n-x\n+y\n" + NL;

        DiffResult result = BUILDER.buildDiff(base, target, RESERVED + utf8(zSection) + 10L);

        assertThat(result.text()).isEqualTo(zSection + "[... 1 further changed file(s) omitted to stay within the size limit ...]");
        // Only the payload limit: the truncation of a section that is not sent is not what the model misses.
        assertThat(result.omissions()).containsExactly(new DiffOmission(DeimosOmissionReason.PAYLOAD_LIMIT, longPath));
    }

    @ParameterizedTest
    @CsvSource({ "0, FILE_DIFF_LIMIT", "1, PAYLOAD_LIMIT" })
    void attributesATruncationToTheLimitThatBound(int capAboveRemaining, DeimosOmissionReason expectedReason) throws Exception {
        String content = IntStream.range(0, 40).mapToObj(line -> "line " + line).collect(Collectors.joining("\n", "", "\n"));
        String headers = "--- /dev/null" + NL + "+++ b/a.txt" + NL;
        int remainingDiffBytes = 200;
        long budget = RESERVED + utf8(headers) + utf8(NL) + remainingDiffBytes;
        var builder = new DeimosDiffBuilder(10_000, remainingDiffBytes + capAboveRemaining);

        DiffResult result = builder.buildDiff(Map.of(), Map.of("a.txt", content), budget);

        int allowedDiffBytes = Math.min(remainingDiffBytes + capAboveRemaining, remainingDiffBytes);
        assertThat(result.text()).startsWith(headers + "@@ -0,0 +1,40 @@\n+line 0\n").endsWith("[... diff truncated at the " + allowedDiffBytes + " byte limit ...]");
        assertThat(utf8(result.text())).isLessThanOrEqualTo((int) budget - RESERVED);
        assertThat(result.omissions()).containsExactly(new DiffOmission(expectedReason, "a.txt"));
    }

    @Test
    void letsTheTrailingNoticeExceedATinyBudget() throws Exception {
        DiffResult result = BUILDER.buildDiff(Map.of("a.txt", "x\n"), Map.of("a.txt", "y\n"), 10);

        // Kept as it is: the caller rejects the whole section, so a notice longer than the budget never reaches the model.
        assertThat(result.text()).isEqualTo("[... 1 further changed file(s) omitted to stay within the size limit ...]");
        assertThat(utf8(result.text())).isGreaterThan(10);
        assertThat(result.omissions()).containsExactly(new DiffOmission(DeimosOmissionReason.PAYLOAD_LIMIT, "a.txt"));
    }

    @Test
    void escapesControlCharactersAndLineSeparatorsInMetadata() {
        assertThat(DeimosDiffBuilder.escapeMetadata(null)).isEmpty();
        assertThat(DeimosDiffBuilder.escapeMetadata("a\nb\rc\td")).isEqualTo("a\\nb\\rc\\td");
        assertThat(DeimosDiffBuilder.escapeMetadata("nul\0 esc\u001b del\u007f nel\u0085 c1\u009f")).isEqualTo("nul\\u0000 esc\\u001b del\\u007f nel\\u0085 c1\\u009f");
        assertThat(DeimosDiffBuilder.escapeMetadata("line" + (char) 0x2028 + "paragraph" + (char) 0x2029)).isEqualTo("line\\u2028paragraph\\u2029");
        assertThat(DeimosDiffBuilder.escapeMetadata("Übung/ä ö ü/日本/😀.java")).isEqualTo("Übung/ä ö ü/日本/😀.java");
    }

    @ParameterizedTest
    @CsvSource({ "0, ''", "1, ''", "4, ä", "5, äö", "6, äö", "7, äöü" })
    void cutsALineWithoutBreakAtACharacterBoundary(int maxBytes, String expectedPrefix) {
        String truncated = DeimosDiffBuilder.truncateAtLineBoundary("äöü", maxBytes);

        String expected = maxBytes == 0 ? "" : expectedPrefix + "\n";
        assertThat(truncated).isEqualTo(expected);
        assertThat(utf8(truncated)).isLessThanOrEqualTo(maxBytes);
    }

    @Test
    void neverSplitsASurrogatePairWhenCuttingALine() {
        assertThat(DeimosDiffBuilder.truncateAtLineBoundary("😀😀", 8)).isEqualTo("😀\n");
        assertThat(DeimosDiffBuilder.truncateAtLineBoundary("😀😀", 4)).isEqualTo("\n");
    }

    @Test
    void cutsAtTheLastFittingLineWhenThereIsOne() {
        assertThat(DeimosDiffBuilder.truncateAtLineBoundary("one\ntwo\nthree\n", 9)).isEqualTo("one\ntwo\n");
    }

    @Test
    void showsTheBeginningOfBothSidesWhenTheFirstChangedLineDoesNotFit() {
        String diff = "@@ -1 +1 @@\n-" + "a".repeat(300) + "\n+" + "b".repeat(300) + "\n";

        String truncated = DeimosDiffBuilder.truncateDiff(diff, 100);

        // 12 bytes of header, then 44 bytes for each side: its marker, 42 characters and the line break.
        assertThat(truncated).isEqualTo("@@ -1 +1 @@\n-" + "a".repeat(42) + "\n+" + "b".repeat(42) + "\n");
    }

    @Test
    void givesAShortSideItsWholeLineAndTheRestToTheOther() {
        String diff = "@@ -1 +1 @@\n-" + "a".repeat(300) + "\n+x\n";

        String truncated = DeimosDiffBuilder.truncateDiff(diff, 100);

        assertThat(truncated).isEqualTo("@@ -1 +1 @@\n-" + "a".repeat(83) + "\n+x\n");
    }

    @Test
    void givesTheWholeBudgetToTheOnlySideOfAnAddedFile() {
        String diff = "@@ -0,0 +1 @@\n+" + "c".repeat(300) + "\n";

        String truncated = DeimosDiffBuilder.truncateDiff(diff, 50);

        assertThat(truncated).isEqualTo("@@ -0,0 +1 @@\n+" + "c".repeat(34) + "\n");
    }

    @Test
    void skipsContextAndTheMissingNewlineMarkerWhenSampling() {
        String diff = "@@ -1,3 +1,3 @@\n " + "k".repeat(90) + "\n-" + "a".repeat(300) + "\n\\ No newline at end of file\n+" + "b".repeat(300) + "\n\\ No newline at end of file\n";

        String truncated = DeimosDiffBuilder.truncateDiff(diff, 120);

        assertThat(truncated).isEqualTo("@@ -1,3 +1,3 @@\n-" + "a".repeat(50) + "\n+" + "b".repeat(50) + "\n");
    }

    @Test
    void neverSplitsACharacterWhenSampling() {
        String diff = "@@ -1 +1 @@\n-" + "ä".repeat(100) + "\n+" + "😀".repeat(100) + "\n";

        String truncated = DeimosDiffBuilder.truncateDiff(diff, 40);

        // 14 bytes per side: the marker, six two-byte characters and the line break; three four-byte characters fit in 13.
        assertThat(truncated).isEqualTo("@@ -1 +1 @@\n-" + "ä".repeat(6) + "\n+" + "😀".repeat(3) + "\n");
        assertThat(utf8(truncated)).isLessThanOrEqualTo(40);
    }

    @ParameterizedTest
    @CsvSource({ "13", "15" })
    void keepsTheLineCutWhenTheBudgetDoesNotHoldBothSides(int maxBytes) {
        String diff = "@@ -1 +1 @@\n-" + "a".repeat(300) + "\n+" + "b".repeat(300) + "\n";

        assertThat(DeimosDiffBuilder.truncateDiff(diff, maxBytes)).isEqualTo(DeimosDiffBuilder.truncateAtLineBoundary(diff, maxBytes)).isEqualTo("@@ -1 +1 @@\n");
    }

    @Test
    void keepsTheCharacterCutWhenNotEvenTheHunkHeaderFits() {
        String diff = "@@ -1 +1 @@\n-" + "a".repeat(300) + "\n+" + "b".repeat(300) + "\n";

        assertThat(DeimosDiffBuilder.truncateDiff(diff, 5)).isEqualTo("@@ -\n");
    }

    @Test
    void samplesOnlyTheFirstHunk() {
        String diff = "@@ -1 +0,0 @@\n-" + "a".repeat(300) + "\n@@ -5,0 +5 @@\n+zzz\n";

        String truncated = DeimosDiffBuilder.truncateDiff(diff, 60);

        assertThat(truncated).isEqualTo("@@ -1 +0,0 @@\n-" + "a".repeat(44) + "\n");
    }

    @Test
    void keepsCarriageReturnsAsContentWhenSampling() {
        String diff = "@@ -1 +1 @@\n-" + "a\r".repeat(150) + "\n+" + "b\r".repeat(150) + "\n";

        String truncated = DeimosDiffBuilder.truncateDiff(diff, 32);

        // Ten bytes per side: a carriage return is an ordinary character of the line, not a line break to cut at.
        assertThat(truncated).isEqualTo("@@ -1 +1 @@\n-a\ra\ra\ra\r\n+b\rb\rb\rb\r\n");
    }

    @Test
    void keepsTheCutForTinyBudgets() {
        String diff = "@@ -1 +1 @@\n-" + "a".repeat(300) + "\n+" + "b".repeat(300) + "\n";

        assertThat(DeimosDiffBuilder.truncateDiff(diff, 0)).isEmpty();
        assertThat(DeimosDiffBuilder.truncateDiff(diff, 1)).isEqualTo("\n");
    }

    @Test
    void reportsMixedOmissionsInPathOrder() throws Exception {
        var builder = new DeimosDiffBuilder(200, 60);
        var base = Map.of("a.txt", "x\n", "b.txt", "x\n", "c.txt", "x\n");
        var target = Map.of("a.txt", "y".repeat(300) + "\n", "b.txt", IntStream.range(0, 12).mapToObj(line -> "line " + line).collect(Collectors.joining("\n", "", "\n")), "c.txt",
                "y\n");

        // The notice replacing a.txt, then exactly what b.txt needs to be truncated at its 60-byte cap rather than by the
        // budget, then nothing. Derived from the separator, so the boundary holds on every platform.
        String aNotice = "--- a/a.txt" + NL + "+++ b/a.txt" + NL + "[file omitted: exceeds the 200 byte analysis size limit]" + NL + NL;
        String bHeadersAndSeparator = "--- a/b.txt" + NL + "+++ b/b.txt" + NL + NL;
        DiffResult result = builder.buildDiff(base, target, RESERVED + utf8(aNotice) + utf8(bHeadersAndSeparator) + 60L);

        assertThat(result.omissions()).containsExactly(new DiffOmission(DeimosOmissionReason.FILE_INPUT_LIMIT, "a.txt"),
                new DiffOmission(DeimosOmissionReason.FILE_DIFF_LIMIT, "b.txt"), new DiffOmission(DeimosOmissionReason.PAYLOAD_LIMIT, "c.txt"));
    }

    @Test
    void keepsTheCutOfATextWithoutHunkHeader() {
        assertThat(DeimosDiffBuilder.truncateDiff(" context only", 5)).isEqualTo(" con\n");
    }

    @Test
    void keepsTheLineCutWhenItAlreadyShowsAnAddition() {
        String diff = "@@ -1,2 +1,2 @@\n-short\n+x\n+" + "b".repeat(300) + "\n";

        assertThat(DeimosDiffBuilder.truncateDiff(diff, 100)).isEqualTo("@@ -1,2 +1,2 @@\n-short\n+x\n");
    }

    @Test
    void samplesTheAddedLineWhenTheLineCutKeepsOnlyARemoval() {
        String diff = "@@ -1,2 +1,2 @@\n-short\n+" + "b".repeat(300) + "\n";

        // 16 bytes of header and 7 for the whole removed line leave 77 for the added one: its marker, 75 characters and the line break.
        assertThat(DeimosDiffBuilder.truncateDiff(diff, 100)).isEqualTo("@@ -1,2 +1,2 @@\n-short\n+" + "b".repeat(75) + "\n");
    }

    @Test
    void keepsTheLineCutOfARemovalWhenTheSampledHunkAddsNothing() {
        String diff = "@@ -1 +0,0 @@\n-x\n@@ -5,0 +5 @@\n+" + "b".repeat(300) + "\n";

        assertThat(DeimosDiffBuilder.truncateDiff(diff, 60)).isEqualTo("@@ -1 +0,0 @@\n-x\n@@ -5,0 +5 @@\n");
    }

    @ParameterizedTest
    @CsvSource({ "0, FILE_DIFF_LIMIT", "1, PAYLOAD_LIMIT" })
    void showsBothSidesOfAMinifiedFileUnderEitherLimit(int capAboveRemaining, DeimosOmissionReason expectedReason) throws Exception {
        String headers = "--- a/app.min.js" + NL + "+++ b/app.min.js" + NL;
        int remainingDiffBytes = 300;
        long budget = RESERVED + utf8(headers) + utf8(NL) + remainingDiffBytes;
        var builder = new DeimosDiffBuilder(10_000, remainingDiffBytes + capAboveRemaining);

        DiffResult result = builder.buildDiff(Map.of("app.min.js", "var a=" + "1".repeat(2000) + ";"), Map.of("app.min.js", "var b=" + "2".repeat(2000) + ";"), budget);

        assertThat(result.text()).startsWith(headers + "@@ -1 +1 @@\n-var a=111").contains("\n+var b=222").endsWith("[... diff truncated at the 300 byte limit ...]");
        assertThat(utf8(result.text())).isLessThanOrEqualTo((int) budget - RESERVED);
        assertThat(result.omissions()).containsExactly(new DiffOmission(expectedReason, "app.min.js"));
    }

    @Test
    void showsTheBeginningOfADeletedMinifiedFile() throws Exception {
        var builder = new DeimosDiffBuilder(10_000, 200);

        DiffResult result = builder.buildDiff(Map.of("data.json", "{\"k\":\"" + "v".repeat(2000) + "\"}"), Map.of(), 10_000);

        assertThat(result.text()).startsWith("--- a/data.json" + NL + "+++ /dev/null" + NL + "@@ -1 +0,0 @@\n-{\"k\":\"vvv");
        assertThat(result.text().substring(result.text().indexOf("@@"))).doesNotContain("\n+");
        assertThat(result.omissions()).containsExactly(new DiffOmission(DeimosOmissionReason.FILE_DIFF_LIMIT, "data.json"));
    }

    private static int utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
