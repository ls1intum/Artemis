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
import static de.tum.cit.aet.artemis.deimos.service.DeimosDiffBuilder.escapeMetadata;
import static de.tum.cit.aet.artemis.deimos.service.DeimosDiffBuilder.utf8Length;

import java.io.IOException;
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

import org.eclipse.jgit.lib.ObjectId;
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
import de.tum.cit.aet.artemis.deimos.service.DeimosDiffBuilder.DiffOmission;
import de.tum.cit.aet.artemis.deimos.service.DeimosSnapshotReader.TextSnapshot;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.localvc.service.BareGitRepositoryService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;

@Conditional(DeimosEnabled.class)
@Lazy
@Service
public class DeimosAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(DeimosAnalysisService.class);

    private static final String SYSTEM_PROMPT_PATH = "prompts/deimos/analyze_submission_system.st";

    private static final String USER_PROMPT_PATH = "prompts/deimos/analyze_submission_user.st";

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

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final DeimosLlmClient deimosLlmClient;

    private final DeimosPromptTemplateService deimosPromptTemplateService;

    private final DeimosSnapshotReader deimosSnapshotReader;

    private final BareGitRepositoryService bareGitRepositoryService;

    private final DeimosDiffBuilder deimosDiffBuilder;

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
            DeimosLlmClient deimosLlmClient, DeimosPromptTemplateService deimosPromptTemplateService, DeimosSnapshotReader deimosSnapshotReader,
            BareGitRepositoryService bareGitRepositoryService, @Value("${" + MAX_FILE_INPUT_BYTES_PROPERTY + ":" + DEFAULT_MAX_FILE_INPUT_BYTES + "}") int maxFileInputBytes,
            @Value("${" + MAX_FILE_DIFF_BYTES_PROPERTY + ":" + DEFAULT_MAX_FILE_DIFF_BYTES + "}") int maxFileDiffBytes,
            @Value("${" + MAX_PAYLOAD_BYTES_PROPERTY + ":" + DEFAULT_MAX_PAYLOAD_BYTES + "}") int maxPayloadBytes,
            @Value("${" + FINAL_STATE_RESERVED_BYTES_PROPERTY + ":" + DEFAULT_FINAL_STATE_RESERVED_BYTES + "}") int finalStateReservedBytes) {
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.deimosLlmClient = deimosLlmClient;
        this.deimosPromptTemplateService = deimosPromptTemplateService;
        this.deimosSnapshotReader = deimosSnapshotReader;
        this.bareGitRepositoryService = bareGitRepositoryService;
        this.deimosDiffBuilder = new DeimosDiffBuilder(maxFileInputBytes, maxFileDiffBytes);
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
                log.info("Skipping Deimos analysis for participation {}: no observed change to text files", participationId);
                // An empty file whose name imitates a marker produces no diff, yet it is still a finding.
                if (evidence.hasFindings()) {
                    return new ParticipationOutcome(
                            toAnalysis(participationId, exerciseId, DeimosVerdict.MANIPULATION_ATTEMPT, null, DeimosFailureType.NO_SNAPSHOT_HISTORY, evidence), null, false);
                }
                // Only binary files or links changed: there is nothing the model could read, but the student did submit
                // content that nobody examined, so the participation must not pass as having nothing to analyse.
                if (evidence.hasOmissions()) {
                    return new ParticipationOutcome(toAnalysis(participationId, exerciseId, DeimosVerdict.INCOMPLETELY_CHECKED, null, null, evidence), null, false);
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
    String generateSentinel() {
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

        /**
         * Whether part of the text evidence was left out, as opposed to the content of binary files and links, which never
         * reaches the model.
         */
        boolean hasTextOmissions() {
            return omissions.stream().anyMatch(omission -> omission.reason() != DeimosOmissionReason.NON_TEXT_FILE);
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
     * @param reason   why the evidence was left out
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
     * @param payload          the rendered history, empty when there is nothing the model could analyse
     * @param emittedSnapshots how many snapshots contributed a diff
     */
    private record SnapshotHistory(String payload, int emittedSnapshots) {
    }

    /**
     * Reconstructs the observed submission snapshot history of a participation as unified diffs.
     * <p>
     * An empty payload means there is nothing the model could analyse: no snapshots, no observed change, or changes to
     * binary files and links alone, which are recorded as omissions. Anything that went wrong throws {@link DeimosSnapshotHistoryException} instead, so a repository failure is
     * never reported as a
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

            TextSnapshot template = setupCommitHash != null ? readSnapshot(repository, setupCommitHash) : new TextSnapshot(Map.of(), Map.of());
            var history = new HistoryAccumulator(participationId, repository, template, new MarkerScan(participationId, template, sentinel, evidence), evidence);
            for (ProgrammingSubmission submission : submissions) {
                history.add(submission);
            }
            return history.finish();
        }
        catch (DeimosSnapshotHistoryException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new DeimosSnapshotHistoryException("Could not build snapshot history for participation " + participationId, ex);
        }
    }

    /**
     * Builds the payload of one participation snapshot by snapshot. Created per participation, since the service is shared.
     */
    private final class HistoryAccumulator {

        private final long participationId;

        private final Repository repository;

        private final TextSnapshot template;

        private final MarkerScan markerScan;

        private final ParticipationEvidence evidence;

        private final StringBuilder sb = new StringBuilder();

        // Byte counts are kept in long: the limits are configurable, and their sums must not overflow.
        private final long incrementalBudget = (long) maxPayloadBytes - finalStateReservedBytes;

        private long usedBytes;

        private Map<String, String> previousFiles;

        private Map<String, ObjectId> previousOtherFiles;

        private int emittedSnapshots;

        private int omittedSnapshots;

        private int unexaminableSubmissions;

        private int nonTextChanges;

        private HistoryAccumulator(long participationId, Repository repository, TextSnapshot template, MarkerScan markerScan, ParticipationEvidence evidence) {
            this.participationId = participationId;
            this.repository = repository;
            this.template = template;
            this.markerScan = markerScan;
            this.evidence = evidence;
            this.previousFiles = template.textFiles();
            this.previousOtherFiles = template.otherFiles();
        }

        private void add(ProgrammingSubmission submission) throws IOException {
            var snapshot = new SnapshotRef(submission.getId(), snapshotLabel(submission));
            if (submission.getCommitHash() == null || submission.getCommitHash().isBlank()) {
                // A submission without a commit hash cannot be examined. Record it rather than skipping silently,
                // so it cannot masquerade as a participation that simply never changed anything.
                log.warn("Submission {} of participation {} has no commit hash, skipping snapshot", submission.getId(), participationId);
                unexaminableSubmissions++;
                evidence.omissions.add(new RecordedOmission(DeimosOmissionReason.MISSING_COMMIT_HASH, null, snapshot));
                return;
            }

            TextSnapshot current = readSnapshot(repository, submission.getCommitHash());
            // Scanned on the full contents before any limit applies, so padding cannot push a forged marker out of
            // sight of this check, even when it pushes it out of the payload.
            markerScan.scan(previousFiles, previousOtherFiles.keySet(), current, snapshot.label());
            // Recorded before any budget decision, so dropping this snapshot's text section cannot drop them as well.
            nonTextChanges += recordNonTextChanges(template, previousOtherFiles, current, snapshot, evidence);

            // The header is known before the diff, so its bytes can be taken out of the budget the diff may spend.
            // Handing buildDiff the whole payload budget instead would let it return a section that no longer fits,
            // which the check below then discards in full rather than keeping a truncated version of it.
            String header = "=== Snapshot %d (%s) ===%n".formatted(emittedSnapshots + 1, snapshot.label());
            long sectionOverheadBytes = utf8Length(header) + (long) SECTION_TRAILER_LINE_SEPARATORS * utf8Length(System.lineSeparator());
            long diffBudget = Math.max(0, incrementalBudget - usedBytes - sectionOverheadBytes);

            var diffResult = deimosDiffBuilder.buildDiff(previousFiles, current.textFiles(), diffBudget);
            previousFiles = current.textFiles();
            previousOtherFiles = current.otherFiles();
            if (!diffResult.text().isEmpty()) {
                appendSection(header + diffResult.text() + System.lineSeparator().repeat(SECTION_TRAILER_LINE_SEPARATORS), recorded(diffResult.omissions(), snapshot), snapshot);
            }
        }

        private void appendSection(String section, List<RecordedOmission> sectionOmissions, SnapshotRef snapshot) {
            int sectionBytes = utf8Length(section);
            if (usedBytes + sectionBytes > incrementalBudget) {
                // The section is dropped as a whole, so its file-level omissions do not describe what the model
                // misses: the entire snapshot is missing.
                omittedSnapshots++;
                evidence.omissions.add(new RecordedOmission(DeimosOmissionReason.PAYLOAD_LIMIT, null, snapshot));
                return;
            }
            evidence.omissions.addAll(sectionOmissions);
            sb.append(section);
            usedBytes += sectionBytes;
            emittedSnapshots++;
        }

        private SnapshotHistory finish() throws IOException {
            appendNotice(omittedSnapshots, "[... %d snapshot(s) omitted to stay within the size limit ...]%n%n");
            // Reported separately from the size-limit omission above: these snapshots are missing because they could not
            // be examined at all, not because the payload ran out of room. Without the notice the model would read the
            // remaining sequence as the participation's complete history, which is what the system prompt forbids.
            appendNotice(unexaminableSubmissions, "[... %d submission(s) could not be examined and are not represented above ...]%n%n");

            // Recomputed against the template so the model always sees where the participation ended up, even when
            // intermediate snapshots were dropped above. The incremental loop stopped at maxPayloadBytes -
            // finalStateReservedBytes, so what remains is the reservation minus the notices and the header; the minimum
            // byte limit enforced at startup keeps that positive.
            String cumulativeHeader = "=== Final state vs. exercise template ===" + System.lineSeparator();
            // Without paths: the names of binary files and links are the student's, and this notice must not carry them into
            // the prompt. It is charged before the final state is built, so the final state cannot exceed the payload bound.
            String nonTextNotice = nonTextChanges > 0
                    ? "[... %d change(s) to binary files or links were not examined; their content is not included ...]%n%n".formatted(nonTextChanges)
                    : "";
            long cumulativeBudget = Math.max(0, maxPayloadBytes - usedBytes - utf8Length(nonTextNotice) - utf8Length(cumulativeHeader));
            // A single complete snapshot already is the change against the template. Building its copy first would let the
            // smaller remaining budget truncate it, and the omissions of a section that is never sent would wrongly mark
            // a fully examined participation as incompletely checked. Changes to binary files and links say nothing about
            // whether that text section is complete.
            boolean cumulativeIsRedundant = emittedSnapshots == 1 && omittedSnapshots == 0 && !evidence.hasTextOmissions();
            String cumulativeSection = cumulativeIsRedundant ? "" : cumulativeSection(cumulativeHeader, cumulativeBudget);
            // Only alongside other content: a history that changed nothing but binary files and links, without any other
            // notice, is reported without asking the model, so this notice alone must not turn it into a payload.
            if (!nonTextNotice.isEmpty() && (!sb.isEmpty() || !cumulativeSection.isEmpty())) {
                sb.append(nonTextNotice);
            }
            sb.append(cumulativeSection);

            return new SnapshotHistory(sb.toString().stripTrailing(), emittedSnapshots);
        }

        private void appendNotice(int count, String format) {
            if (count > 0) {
                String notice = format.formatted(count);
                sb.append(notice);
                usedBytes += utf8Length(notice);
            }
        }

        private String cumulativeSection(String header, long budget) throws IOException {
            var cumulativeResult = deimosDiffBuilder.buildDiff(template.textFiles(), previousFiles, budget);
            if (cumulativeResult.text().isEmpty()) {
                return "";
            }
            evidence.omissions.addAll(recorded(cumulativeResult.omissions(), null));
            return header + cumulativeResult.text();
        }
    }

    /**
     * Records every imitated fence marker a participation's snapshots introduce. Created per participation.
     * <p>
     * Only files whose content changed against the previous snapshot are scanned, so each content is scanned once. A
     * marker line that already occurs in the exercise template's version of the same file is the instructor's, for
     * example in an exercise about prompt injection, and is not attributed to the student. File paths are scanned as
     * well, because they reach the prompt through the diff headers.
     */
    private static final class MarkerScan {

        private final long participationId;

        private final TextSnapshot template;

        /**
         * The prompt sentinel, which must never occur in a submission.
         */
        private final String sentinel;

        private final ParticipationEvidence evidence;

        /**
         * The marker lines per template file, computed once per file.
         */
        private final Map<String, Set<String>> templateMarkerLines = new HashMap<>();

        private MarkerScan(long participationId, TextSnapshot template, String sentinel, ParticipationEvidence evidence) {
            this.participationId = participationId;
            this.template = template;
            this.sentinel = sentinel;
            this.evidence = evidence;
        }

        /**
         * Records the imitated fence markers one snapshot introduces.
         *
         * @param previousFiles      the text files of the previous snapshot
         * @param previousOtherPaths the paths of the previous snapshot that are not text files
         * @param current            the snapshot to scan
         * @param snapshotLabel      the label of the snapshot, for the report
         */
        private void scan(Map<String, String> previousFiles, Set<String> previousOtherPaths, TextSnapshot current, String snapshotLabel) {
            // Binary files and symbolic links never reach the payload, but their names are the student's as well.
            for (String path : new TreeSet<>(current.otherFiles().keySet())) {
                if (!previousOtherPaths.contains(path)) {
                    scanPath(path, snapshotLabel);
                }
            }
            for (Map.Entry<String, String> file : new TreeMap<>(current.textFiles()).entrySet()) {
                String content = file.getValue();
                if (content != null && !content.equals(previousFiles.get(file.getKey()))) {
                    scanTextFile(file.getKey(), content, snapshotLabel);
                }
            }
        }

        private void scanPath(String path, String snapshotLabel) {
            boolean inTemplate = template.textFiles().containsKey(path) || template.otherFiles().containsKey(path);
            if (!inTemplate && DeimosFenceMarkers.containsFenceMarker(path)) {
                evidence.addFinding("path\0" + path, new DeimosManipulationFinding(displayPath(path), null, snapshotLabel));
            }
        }

        private void scanTextFile(String path, String content, String snapshotLabel) {
            scanPath(path, snapshotLabel);
            Set<String> markerLinesInTemplate = templateMarkerLines.computeIfAbsent(path, this::markerLinesOfTemplateFile);
            DeimosFenceMarkers.forEachMarkerLine(content, (lineNumber, line) -> {
                if (!markerLinesInTemplate.contains(line)) {
                    evidence.addFinding("line\0" + path + "\0" + line, new DeimosManipulationFinding(displayPath(path), lineNumber, snapshotLabel));
                }
            });

            // The sentinel is random and generated for this participation alone, so finding it in a submission means
            // it leaked from Deimos itself. buildPrompt would still regenerate it, but the cause needs looking into.
            if (!evidence.sentinelSeen && content.contains(sentinel)) {
                evidence.sentinelSeen = true;
                log.warn("The prompt sentinel of participation {} occurs in its submitted content, which indicates a defect in Deimos", participationId);
            }
        }

        private Set<String> markerLinesOfTemplateFile(String path) {
            String templateContent = template.textFiles().get(path);
            Set<String> lines = new HashSet<>();
            if (templateContent != null) {
                DeimosFenceMarkers.forEachMarkerLine(templateContent, (lineNumber, line) -> lines.add(line));
            }
            return lines;
        }
    }

    /**
     * Records every binary file, symbolic link or submodule whose content a snapshot adds or changes, as recognised by its
     * object id. Only text reaches the model, so that content is never examined. An entry that matches the exercise
     * template is the instructor's, also when a student restores it, and a deleted entry leaves nothing to examine.
     *
     * @param template           the exercise template
     * @param previousOtherFiles the non-text entries of the previous snapshot
     * @param current            the snapshot to check
     * @param snapshot           the snapshot, for the report
     * @param evidence           collects the omissions
     * @return how many entries were recorded
     */
    private static int recordNonTextChanges(TextSnapshot template, Map<String, ObjectId> previousOtherFiles, TextSnapshot current, SnapshotRef snapshot,
            ParticipationEvidence evidence) {
        int recorded = 0;
        // Sorted, so the entries shown in the capped report do not depend on hash order.
        for (Map.Entry<String, ObjectId> entry : new TreeMap<>(current.otherFiles()).entrySet()) {
            ObjectId objectId = entry.getValue();
            boolean changed = !objectId.equals(previousOtherFiles.get(entry.getKey())) && !objectId.equals(template.otherFiles().get(entry.getKey()));
            if (changed && evidence.omissions.add(new RecordedOmission(DeimosOmissionReason.NON_TEXT_FILE, entry.getKey(), snapshot))) {
                recorded++;
            }
        }
        return recorded;
    }

    /**
     * Reads one snapshot, failing loudly when the commit cannot be resolved.
     * <p>
     * Which files count as text, scripts always among them, is decided by {@link DeimosSnapshotReader}.
     * <p>
     * {@link DeimosSnapshotReader#read} returns an empty snapshot for an unresolvable hash, which is indistinguishable
     * from an empty repository. Left unchecked, a garbage-collected or rewritten commit would
     * be rendered as "the student deleted every file", or as "nothing to analyse", instead of being reported as an error.
     *
     * @param repository the bare repository
     * @param commitHash the commit to read
     * @return the text files of that commit and all its other entries
     * @throws DeimosSnapshotHistoryException if the commit cannot be resolved or read
     */
    private TextSnapshot readSnapshot(Repository repository, String commitHash) {
        try {
            if (repository.resolve(commitHash) == null) {
                throw new DeimosSnapshotHistoryException("Could not resolve commit " + commitHash + " in the participation repository");
            }
            return deimosSnapshotReader.read(repository, commitHash);
        }
        catch (DeimosSnapshotHistoryException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new DeimosSnapshotHistoryException("Could not read commit " + commitHash + " from the participation repository", ex);
        }
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

    private static List<RecordedOmission> recorded(List<DiffOmission> omissions, @Nullable SnapshotRef snapshot) {
        return omissions.stream().map(omission -> new RecordedOmission(omission.reason(), omission.path(), snapshot)).toList();
    }

    private static String shortHash(String commitHash) {
        return commitHash.substring(0, Math.min(SHORT_COMMIT_HASH_LENGTH, commitHash.length()));
    }

}
