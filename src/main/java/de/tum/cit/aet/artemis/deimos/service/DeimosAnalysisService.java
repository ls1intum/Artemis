package de.tum.cit.aet.artemis.deimos.service;

import static de.tum.cit.aet.artemis.core.config.Constants.SET_UP_TEMPLATE_FOR_EXERCISE;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.deimos.config.DeimosEnabled;
import de.tum.cit.aet.artemis.deimos.dto.DeimosBatchScope;
import de.tum.cit.aet.artemis.deimos.dto.DeimosBatchSummaryDTO;
import de.tum.cit.aet.artemis.deimos.dto.DeimosFailureType;
import de.tum.cit.aet.artemis.deimos.dto.DeimosLlmRequest;
import de.tum.cit.aet.artemis.deimos.dto.DeimosLlmResponse;
import de.tum.cit.aet.artemis.deimos.dto.DeimosTriggerType;
import de.tum.cit.aet.artemis.deimos.exception.DeimosLlmException;
import de.tum.cit.aet.artemis.deimos.exception.DeimosSnapshotHistoryException;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

@Conditional(DeimosEnabled.class)
@Lazy
@Service
public class DeimosAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(DeimosAnalysisService.class);

    private static final String SYSTEM_PROMPT_PATH = "prompts/deimos/analyze_submission_system.st";

    private static final String USER_PROMPT_PATH = "prompts/deimos/analyze_submission_user.st";

    /**
     * Files larger than this are not diffed at all. The output budgets below bound what is sent to the model, but they
     * do not bound the work done to produce it: the snapshot is already fully in memory and {@link HistogramDiff}
     * processes complete inputs. This guard is what keeps a pathological file from dominating a run.
     */
    private static final int MAX_FILE_INPUT_BYTES = 256 * 1024;

    /**
     * Maximum size of the unified diff emitted for a single file.
     */
    private static final int MAX_FILE_DIFF_BYTES = 32 * 1024;

    /**
     * Maximum size of the whole untrusted payload handed to the model.
     */
    private static final int MAX_PAYLOAD_BYTES = 128 * 1024;

    /**
     * Share of {@link #MAX_PAYLOAD_BYTES} held back for the final cumulative diff. Without this reservation a
     * participation with many snapshots would spend the whole budget on its earliest commits and the model would judge
     * a chronological prefix while never seeing the final state.
     */
    private static final int FINAL_STATE_RESERVED_BYTES = 32 * 1024;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final DeimosLlmClient deimosLlmClient;

    private final DeimosPromptTemplateService deimosPromptTemplateService;

    private final RepositoryService repositoryService;

    private final GitService gitService;

    public DeimosAnalysisService(ProgrammingSubmissionRepository programmingSubmissionRepository, StudentParticipationRepository studentParticipationRepository,
            DeimosLlmClient deimosLlmClient, DeimosPromptTemplateService deimosPromptTemplateService, RepositoryService repositoryService, GitService gitService) {
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.deimosLlmClient = deimosLlmClient;
        this.deimosPromptTemplateService = deimosPromptTemplateService;
        this.repositoryService = repositoryService;
        this.gitService = gitService;
    }

    /**
     * Analyzes the provided participation ids and returns an aggregated run summary.
     *
     * @param runId            the unique id of the current batch run
     * @param triggerType      the trigger type that started the analysis
     * @param scope            the scope the run belongs to (course or exercise)
     * @param from             the start of the selected analysis window
     * @param to               the end of the selected analysis window
     * @param participationIds the participation ids to analyze
     * @return the aggregated analysis summary for the run
     */
    public DeimosBatchSummaryDTO analyze(String runId, DeimosTriggerType triggerType, DeimosBatchScope scope, ZonedDateTime from, ZonedDateTime to, List<Long> participationIds) {
        long analyzed = 0;
        long failed = 0;
        long maliciousCount = 0;
        long benignCount = 0;
        List<DeimosBatchSummaryDTO.ParticipationAnalysis> analyzedParticipations = new ArrayList<>();
        List<DeimosBatchSummaryDTO.FailedAnalysis> failedAnalyses = new ArrayList<>();

        for (Long participationId : participationIds) {
            try {
                var participation = studentParticipationRepository.findById(participationId).orElseThrow();
                if (!(participation instanceof ProgrammingExerciseParticipation programmingParticipation)) {
                    log.warn("Participation {} is not a ProgrammingExerciseParticipation, skipping", participationId);
                    failed++;
                    failedAnalyses.add(new DeimosBatchSummaryDTO.FailedAnalysis(participationId, DeimosFailureType.OTHER, "Not a programming exercise participation"));
                    continue;
                }

                String sentinel = generateSentinel();
                SnapshotHistory snapshotHistory = buildSnapshotHistory(participationId, programmingParticipation);
                if (snapshotHistory.payload().isBlank()) {
                    log.info("Skipping Deimos analysis for participation {}: no observed snapshot history", participationId);
                    failed++;
                    failedAnalyses.add(
                            new DeimosBatchSummaryDTO.FailedAnalysis(participationId, DeimosFailureType.NO_SNAPSHOT_HISTORY, "No observed submission snapshot history available"));
                    continue;
                }

                DeimosLlmRequest request = buildPrompt(participationId, snapshotHistory.payload(), sentinel);
                DeimosLlmResponse response = deimosLlmClient.analyze(request);
                long exerciseId = 0L;
                if (programmingParticipation.getProgrammingExercise() != null && programmingParticipation.getProgrammingExercise().getId() != null) {
                    exerciseId = programmingParticipation.getProgrammingExercise().getId();
                }
                analyzedParticipations.add(new DeimosBatchSummaryDTO.ParticipationAnalysis(participationId, exerciseId, response.malicious(), response.rationale()));

                analyzed++;
                if (response.malicious()) {
                    maliciousCount++;
                }
                else {
                    benignCount++;
                }
            }
            catch (Exception ex) {
                failed++;
                failedAnalyses.add(new DeimosBatchSummaryDTO.FailedAnalysis(participationId, classifyFailure(ex), ex.getClass().getSimpleName() + ": " + ex.getMessage()));
                log.warn("Deimos analysis failed for participation {}", participationId, ex);
            }
        }

        return new DeimosBatchSummaryDTO(runId, triggerType.name(), scope.name(), from, to, participationIds.size(), analyzed, maliciousCount, benignCount, failed,
                List.copyOf(analyzedParticipations), List.copyOf(failedAnalyses));
    }

    /**
     * Maps a thrown exception onto a reportable failure type.
     *
     * @param exception the exception thrown while analysing one participation
     * @return the matching failure type
     */
    private static DeimosFailureType classifyFailure(Exception exception) {
        if (exception instanceof DeimosLlmException llmException) {
            return llmException.getFailureType();
        }
        if (exception instanceof DeimosSnapshotHistoryException) {
            return DeimosFailureType.SNAPSHOT_HISTORY_ERROR;
        }
        return DeimosFailureType.OTHER;
    }

    /**
     * Generates a 128-bit sentinel used to delimit the untrusted payload in the prompt.
     *
     * @return the sentinel as a hex string
     */
    private static String generateSentinel() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private DeimosLlmRequest buildPrompt(long participationId, String snapshotHistory, String sentinel) {
        // A sentinel occurring inside the payload would let a student close the untrusted region early and have the rest
        // of their file read as instructions. Regenerate rather than strip, so the evidence is never altered.
        String effectiveSentinel = sentinel;
        while (snapshotHistory.contains(effectiveSentinel)) {
            effectiveSentinel = generateSentinel();
        }
        String systemPrompt = deimosPromptTemplateService.render(SYSTEM_PROMPT_PATH, Map.of("sentinel", effectiveSentinel));
        String userPrompt = deimosPromptTemplateService.render(USER_PROMPT_PATH,
                Map.of("participationId", String.valueOf(participationId), "sentinel", effectiveSentinel, "snapshotHistory", snapshotHistory));
        return new DeimosLlmRequest(participationId, systemPrompt, userPrompt);
    }

    /**
     * Result of reconstructing a participation's observed snapshot history.
     *
     * @param payload           the rendered history, empty when there is genuinely nothing to analyse
     * @param emittedSnapshots  how many snapshots contributed a diff
     * @param omissionsOccurred whether anything was skipped or truncated to stay inside the size budgets
     */
    private record SnapshotHistory(String payload, int emittedSnapshots, boolean omissionsOccurred) {
    }

    /**
     * Reconstructs the observed submission snapshot history of a participation as unified diffs.
     * <p>
     * An empty payload means there is genuinely nothing to analyse (no snapshots, or no observed change). Anything that
     * went wrong throws {@link DeimosSnapshotHistoryException} instead, so a repository failure is never reported as a
     * clean participation.
     *
     * @param participationId the participation to reconstruct
     * @param participation   the programming participation owning the repository
     * @return the reconstructed history
     * @throws DeimosSnapshotHistoryException if the repository or any snapshot could not be read
     */
    private SnapshotHistory buildSnapshotHistory(long participationId, ProgrammingExerciseParticipation participation) {
        if (participation.getProgrammingExercise() == null) {
            throw new DeimosSnapshotHistoryException("Participation " + participationId + " has no programming exercise");
        }
        if (participation.getVcsRepositoryUri() == null) {
            throw new DeimosSnapshotHistoryException("Participation " + participationId + " has no repository URI");
        }

        List<ProgrammingSubmission> submissions = programmingSubmissionRepository.findByParticipationIdOrderBySubmissionDateAsc(participationId);
        if (submissions.isEmpty()) {
            return new SnapshotHistory("", 0, false);
        }

        try (Repository repository = gitService.getBareRepository(participation.getVcsRepositoryUri(), false)) {
            String setupCommitHash = gitService.getFirstCommitWithMessage(repository, SET_UP_TEMPLATE_FOR_EXERCISE);
            if (setupCommitHash == null) {
                log.warn("No setup commit found for participation {}, falling back to empty template", participationId);
            }

            Map<String, String> templateFiles = setupCommitHash != null ? readSnapshot(repository, setupCommitHash) : Map.of();

            var sb = new StringBuilder();
            int usedBytes = 0;
            int incrementalBudget = MAX_PAYLOAD_BYTES - FINAL_STATE_RESERVED_BYTES;
            Map<String, String> previousFiles = templateFiles;
            int emittedSnapshots = 0;
            int omittedSnapshots = 0;
            boolean omissionsOccurred = false;

            for (ProgrammingSubmission submission : submissions) {
                if (submission.getCommitHash() == null || submission.getCommitHash().isBlank()) {
                    // A submission without a commit hash cannot be examined. Record it rather than skipping silently,
                    // so it cannot masquerade as a participation that simply never changed anything.
                    log.warn("Submission {} of participation {} has no commit hash, skipping snapshot", submission.getId(), participationId);
                    omissionsOccurred = true;
                    continue;
                }

                Map<String, String> currentFiles = readSnapshot(repository, submission.getCommitHash());
                var diffResult = buildDiff(previousFiles, currentFiles, MAX_PAYLOAD_BYTES);
                previousFiles = currentFiles;

                if (diffResult.text().isEmpty()) {
                    continue;
                }
                omissionsOccurred |= diffResult.omissionsOccurred();

                String header = "=== Snapshot %d (%s, %s) ===%n".formatted(emittedSnapshots + 1, escapeMetadata(shortHash(submission.getCommitHash())),
                        escapeMetadata(submission.getSubmissionDate() != null ? submission.getSubmissionDate().toString() : "unknown"));
                String section = header + diffResult.text() + System.lineSeparator() + System.lineSeparator();
                int sectionBytes = utf8Length(section);

                if (usedBytes + sectionBytes > incrementalBudget) {
                    omittedSnapshots++;
                    omissionsOccurred = true;
                    continue;
                }
                sb.append(section);
                usedBytes += sectionBytes;
                emittedSnapshots++;
            }

            if (omittedSnapshots > 0) {
                String omissionNotice = "[... %d snapshot(s) omitted to stay within the size limit ...]%n%n".formatted(omittedSnapshots);
                sb.append(omissionNotice);
                usedBytes += utf8Length(omissionNotice);
            }

            // Recomputed against the template so the model always sees where the participation ended up, even when
            // intermediate snapshots were dropped above. The remaining budget is at least FINAL_STATE_RESERVED_BYTES,
            // because the incremental loop above was capped at MAX_PAYLOAD_BYTES - FINAL_STATE_RESERVED_BYTES.
            String cumulativeHeader = "=== Final state vs. exercise template ===" + System.lineSeparator();
            int cumulativeBudget = Math.max(0, MAX_PAYLOAD_BYTES - usedBytes - utf8Length(cumulativeHeader));
            var cumulativeResult = buildDiff(templateFiles, previousFiles, cumulativeBudget);
            boolean cumulativeIsRedundant = emittedSnapshots == 1 && omittedSnapshots == 0 && !omissionsOccurred && !cumulativeResult.omissionsOccurred();
            if (!cumulativeResult.text().isEmpty() && !cumulativeIsRedundant) {
                omissionsOccurred |= cumulativeResult.omissionsOccurred();
                sb.append(cumulativeHeader);
                sb.append(cumulativeResult.text());
            }

            return new SnapshotHistory(sb.toString().stripTrailing(), emittedSnapshots, omissionsOccurred);
        }
        catch (DeimosSnapshotHistoryException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new DeimosSnapshotHistoryException("Could not build snapshot history for participation " + participationId, ex);
        }
    }

    /**
     * Reads one snapshot, failing loudly when the commit cannot be resolved.
     * <p>
     * {@link RepositoryService#getFilesContentFromBareRepository} returns an empty map for an unresolvable hash, which
     * is indistinguishable from an empty repository. Left unchecked, a garbage-collected or rewritten commit would be
     * rendered as "the student deleted every file", or as "nothing to analyse", instead of being reported as an error.
     *
     * @param repository the bare repository
     * @param commitHash the commit to read
     * @return the file contents of that commit
     * @throws DeimosSnapshotHistoryException if the commit cannot be resolved or read
     */
    private Map<String, String> readSnapshot(Repository repository, String commitHash) {
        try {
            if (repository.resolve(commitHash) == null) {
                throw new DeimosSnapshotHistoryException("Could not resolve commit " + commitHash + " in the participation repository");
            }
            return repositoryService.getFilesContentFromBareRepository(repository, commitHash);
        }
        catch (DeimosSnapshotHistoryException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new DeimosSnapshotHistoryException("Could not read commit " + commitHash + " from the participation repository", ex);
        }
    }

    /**
     * Outcome of diffing two snapshots.
     *
     * @param text              the rendered unified diff, empty when nothing changed
     * @param omissionsOccurred whether a file was skipped or a diff truncated
     */
    private record DiffResult(String text, boolean omissionsOccurred) {
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
     * @return the rendered diff and whether anything had to be omitted
     */
    private DiffResult buildDiff(Map<String, String> baseFiles, Map<String, String> targetFiles, int budgetInBytes) {
        var allPaths = new TreeSet<String>();
        allPaths.addAll(baseFiles.keySet());
        allPaths.addAll(targetFiles.keySet());

        var sb = new StringBuilder();
        boolean omissionsOccurred = false;
        int usedBytes = 0;
        int omittedFiles = 0;

        for (String path : allPaths) {
            // A per-file cap alone does not bound the payload: many individually capped diffs still add up. Stop once
            // the budget for this section is spent, and say how many files were dropped.
            if (usedBytes >= budgetInBytes) {
                omittedFiles++;
                omissionsOccurred = true;
                continue;
            }
            String baseContent = baseFiles.get(path);
            String targetContent = targetFiles.get(path);
            if (baseContent == null && targetContent == null) {
                continue;
            }
            if (baseContent != null && baseContent.equals(targetContent)) {
                continue;
            }

            String safePath = escapeMetadata(path);
            String oldHeader = baseContent == null ? "--- /dev/null" : "--- a/" + safePath;
            String newHeader = targetContent == null ? "+++ /dev/null" : "+++ b/" + safePath;

            if (utf8Length(baseContent != null ? baseContent : "") > MAX_FILE_INPUT_BYTES || utf8Length(targetContent != null ? targetContent : "") > MAX_FILE_INPUT_BYTES) {
                String notice = oldHeader + System.lineSeparator() + newHeader + System.lineSeparator() + "[file omitted: exceeds the " + MAX_FILE_INPUT_BYTES
                        + " byte analysis size limit]" + System.lineSeparator() + System.lineSeparator();
                sb.append(notice);
                usedBytes += utf8Length(notice);
                omissionsOccurred = true;
                continue;
            }

            String unifiedDiff;
            try {
                unifiedDiff = unifiedDiff(baseContent != null ? baseContent : "", targetContent != null ? targetContent : "");
            }
            catch (Exception ex) {
                throw new DeimosSnapshotHistoryException("Could not build diff for file " + path, ex);
            }
            if (unifiedDiff.isEmpty()) {
                continue;
            }

            // Bounded by whichever is smaller: the per-file cap, or what is left of this section's overall budget.
            int allowedDiffBytes = Math.min(MAX_FILE_DIFF_BYTES, budgetInBytes - usedBytes);
            if (utf8Length(unifiedDiff) > allowedDiffBytes) {
                unifiedDiff = truncateAtLineBoundary(unifiedDiff, allowedDiffBytes) + "[... diff truncated at the " + allowedDiffBytes + " byte limit ...]"
                        + System.lineSeparator();
                omissionsOccurred = true;
            }

            String section = oldHeader + System.lineSeparator() + newHeader + System.lineSeparator() + unifiedDiff + System.lineSeparator();
            sb.append(section);
            usedBytes += utf8Length(section);
        }

        if (omittedFiles > 0) {
            sb.append("[... %d further changed file(s) omitted to stay within the size limit ...]%n".formatted(omittedFiles));
        }

        return new DiffResult(sb.toString().stripTrailing(), omissionsOccurred);
    }

    /**
     * Renders a unified diff between two file contents using JGit, without touching a repository.
     *
     * @param oldContent the base content
     * @param newContent the target content
     * @return the unified diff hunks, without file headers
     */
    private static String unifiedDiff(String oldContent, String newContent) throws Exception {
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
                    // Compared numerically on purpose: a '\u2028' char literal would be expanded by the Java lexer
                    // into a real line terminator before parsing and would not compile.
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

    private static String shortHash(String commitHash) {
        return commitHash.substring(0, Math.min(8, commitHash.length()));
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * Truncates text to at most {@code maxBytes} UTF-8 bytes, cutting only at a line boundary so no character and no
     * diff line is split in half.
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
     * @return the truncated text, ending with a line separator
     */
    private static String truncateAtCharacterBoundary(String text, int maxBytes) {
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
