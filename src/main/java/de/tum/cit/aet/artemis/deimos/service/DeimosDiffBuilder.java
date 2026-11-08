package de.tum.cit.aet.artemis.deimos.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.deimos.dto.DeimosOmissionReason;

/**
 * Renders the unified diffs Deimos sends to the model, within the configured size limits, and escapes the
 * student-controlled metadata that appears in them.
 */
final class DeimosDiffBuilder {

    /**
     * Space held back inside every diff budget for the trailing "further changed file(s) omitted" notice. Reserving it
     * up front is what lets {@link #buildDiff} treat its budget as a bound: the notice is only known after the loop, so
     * it cannot be checked against the remaining space like the sections before it. Only a budget smaller than the
     * notice itself is exceeded; the caller then rejects the section as a whole.
     */
    private static final int DIFF_OMISSION_NOTICE_RESERVED_BYTES = 128;

    /**
     * Files larger than this are not diffed at all. The output budgets bound what is sent to the model, but they
     * do not bound the work done to produce it: the snapshot is already fully in memory and {@link HistogramDiff}
     * processes complete inputs. This guard is what keeps a pathological file from dominating a run.
     */
    private final int maxFileInputBytes;

    /**
     * Maximum size of the unified diff emitted for a single file.
     */
    private final int maxFileDiffBytes;

    DeimosDiffBuilder(int maxFileInputBytes, int maxFileDiffBytes) {
        this.maxFileInputBytes = maxFileInputBytes;
        this.maxFileDiffBytes = maxFileDiffBytes;
    }

    /**
     * Outcome of diffing two snapshots.
     *
     * @param text      the rendered unified diff, empty when nothing changed
     * @param omissions the files skipped or truncated, each with the limit that actually caused it
     */
    record DiffResult(String text, List<DiffOmission> omissions) {
    }

    /**
     * A file left out of a diff or truncated in it.
     *
     * @param reason the limit that caused it
     * @param path   the affected file
     */
    record DiffOmission(DeimosOmissionReason reason, String path) {
    }

    /**
     * Builds a unified diff between two snapshots.
     * <p>
     * Emits actual hunks rather than whole file contents. Dumping full files made the payload grow with file size and
     * snapshot count, repeated the same content in the cumulative section, and described itself to the model as a diff
     * while being nothing of the sort.
     *
     * @param baseFiles     the base snapshot, path to content
     * @param targetFiles   the target snapshot, path to content
     * @param budgetInBytes the maximum size of the rendered diff in UTF-8 bytes, across all files
     * @return the rendered diff and what had to be omitted
     * @throws IOException if a diff cannot be rendered
     */
    DiffResult buildDiff(Map<String, String> baseFiles, Map<String, String> targetFiles, long budgetInBytes) throws IOException {
        var allPaths = new TreeSet<String>();
        allPaths.addAll(baseFiles.keySet());
        allPaths.addAll(targetFiles.keySet());

        var sb = new StringBuilder();
        List<DiffOmission> omissions = new ArrayList<>();
        long usedBytes = 0;
        int omittedFiles = 0;
        // Held back so the trailing notice, whose text is only known once the loop has finished, still fits.
        long sectionBudget = Math.max(0, budgetInBytes - DIFF_OMISSION_NOTICE_RESERVED_BYTES);

        for (String path : allPaths) {
            // A path missing on one side is an added or deleted file: the snapshot reader never stores null content.
            FileSection file = renderFile(path, baseFiles.get(path), targetFiles.get(path), sectionBudget - usedBytes);
            omissions.addAll(file.omissions());
            if (file.countsAsOmitted()) {
                omittedFiles++;
            }
            sb.append(file.text());
            usedBytes += utf8Length(file.text());
        }

        if (omittedFiles > 0) {
            sb.append("[... %d further changed file(s) omitted to stay within the size limit ...]%n".formatted(omittedFiles));
        }

        return new DiffResult(sb.toString().stripTrailing(), omissions);
    }

    /**
     * What one file contributes to a diff.
     *
     * @param text            the rendered section, empty when the file contributes none
     * @param omissions       what was left out of the file, each with the limit that actually caused it
     * @param countsAsOmitted whether the file belongs to the files the trailing notice says were omitted
     */
    private record FileSection(String text, List<DiffOmission> omissions, boolean countsAsOmitted) {

        private static final FileSection UNCHANGED = new FileSection("", List.of(), false);

        private static FileSection omitted(DiffOmission omission) {
            return new FileSection("", List.of(omission), true);
        }
    }

    /**
     * Renders one file within what is left of the section budget.
     *
     * @param path           the file
     * @param baseContent    its base content, or {@code null} if it was added
     * @param targetContent  its target content, or {@code null} if it was deleted
     * @param remainingBytes what is left of the section budget
     * @return the file's section and omissions
     */
    private FileSection renderFile(String path, @Nullable String baseContent, @Nullable String targetContent, long remainingBytes) throws IOException {
        if (baseContent != null && baseContent.equals(targetContent)) {
            return FileSection.UNCHANGED;
        }
        // A per-file cap alone does not bound the payload: many individually capped diffs still add up. Stop once the
        // budget for this section is spent, and say how many files were dropped. Placed after the identity check above
        // so files unchanged between the two snapshots are never counted as omitted.
        if (remainingBytes <= 0) {
            return FileSection.omitted(new DiffOmission(DeimosOmissionReason.PAYLOAD_LIMIT, path));
        }

        String safePath = escapeMetadata(path);
        String headers = (baseContent == null ? "--- /dev/null" : "--- a/" + safePath) + System.lineSeparator() + (targetContent == null ? "+++ /dev/null" : "+++ b/" + safePath)
                + System.lineSeparator();
        String oldContent = baseContent != null ? baseContent : "";
        String newContent = targetContent != null ? targetContent : "";
        if (utf8Length(oldContent) > maxFileInputBytes || utf8Length(newContent) > maxFileInputBytes) {
            return renderOversizedFile(path, headers, remainingBytes);
        }

        String unifiedDiff = unifiedDiff(oldContent, newContent);
        if (unifiedDiff.isEmpty()) {
            return FileSection.UNCHANGED;
        }
        return renderDiff(path, headers, unifiedDiff, remainingBytes);
    }

    private FileSection renderOversizedFile(String path, String headers, long remainingBytes) {
        String notice = headers + "[file omitted: exceeds the " + maxFileInputBytes + " byte analysis size limit]" + System.lineSeparator() + System.lineSeparator();
        // Its content would never have been diffed, so the input limit is what the model misses it to, whether or not the
        // notice still fits.
        var omission = new DiffOmission(DeimosOmissionReason.FILE_INPUT_LIMIT, path);
        if (utf8Length(notice) > remainingBytes) {
            return new FileSection("", List.of(omission), true);
        }
        return new FileSection(notice, List.of(omission), false);
    }

    private FileSection renderDiff(String path, String headers, String unifiedDiff, long remainingBytes) {
        // Bounded by whichever is smaller: the per-file cap, or what is left of this section's overall budget. The file
        // headers and the trailing separator are appended alongside the diff, so they are subtracted here rather than
        // added on top afterwards, which would let the rendered section outgrow the caller's budget.
        int fixedSectionBytes = utf8Length(headers) + utf8Length(System.lineSeparator());
        long remainingDiffBytes = Math.max(0, remainingBytes - fixedSectionBytes);
        int allowedDiffBytes = (int) Math.min(maxFileDiffBytes, remainingDiffBytes);
        String body = unifiedDiff;
        List<DiffOmission> truncation = List.of();
        if (utf8Length(unifiedDiff) > allowedDiffBytes) {
            String truncationNotice = "[... diff truncated at the " + allowedDiffBytes + " byte limit ...]" + System.lineSeparator();
            body = truncateAtLineBoundary(unifiedDiff, Math.max(0, allowedDiffBytes - utf8Length(truncationNotice))) + truncationNotice;
            // Attributed to the limit that actually bound: the per-file cap only when it was the smaller of the two.
            DeimosOmissionReason reason = maxFileDiffBytes <= remainingDiffBytes ? DeimosOmissionReason.FILE_DIFF_LIMIT : DeimosOmissionReason.PAYLOAD_LIMIT;
            truncation = List.of(new DiffOmission(reason, path));
        }

        String section = headers + body + System.lineSeparator();
        // Even a fully truncated diff still carries the headers, and a long student-controlled path can make those headers
        // alone exceed the remaining budget. Omit the file rather than append an over-budget section, so the payload stays
        // bounded regardless of path length. The truncation of a section that is not sent is not what the model misses.
        if (utf8Length(section) > remainingBytes) {
            return FileSection.omitted(new DiffOmission(DeimosOmissionReason.PAYLOAD_LIMIT, path));
        }
        return new FileSection(section, truncation, false);
    }

    /**
     * Renders a unified diff between two file contents using JGit, without touching a repository.
     *
     * @param oldContent the base content
     * @param newContent the target content
     * @return the unified diff hunks, without file headers
     */
    private static String unifiedDiff(String oldContent, String newContent) throws IOException {
        RawText oldText = oldContent.isEmpty() ? RawText.EMPTY_TEXT : new RawText(oldContent.getBytes(StandardCharsets.UTF_8));
        RawText newText = newContent.isEmpty() ? RawText.EMPTY_TEXT : new RawText(newContent.getBytes(StandardCharsets.UTF_8));
        EditList edits = new HistogramDiff().diff(RawTextComparator.DEFAULT, oldText, newText);
        if (edits.isEmpty()) {
            return "";
        }
        try (var outputStream = new ByteArrayOutputStream(); var formatter = new DiffFormatter(outputStream)) {
            // DiffFormatter.format(EditList, RawText, RawText) writes hunks only; the file headers are written by the caller.
            formatter.format(edits, oldText, newText);
            formatter.flush();
            return outputStream.toString(StandardCharsets.UTF_8);
        }
    }

    /**
     * Escapes newlines and control characters in student-influenced metadata such as file paths.
     * <p>
     * Git allows newlines and control characters in paths, so an unescaped path could forge a section header inside the
     * prompt. The value is escaped rather than rejected: refusing a hostile filename would let a student suppress the
     * analysis of their own participation, which is exactly the evasion this feature exists to catch.
     *
     * @param value the raw value
     * @return the value with newlines and control characters rendered visibly
     */
    static String escapeMetadata(String value) {
        if (value == null) {
            return "";
        }
        var sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char currentChar = value.charAt(i);
            switch (currentChar) {
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    // C0 controls and DEL, C1 controls (0x80-0x9F, including NEL U+0085), and the Unicode line and
                    // paragraph separators U+2028/U+2029. All of these render as a line break somewhere in the
                    // toolchain and could otherwise be used to forge a structural line inside the payload.
                    boolean isControl = currentChar < 0x20 || currentChar == 0x7F || (currentChar >= 0x80 && currentChar <= 0x9F);
                    // Compared numerically on purpose: written as Unicode escapes in a char literal, these characters
                    // break source tooling such as the formatter's import cleanup.
                    boolean isLineSeparator = currentChar == 0x2028 || currentChar == 0x2029;
                    if (isControl || isLineSeparator) {
                        sb.append("\\u%04x".formatted((int) currentChar));
                    }
                    else {
                        sb.append(currentChar);
                    }
                }
            }
        }
        return sb.toString();
    }

    static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * Truncates text to at most {@code maxBytes} UTF-8 bytes, cutting at a line boundary so no character and no diff
     * line is split in half. Only when not even the first line fits is that line cut at a character boundary.
     *
     * @param text     the text to truncate
     * @param maxBytes the byte budget
     * @return the truncated text, always ending with a line separator when non-empty
     */
    static String truncateAtLineBoundary(String text, int maxBytes) {
        var sb = new StringBuilder();
        int usedBytes = 0;
        for (String line : text.split("\n", -1)) {
            String lineWithSeparator = line + "\n";
            int lineBytes = utf8Length(lineWithSeparator);
            if (usedBytes + lineBytes > maxBytes) {
                break;
            }
            sb.append(lineWithSeparator);
            usedBytes += lineBytes;
        }

        // Minified sources and one-line JSON have no line break to cut at, so a purely line-based truncation would
        // emit nothing at all and hand the model an empty diff. Fall back to a hard cut on a character boundary so
        // at least the beginning of the change stays visible.
        if (sb.isEmpty() && !text.isEmpty()) {
            return truncateAtCharacterBoundary(text, maxBytes);
        }
        return sb.toString();
    }

    /**
     * Truncates text to at most {@code maxBytes} UTF-8 bytes without splitting a character.
     *
     * @param text     the text to truncate
     * @param maxBytes the byte budget
     * @return the truncated text, ending with a line separator, or empty when not even the separator fits
     */
    private static String truncateAtCharacterBoundary(String text, int maxBytes) {
        if (maxBytes < 1) {
            return "";
        }
        var sb = new StringBuilder();
        int usedBytes = 0;
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            int charCount = Character.charCount(codePoint);
            int codePointBytes = utf8Length(text.substring(index, index + charCount));
            // Reserve one byte for the line separator appended below.
            if (usedBytes + codePointBytes + 1 > maxBytes) {
                break;
            }
            sb.appendCodePoint(codePoint);
            usedBytes += codePointBytes;
            index += charCount;
        }
        return sb.append('\n').toString();
    }
}
