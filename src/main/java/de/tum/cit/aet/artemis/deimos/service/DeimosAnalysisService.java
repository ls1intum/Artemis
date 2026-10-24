package de.tum.cit.aet.artemis.deimos.service;

import static de.tum.cit.aet.artemis.core.config.Constants.SET_UP_TEMPLATE_FOR_EXERCISE;
import static de.tum.cit.aet.artemis.deimos.config.DeimosLimits.DEFAULT_FINAL_STATE_RESERVED_BYTES;
import static de.tum.cit.aet.artemis.deimos.config.DeimosLimits.DEFAULT_MAX_FILE_DIFF_BYTES;
import static de.tum.cit.aet.artemis.deimos.config.DeimosLimits.DEFAULT_MAX_FILE_INPUT_BYTES;
import static de.tum.cit.aet.artemis.deimos.config.DeimosLimits.DEFAULT_MAX_PAYLOAD_BYTES;
import static de.tum.cit.aet.artemis.deimos.config.DeimosLimits.FINAL_STATE_RESERVED_BYTES_PROPERTY;
import static de.tum.cit.aet.artemis.deimos.config.DeimosLimits.MAX_FILE_DIFF_BYTES_PROPERTY;
import static de.tum.cit.aet.artemis.deimos.config.DeimosLimits.MAX_FILE_INPUT_BYTES_PROPERTY;
import static de.tum.cit.aet.artemis.deimos.config.DeimosLimits.MAX_PAYLOAD_BYTES_PROPERTY;
import static de.tum.cit.aet.artemis.deimos.dto.DeimosBatchSummaryDTO.BATCH_LEVEL_FAILURE_PARTICIPATION_ID;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.deimos.config.DeimosEnabled;
import de.tum.cit.aet.artemis.deimos.dto.DeimosBatchScope;
import de.tum.cit.aet.artemis.deimos.dto.DeimosBatchSummaryDTO;
import de.tum.cit.aet.artemis.deimos.dto.DeimosBatchSummaryDTO.FailedAnalysis;
import de.tum.cit.aet.artemis.deimos.dto.DeimosBatchSummaryDTO.ParticipationAnalysis;
import de.tum.cit.aet.artemis.deimos.dto.DeimosFailureType;
import de.tum.cit.aet.artemis.deimos.dto.DeimosLlmRequest;
import de.tum.cit.aet.artemis.deimos.dto.DeimosLlmResponse;
import de.tum.cit.aet.artemis.deimos.dto.DeimosManipulationFinding;
import de.tum.cit.aet.artemis.deimos.dto.DeimosOmission;
import de.tum.cit.aet.artemis.deimos.dto.DeimosOmissionReason;
import de.tum.cit.aet.artemis.deimos.dto.DeimosTriggerType;
import de.tum.cit.aet.artemis.deimos.dto.DeimosVerdict;
import de.tum.cit.aet.artemis.deimos.exception.DeimosLlmException;
import de.tum.cit.aet.artemis.deimos.exception.DeimosSnapshotHistoryException;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.localvc.service.BareGitRepositoryService;
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
     * Space held back inside every diff budget for the trailing "further changed file(s) omitted" notice. Reserving it
     * up front is what lets {@link #buildDiff} treat its budget as a hard bound: the notice is only known after the
     * loop, so it cannot be checked against the remaining space like the sections before it.
     */
    private static final int DIFF_OMISSION_NOTICE_RESERVED_BYTES = 128;

    /**
     * Length of the random sentinel fencing the untrusted payload: 16 bytes, i.e. 128 bits.
     */
    private static final int SENTINEL_BYTES = 16;

    /**
     * Number of hex digits of a commit hash shown in snapshot headers and in the completion email.
     */
    private static final int SHORT_COMMIT_HASH_LENGTH = 8;

    /**
     * Blank lines appended after every snapshot section to separate it from the next one.
     */
    private static final int SECTION_TRAILER_LINE_SEPARATORS = 2;

    private static final int MAX_REPORTED_FINDINGS_PER_PARTICIPATION = 10;

    private static final int MAX_REPORTED_OMISSIONS_PER_PARTICIPATION = 10;

    /**
     * File paths are student-controlled and can be arbitrarily long, so they are shortened before they reach the email.
     */
    private static final int MAX_REPORTED_PATH_LENGTH = 200;

    private static final String TRUNCATION_SUFFIX = "...";

    /**
     * A line imitating one of the markers that fence the untrusted payload, {@code ---BEGIN UNTRUSTED DATA <id>---} or
     * {@code ---END UNTRUSTED DATA <id>---}. Any identifier, or none, and any surrounding text are accepted, and case and
     * whitespace (including Unicode spaces) may differ, so a marker in a comment is caught as well.
     * <p>
     * The scan has to stay linear in the size of the submission. The fixed {@code -{3}} prefix makes every start
     * position fail within three characters unless it reaches the whitespace, and the possessive quantifiers never give
     * back what they consumed, so a long run of hyphens or whitespace is scanned once rather than once per start
     * position.
     */
    private static final Pattern FENCE_MARKER_PATTERN = Pattern.compile("-{3}[\\s\\p{Z}]*+(?:BEGIN|END)[\\s\\p{Z}]++UNTRUSTED[\\s\\p{Z}]++DATA", Pattern.CASE_INSENSITIVE);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final DeimosLlmClient deimosLlmClient;

    private final DeimosPromptTemplateService deimosPromptTemplateService;

    private final RepositoryService repositoryService;

    private final BareGitRepositoryService bareGitRepositoryService;

    /**
     * Files larger than this are not diffed at all. The output budgets below bound what is sent to the model, but they
     * do not bound the work done to produce it: the snapshot is already fully in memory and {@link HistogramDiff}
     * processes complete inputs. This guard is what keeps a pathological file from dominating a run.
     */
    private final int maxFileInputBytes;

    /**
     * Maximum size of the unified diff emitted for a single file.
     */
    private final int maxFileDiffBytes;

    /**
     * Maximum size of the whole untrusted payload handed to the model.
     */
    private final int maxPayloadBytes;

    /**
     * Share of {@link #maxPayloadBytes} held back for the final cumulative diff. Without this reservation a participation
     * with many snapshots would spend the whole budget on its earliest commits and the model would judge a chronological
     * prefix while never seeing the final state.
     */
    private final int finalStateReservedBytes;

    public DeimosAnalysisService(ProgrammingSubmissionRepository programmingSubmissionRepository, StudentParticipationRepository studentParticipationRepository,
            DeimosLlmClient deimosLlmClient, DeimosPromptTemplateService deimosPromptTemplateService, RepositoryService repositoryService,
            BareGitRepositoryService bareGitRepositoryService, @Value("${" + MAX_FILE_INPUT_BYTES_PROPERTY + ":" + DEFAULT_MAX_FILE_INPUT_BYTES + "}") int maxFileInputBytes,
            @Value("${" + MAX_FILE_DIFF_BYTES_PROPERTY + ":" + DEFAULT_MAX_FILE_DIFF_BYTES + "}") int maxFileDiffBytes,
            @Value("${" + MAX_PAYLOAD_BYTES_PROPERTY + ":" + DEFAULT_MAX_PAYLOAD_BYTES + "}") int maxPayloadBytes,
            @Value("${" + FINAL_STATE_RESERVED_BYTES_PROPERTY + ":" + DEFAULT_FINAL_STATE_RESERVED_BYTES + "}") int finalStateReservedBytes) {
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.deimosLlmClient = deimosLlmClient;
        this.deimosPromptTemplateService = deimosPromptTemplateService;
        this.repositoryService = repositoryService;
        this.bareGitRepositoryService = bareGitRepositoryService;
        this.maxFileInputBytes = maxFileInputBytes;
        this.maxFileDiffBytes = maxFileDiffBytes;
        this.maxPayloadBytes = maxPayloadBytes;
        this.finalStateReservedBytes = finalStateReservedBytes;
    }

    /**
     * Analyzes the provided participation ids and returns an aggregated run summary.
     * <p>
     * The run stops at the first participation for which the LLM endpoint rejects the credentials: every further request
     * would be rejected the same way. The participations not reached are reported in one batch-level failure.
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
        long manipulationAttemptCount = 0;
        long maliciousCount = 0;
        long incompletelyCheckedCount = 0;
        long benignCount = 0;
        long failed = 0;
        List<ParticipationAnalysis> analyzedParticipations = new ArrayList<>();
        List<FailedAnalysis> failedAnalyses = new ArrayList<>();

        for (int index = 0; index < participationIds.size(); index++) {
            ParticipationOutcome outcome = analyzeParticipation(participationIds.get(index));
            if (outcome.analysis() != null) {
                analyzedParticipations.add(outcome.analysis());
                analyzed++;
                switch (outcome.analysis().verdict()) {
                    case MANIPULATION_ATTEMPT -> manipulationAttemptCount++;
                    case MALICIOUS -> maliciousCount++;
                    case INCOMPLETELY_CHECKED -> incompletelyCheckedCount++;
                    case BENIGN -> benignCount++;
                }
            }
            else {
                failedAnalyses.add(outcome.failure());
                failed++;
            }

            // Decided on the model's answer alone, not on the verdict: a participation that is reported as a manipulation
            // attempt despite the rejection still means every following request would be rejected too.
            if (outcome.credentialsRejected()) {
                int notReached = participationIds.size() - index - 1;
                if (notReached > 0) {
                    failedAnalyses.add(new FailedAnalysis(BATCH_LEVEL_FAILURE_PARTICIPATION_ID, DeimosFailureType.LLM_AUTHENTICATION_FAILED,
                            "Run aborted after the LLM endpoint rejected the credentials; %d participation(s) were not analysed".formatted(notReached)));
                    failed += notReached;
                }
                log.warn("Deimos run {} aborted after the LLM endpoint rejected the credentials; {} participation(s) were not analysed", runId, notReached);
                break;
            }
        }

        return new DeimosBatchSummaryDTO(runId, triggerType.name(), scope.name(), from, to, participationIds.size(), analyzed, manipulationAttemptCount, maliciousCount,
                incompletelyCheckedCount, benignCount, failed, List.copyOf(analyzedParticipations), List.copyOf(failedAnalyses));
    }

    /**
     * Result of analysing one participation: either a verdict or a failure.
     *
     * @param analysis            the verdict, or {@code null} if the participation failed
     * @param failure             the failure, or {@code null} if the participation received a verdict
     * @param credentialsRejected whether the LLM endpoint rejected the credentials, which ends the run
     */
    private record ParticipationOutcome(@Nullable ParticipationAnalysis analysis, @Nullable FailedAnalysis failure, boolean credentialsRejected) {
    }

    private ParticipationOutcome analyzeParticipation(long participationId) {
        var evidence = new ParticipationEvidence();
        long exerciseId = 0L;
        try {
            var participation = studentParticipationRepository.findById(participationId).orElseThrow();
            if (!(participation instanceof ProgrammingExerciseParticipation programmingParticipation)) {
                log.warn("Participation {} is not a ProgrammingExerciseParticipation, skipping", participationId);
                return new ParticipationOutcome(null, new FailedAnalysis(participationId, DeimosFailureType.OTHER, "Not a programming exercise participation"), false);
            }
            if (programmingParticipation.getProgrammingExercise() != null && programmingParticipation.getProgrammingExercise().getId() != null) {
                exerciseId = programmingParticipation.getProgrammingExercise().getId();
            }

            String sentinel = generateSentinel();
            SnapshotHistory snapshotHistory = buildSnapshotHistory(participationId, programmingParticipation, sentinel, evidence);
            if (snapshotHistory.payload().isBlank()) {
                log.info("Skipping Deimos analysis for participation {}: no observed snapshot history", participationId);
                // An empty file whose name imitates a marker produces no diff, yet it is still a finding.
                if (evidence.hasFindings()) {
                    return new ParticipationOutcome(
                            toAnalysis(participationId, exerciseId, DeimosVerdict.MANIPULATION_ATTEMPT, null, DeimosFailureType.NO_SNAPSHOT_HISTORY, evidence), null, false);
                }
                return new ParticipationOutcome(null,
                        new FailedAnalysis(participationId, DeimosFailureType.NO_SNAPSHOT_HISTORY, "No observed submission snapshot history available"), false);
            }

            DeimosLlmRequest request = buildPrompt(participationId, snapshotHistory.payload(), sentinel);
            DeimosLlmResponse response = deimosLlmClient.analyze(request);
            return new ParticipationOutcome(toAnalysis(participationId, exerciseId, resolveVerdict(evidence, response), response, null, evidence), null, false);
        }
        catch (Exception ex) {
            DeimosFailureType failureType = classifyFailure(ex);
            boolean credentialsRejected = failureType == DeimosFailureType.LLM_AUTHENTICATION_FAILED;
            if (credentialsRejected) {
                // Only the fixed message carrying the HTTP status: the rejected request must not end up in the log.
                log.warn("Deimos analysis failed for participation {}: {}", participationId, ex.getMessage());
            }
            else {
                log.warn("Deimos analysis failed for participation {}", participationId, ex);
            }

            // An imitated fence marker is found without the model and from snapshots already read, so it holds whatever
            // stopped the analysis afterwards. The failure is kept as additional information.
            if (evidence.hasFindings()) {
                return new ParticipationOutcome(toAnalysis(participationId, exerciseId, DeimosVerdict.MANIPULATION_ATTEMPT, null, failureType, evidence), null,
                        credentialsRejected);
            }
            return new ParticipationOutcome(null, new FailedAnalysis(participationId, failureType, ex.getClass().getSimpleName() + ": " + ex.getMessage()), credentialsRejected);
        }
    }

    /**
     * Applies the verdict precedence: a manipulation attempt outranks the model, a malicious classification outranks an
     * incomplete check, and an incomplete check only replaces a benign classification.
     *
     * @param evidence what was found and omitted while building the payload
     * @param response the model's answer
     * @return the verdict to report
     */
    private static DeimosVerdict resolveVerdict(ParticipationEvidence evidence, DeimosLlmResponse response) {
        if (evidence.hasFindings()) {
            return DeimosVerdict.MANIPULATION_ATTEMPT;
        }
        if (response.malicious()) {
            return DeimosVerdict.MALICIOUS;
        }
        if (evidence.hasOmissions()) {
            return DeimosVerdict.INCOMPLETELY_CHECKED;
        }
        return DeimosVerdict.BENIGN;
    }

    private static ParticipationAnalysis toAnalysis(long participationId, long exerciseId, DeimosVerdict verdict, @Nullable DeimosLlmResponse response,
            @Nullable DeimosFailureType failureType, ParticipationEvidence evidence) {
        List<DeimosOmission> reportedOmissions = evidence.omissions.stream().limit(MAX_REPORTED_OMISSIONS_PER_PARTICIPATION).map(RecordedOmission::toDisplay).toList();
        return new ParticipationAnalysis(participationId, exerciseId, verdict, response != null ? response.malicious() : null, response != null ? response.rationale() : null,
                failureType, List.copyOf(evidence.reportedFindings), evidence.findingKeys.size(), reportedOmissions, evidence.omissions.size());
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
        byte[] bytes = new byte[SENTINEL_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private DeimosLlmRequest buildPrompt(long participationId, String snapshotHistory, String sentinel) {
        // A sentinel occurring inside the payload would let a student close the untrusted region early and have the rest
        // of their file read as instructions. Regenerate rather than strip, so the evidence is never altered.
        String effectiveSentinel = sentinel;
        while (snapshotHistory.contains(effectiveSentinel)) {
            log.warn("The prompt sentinel occurred in the payload of participation {}, regenerating it", participationId);
            effectiveSentinel = generateSentinel();
        }
        String systemPrompt = deimosPromptTemplateService.render(SYSTEM_PROMPT_PATH, Map.of("sentinel", effectiveSentinel));
        String userPrompt = deimosPromptTemplateService.render(USER_PROMPT_PATH,
                Map.of("participationId", String.valueOf(participationId), "sentinel", effectiveSentinel, "snapshotHistory", snapshotHistory));
        return new DeimosLlmRequest(participationId, systemPrompt, userPrompt);
    }

    /**
     * What was found in and left out of one participation's evidence, collected while its history is built.
     * <p>
     * Owned by the caller rather than returned with the history, so findings from the snapshots already read survive a
     * snapshot that fails afterwards.
     */
    private static final class ParticipationEvidence {

        /**
         * Keys of all distinct findings, so a marker that persists through later snapshots is counted once.
         */
        private final Set<String> findingKeys = new HashSet<>();

        private final List<DeimosManipulationFinding> reportedFindings = new ArrayList<>();

        /**
         * All distinct omissions in order of occurrence. Kept in full so the reported total is exact; only the display
         * is capped.
         */
        private final Set<RecordedOmission> omissions = new LinkedHashSet<>();

        private boolean sentinelSeen;

        void addFinding(String key, DeimosManipulationFinding finding) {
            if (findingKeys.add(key) && reportedFindings.size() < MAX_REPORTED_FINDINGS_PER_PARTICIPATION) {
                reportedFindings.add(finding);
            }
        }

        boolean hasFindings() {
            return !findingKeys.isEmpty();
        }

        boolean hasOmissions() {
            return !omissions.isEmpty();
        }
    }

    /**
     * Identifies a snapshot for the report.
     *
     * @param submissionId the submission the snapshot belongs to, which keeps two snapshots with the same label apart
     * @param label        the abbreviated commit hash and the submission date, escaped
     */
    private record SnapshotRef(@Nullable Long submissionId, String label) {
    }

    /**
     * An omission as recorded while the history is built. Compared on the raw path and the snapshot identity, so two
     * long paths that only differ after the part kept for display are still counted separately.
     *
     * @param reason   the limit that caused the omission
     * @param rawPath  the affected file, or {@code null} when a whole snapshot is affected
     * @param snapshot the affected snapshot, or {@code null} for the final state
     */
    private record RecordedOmission(DeimosOmissionReason reason, @Nullable String rawPath, @Nullable SnapshotRef snapshot) {

        DeimosOmission toDisplay() {
            return new DeimosOmission(reason, rawPath != null ? displayPath(rawPath) : null, snapshot != null ? snapshot.label() : null);
        }
    }

    /**
     * Result of reconstructing a participation's observed snapshot history.
     *
     * @param payload          the rendered history, empty when there is genuinely nothing to analyse
     * @param emittedSnapshots how many snapshots contributed a diff
     */
    private record SnapshotHistory(String payload, int emittedSnapshots) {
    }

    /**
     * Reconstructs the observed submission snapshot history of a participation as unified diffs.
     * <p>
     * An empty payload means there is genuinely nothing to analyse (no snapshots, or no observed change). Anything that
     * went wrong throws {@link DeimosSnapshotHistoryException} instead, so a repository failure is never reported as a
     * clean participation. Imitated fence markers found in the full snapshot contents and every omission caused by a
     * limit are recorded in {@code evidence}.
     *
     * @param participationId the participation to reconstruct
     * @param participation   the programming participation owning the repository
     * @param sentinel        the sentinel that will fence the payload, checked against the snapshot contents
     * @param evidence        collects findings and omissions
     * @return the reconstructed history
     * @throws DeimosSnapshotHistoryException if the repository or any snapshot could not be read
     */
    private SnapshotHistory buildSnapshotHistory(long participationId, ProgrammingExerciseParticipation participation, String sentinel, ParticipationEvidence evidence) {
        if (participation.getProgrammingExercise() == null) {
            throw new DeimosSnapshotHistoryException("Participation " + participationId + " has no programming exercise");
        }
        if (participation.getVcsRepositoryUri() == null) {
            throw new DeimosSnapshotHistoryException("Participation " + participationId + " has no repository URI");
        }

        List<ProgrammingSubmission> submissions = programmingSubmissionRepository.findByParticipationIdOrderBySubmissionDateAsc(participationId);
        if (submissions.isEmpty()) {
            return new SnapshotHistory("", 0);
        }

        try (Repository repository = bareGitRepositoryService.getBareRepository(participation.getVcsRepositoryUri(), false)) {
            String setupCommitHash = bareGitRepositoryService.getFirstCommitWithMessage(repository, SET_UP_TEMPLATE_FOR_EXERCISE);
            if (setupCommitHash == null) {
                log.warn("No setup commit found for participation {}, falling back to empty template", participationId);
            }

            Map<String, String> templateFiles = setupCommitHash != null ? readSnapshot(repository, setupCommitHash) : Map.of();
            var templateMarkerLines = new HashMap<String, Set<String>>();

            var sb = new StringBuilder();
            // Byte counts are kept in long: the limits are configurable, and their sums must not overflow.
            long usedBytes = 0;
            long incrementalBudget = (long) maxPayloadBytes - finalStateReservedBytes;
            Map<String, String> previousFiles = templateFiles;
            int emittedSnapshots = 0;
            int omittedSnapshots = 0;
            int unexaminableSubmissions = 0;

            for (ProgrammingSubmission submission : submissions) {
                var snapshot = new SnapshotRef(submission.getId(), snapshotLabel(submission));
                if (submission.getCommitHash() == null || submission.getCommitHash().isBlank()) {
                    // A submission without a commit hash cannot be examined. Record it rather than skipping silently,
                    // so it cannot masquerade as a participation that simply never changed anything.
                    log.warn("Submission {} of participation {} has no commit hash, skipping snapshot", submission.getId(), participationId);
                    unexaminableSubmissions++;
                    evidence.omissions.add(new RecordedOmission(DeimosOmissionReason.MISSING_COMMIT_HASH, null, snapshot));
                    continue;
                }

                Map<String, String> currentFiles = readSnapshot(repository, submission.getCommitHash());
                // Scanned on the full contents before any limit applies, so padding cannot push a forged marker out of
                // sight of this check, even when it pushes it out of the payload.
                scanForImitatedMarkers(participationId, templateFiles, templateMarkerLines, previousFiles, currentFiles, snapshot.label(), sentinel, evidence);

                // The header is known before the diff, so its bytes can be taken out of the budget the diff may spend.
                // Handing buildDiff the whole payload budget instead would let it return a section that no longer fits,
                // which the check below then discards in full rather than keeping a truncated version of it.
                String header = "=== Snapshot %d (%s) ===%n".formatted(emittedSnapshots + 1, snapshot.label());
                long sectionOverheadBytes = utf8Length(header) + (long) SECTION_TRAILER_LINE_SEPARATORS * utf8Length(System.lineSeparator());
                long diffBudget = Math.max(0, incrementalBudget - usedBytes - sectionOverheadBytes);

                var diffResult = buildDiff(previousFiles, currentFiles, diffBudget, snapshot);
                previousFiles = currentFiles;

                if (diffResult.text().isEmpty()) {
                    continue;
                }

                String section = header + diffResult.text() + System.lineSeparator().repeat(SECTION_TRAILER_LINE_SEPARATORS);
                int sectionBytes = utf8Length(section);

                if (usedBytes + sectionBytes > incrementalBudget) {
                    // The section is dropped as a whole, so its file-level omissions do not describe what the model
                    // misses: the entire snapshot is missing.
                    omittedSnapshots++;
                    evidence.omissions.add(new RecordedOmission(DeimosOmissionReason.PAYLOAD_LIMIT, null, snapshot));
                    continue;
                }
                evidence.omissions.addAll(diffResult.omissions());
                sb.append(section);
                usedBytes += sectionBytes;
                emittedSnapshots++;
            }

            if (omittedSnapshots > 0) {
                String omissionNotice = "[... %d snapshot(s) omitted to stay within the size limit ...]%n%n".formatted(omittedSnapshots);
                sb.append(omissionNotice);
                usedBytes += utf8Length(omissionNotice);
            }

            // Reported separately from the size-limit omission above: these snapshots are missing because they could not
            // be examined at all, not because the payload ran out of room. Without the notice the model would read the
            // remaining sequence as the participation's complete history, which is what the system prompt forbids.
            if (unexaminableSubmissions > 0) {
                String unexaminableNotice = "[... %d submission(s) could not be examined and are not represented above ...]%n%n".formatted(unexaminableSubmissions);
                sb.append(unexaminableNotice);
                usedBytes += utf8Length(unexaminableNotice);
            }

            // Recomputed against the template so the model always sees where the participation ended up, even when
            // intermediate snapshots were dropped above. The incremental loop stopped at maxPayloadBytes -
            // finalStateReservedBytes, so what remains is the reservation minus the two notices above and the header
            // below; the minimum byte limit enforced at startup keeps that positive.
            String cumulativeHeader = "=== Final state vs. exercise template ===" + System.lineSeparator();
            long cumulativeBudget = Math.max(0, maxPayloadBytes - usedBytes - utf8Length(cumulativeHeader));
            var cumulativeResult = buildDiff(templateFiles, previousFiles, cumulativeBudget, null);
            boolean cumulativeIsRedundant = emittedSnapshots == 1 && omittedSnapshots == 0 && !evidence.hasOmissions() && cumulativeResult.omissions().isEmpty();
            if (!cumulativeResult.text().isEmpty() && !cumulativeIsRedundant) {
                evidence.omissions.addAll(cumulativeResult.omissions());
                sb.append(cumulativeHeader);
                sb.append(cumulativeResult.text());
            }

            return new SnapshotHistory(sb.toString().stripTrailing(), emittedSnapshots);
        }
        catch (DeimosSnapshotHistoryException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new DeimosSnapshotHistoryException("Could not build snapshot history for participation " + participationId, ex);
        }
    }

    /**
     * Records every imitated fence marker a snapshot introduces.
     * <p>
     * Only files whose content changed against the previous snapshot are scanned, so each content is scanned once. A
     * marker line that already occurs in the exercise template's version of the same file is the instructor's, for
     * example in an exercise about prompt injection, and is not attributed to the student. File paths are scanned as
     * well, because they reach the prompt through the diff headers.
     *
     * @param participationId     the participation, for the log
     * @param templateFiles       the exercise template
     * @param templateMarkerLines cache of the marker lines per template file
     * @param previousFiles       the previous snapshot
     * @param currentFiles        the snapshot to scan
     * @param snapshot            the label of the snapshot, for the report
     * @param sentinel            the prompt sentinel, which must never occur in a submission
     * @param evidence            collects the findings
     */
    private static void scanForImitatedMarkers(long participationId, Map<String, String> templateFiles, Map<String, Set<String>> templateMarkerLines,
            Map<String, String> previousFiles, Map<String, String> currentFiles, String snapshot, String sentinel, ParticipationEvidence evidence) {
        for (Map.Entry<String, String> file : new TreeMap<>(currentFiles).entrySet()) {
            String path = file.getKey();
            String content = file.getValue();
            if (content == null || content.equals(previousFiles.get(path))) {
                continue;
            }

            if (!templateFiles.containsKey(path) && containsFenceMarker(path)) {
                evidence.addFinding("path\0" + path, new DeimosManipulationFinding(displayPath(path), null, snapshot));
            }

            Set<String> markerLinesInTemplate = templateMarkerLines.computeIfAbsent(path, templatePath -> {
                String templateContent = templateFiles.get(templatePath);
                Set<String> lines = new HashSet<>();
                if (templateContent != null) {
                    forEachFenceMarkerLine(templateContent, (lineNumber, line) -> lines.add(line));
                }
                return lines;
            });
            forEachFenceMarkerLine(content, (lineNumber, line) -> {
                if (!markerLinesInTemplate.contains(line)) {
                    evidence.addFinding("line\0" + path + "\0" + line, new DeimosManipulationFinding(displayPath(path), lineNumber, snapshot));
                }
            });

            // The sentinel is random and generated for this participation alone, so finding it in a submission means
            // it leaked from Deimos itself. buildPrompt would still regenerate it, but the cause needs looking into.
            if (!evidence.sentinelSeen && content.contains(sentinel)) {
                evidence.sentinelSeen = true;
                log.warn("The prompt sentinel of participation {} occurs in its submitted content, which indicates a defect in Deimos", participationId);
            }
        }
    }

    /**
     * Checks a single line, or a file path, for an imitated fence marker.
     *
     * @param text the text to check, without line breaks
     * @return whether it contains an imitated fence marker
     */
    static boolean containsFenceMarker(CharSequence text) {
        return FENCE_MARKER_PATTERN.matcher(text).find();
    }

    @FunctionalInterface
    private interface MarkerLineConsumer {

        void accept(int lineNumber, String line);
    }

    /**
     * Calls {@code consumer} for every line of {@code content} that contains an imitated fence marker.
     * <p>
     * One matcher is reused with its region set to each line in turn, so the whole content is scanned in a single linear
     * pass and a line is only copied when it matches.
     *
     * @param content  the text to scan
     * @param consumer receives the 1-based line number and the text of each matching line
     */
    private static void forEachFenceMarkerLine(String content, MarkerLineConsumer consumer) {
        Matcher matcher = FENCE_MARKER_PATTERN.matcher(content);
        int lineStart = 0;
        int lineNumber = 1;
        while (lineStart <= content.length()) {
            int lineEnd = content.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = content.length();
            }
            if (matcher.region(lineStart, lineEnd).find()) {
                consumer.accept(lineNumber, content.substring(lineStart, lineEnd));
            }
            lineStart = lineEnd + 1;
            lineNumber++;
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
     * @param text      the rendered unified diff, empty when nothing changed
     * @param omissions the files skipped or truncated, each with the limit that actually caused it
     */
    private record DiffResult(String text, List<RecordedOmission> omissions) {
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
     * @param snapshot      the target snapshot, or {@code null} for the final state, for the omissions
     * @return the rendered diff and what had to be omitted
     */
    private DiffResult buildDiff(Map<String, String> baseFiles, Map<String, String> targetFiles, long budgetInBytes, @Nullable SnapshotRef snapshot) {
        var allPaths = new TreeSet<String>();
        allPaths.addAll(baseFiles.keySet());
        allPaths.addAll(targetFiles.keySet());

        var sb = new StringBuilder();
        List<RecordedOmission> omissions = new ArrayList<>();
        long usedBytes = 0;
        int omittedFiles = 0;
        // Held back so the trailing notice, whose text is only known once the loop has finished, still fits.
        long sectionBudget = Math.max(0, budgetInBytes - DIFF_OMISSION_NOTICE_RESERVED_BYTES);

        for (String path : allPaths) {
            String baseContent = baseFiles.get(path);
            String targetContent = targetFiles.get(path);
            if (baseContent == null && targetContent == null) {
                continue;
            }
            if (baseContent != null && baseContent.equals(targetContent)) {
                continue;
            }

            // A per-file cap alone does not bound the payload: many individually capped diffs still add up. Stop once
            // the budget for this section is spent, and say how many files were dropped. Placed after the identity
            // checks above so files unchanged between the two snapshots are never counted as omitted.
            if (usedBytes >= sectionBudget) {
                omittedFiles++;
                omissions.add(new RecordedOmission(DeimosOmissionReason.PAYLOAD_LIMIT, path, snapshot));
                continue;
            }

            String safePath = escapeMetadata(path);
            String oldHeader = baseContent == null ? "--- /dev/null" : "--- a/" + safePath;
            String newHeader = targetContent == null ? "+++ /dev/null" : "+++ b/" + safePath;

            if (utf8Length(baseContent != null ? baseContent : "") > maxFileInputBytes || utf8Length(targetContent != null ? targetContent : "") > maxFileInputBytes) {
                String notice = oldHeader + System.lineSeparator() + newHeader + System.lineSeparator() + "[file omitted: exceeds the " + maxFileInputBytes
                        + " byte analysis size limit]" + System.lineSeparator() + System.lineSeparator();
                // Its content would never have been diffed, so the input limit is what the model misses it to, whether or
                // not the notice still fits.
                omissions.add(new RecordedOmission(DeimosOmissionReason.FILE_INPUT_LIMIT, path, snapshot));
                if (usedBytes + utf8Length(notice) > sectionBudget) {
                    omittedFiles++;
                    continue;
                }
                sb.append(notice);
                usedBytes += utf8Length(notice);
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

            // Bounded by whichever is smaller: the per-file cap, or what is left of this section's overall budget. The
            // file headers and the trailing separator are appended alongside the diff, so they are subtracted here
            // rather than added on top afterwards, which would let the rendered section outgrow the caller's budget.
            String headers = oldHeader + System.lineSeparator() + newHeader + System.lineSeparator();
            int fixedSectionBytes = utf8Length(headers) + utf8Length(System.lineSeparator());
            long remainingDiffBytes = Math.max(0, sectionBudget - usedBytes - fixedSectionBytes);
            int allowedDiffBytes = (int) Math.min(maxFileDiffBytes, remainingDiffBytes);
            RecordedOmission truncation = null;
            if (utf8Length(unifiedDiff) > allowedDiffBytes) {
                String truncationNotice = "[... diff truncated at the " + allowedDiffBytes + " byte limit ...]" + System.lineSeparator();
                unifiedDiff = truncateAtLineBoundary(unifiedDiff, Math.max(0, allowedDiffBytes - utf8Length(truncationNotice))) + truncationNotice;
                // Attributed to the limit that actually bound: the per-file cap only when it was the smaller of the two.
                DeimosOmissionReason reason = maxFileDiffBytes <= remainingDiffBytes ? DeimosOmissionReason.FILE_DIFF_LIMIT : DeimosOmissionReason.PAYLOAD_LIMIT;
                truncation = new RecordedOmission(reason, path, snapshot);
            }

            String section = headers + unifiedDiff + System.lineSeparator();
            // Even a fully truncated diff still carries the headers, and a long student-controlled path can make those
            // headers alone exceed the remaining budget. Omit the file rather than append an over-budget section, so the
            // payload stays bounded regardless of path length.
            if (usedBytes + utf8Length(section) > sectionBudget) {
                omittedFiles++;
                omissions.add(new RecordedOmission(DeimosOmissionReason.PAYLOAD_LIMIT, path, snapshot));
                continue;
            }
            if (truncation != null) {
                omissions.add(truncation);
            }
            sb.append(section);
            usedBytes += utf8Length(section);
        }

        if (omittedFiles > 0) {
            sb.append("[... %d further changed file(s) omitted to stay within the size limit ...]%n".formatted(omittedFiles));
        }

        return new DiffResult(sb.toString().stripTrailing(), omissions);
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

    /**
     * Escapes a student-controlled file path and shortens it for the completion email.
     *
     * @param path the raw path
     * @return the escaped path, at most {@link #MAX_REPORTED_PATH_LENGTH} characters long
     */
    static String displayPath(String path) {
        String escaped = escapeMetadata(path);
        if (escaped.length() <= MAX_REPORTED_PATH_LENGTH) {
            return escaped;
        }
        int end = MAX_REPORTED_PATH_LENGTH - TRUNCATION_SUFFIX.length();
        // Never cut a surrogate pair in half.
        if (Character.isHighSurrogate(escaped.charAt(end - 1))) {
            end--;
        }
        return escaped.substring(0, end) + TRUNCATION_SUFFIX;
    }

    /**
     * Describes a submission for snapshot headers and the completion email.
     *
     * @param submission the submission
     * @return the abbreviated commit hash and the submission date, escaped
     */
    private static String snapshotLabel(ProgrammingSubmission submission) {
        String commitHash = submission.getCommitHash();
        String commit = commitHash == null || commitHash.isBlank() ? "no commit hash" : shortHash(commitHash);
        String submissionDate = submission.getSubmissionDate() != null ? submission.getSubmissionDate().toString() : "unknown";
        return escapeMetadata(commit) + ", " + escapeMetadata(submissionDate);
    }

    private static String shortHash(String commitHash) {
        return commitHash.substring(0, Math.min(SHORT_COMMIT_HASH_LENGTH, commitHash.length()));
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
