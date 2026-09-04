package de.tum.cit.aet.artemis.assessment.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentUploadErrorType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.AssessmentUploadErrorDTO;
import de.tum.cit.aet.artemis.assessment.dto.AssessmentUploadParticipationDTO;
import de.tum.cit.aet.artemis.assessment.dto.AssessmentUploadResultDTO;
import de.tum.cit.aet.artemis.assessment.repository.AssessmentUploadParticipationRepository;
import de.tum.cit.aet.artemis.assessment.service.AssessmentUploadArchiveParsingService.CsvParseError;
import de.tum.cit.aet.artemis.assessment.service.AssessmentUploadArchiveParsingService.CsvParseResult;
import de.tum.cit.aet.artemis.assessment.service.AssessmentUploadArchiveParsingService.ParsedCsv;
import de.tum.cit.aet.artemis.assessment.service.AssessmentUploadArchiveParsingService.ZipContents;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.SubmissionService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Service that parses a zip file uploaded by an instructor and stores manual assessments for the participants of a programming exercise at once.
 * <p>
 * The zip file has to contain exactly one {@code assessment-scores.csv} file and one {@code .txt} file per graded participant. The CSV file identifies each participant by the
 * repository-export identifier {@code <participationId>-<login>} in its first column and provides the achieved points in an {@code Overall points} column. Each {@code .txt} file
 * is
 * named after the exported repository folder of the participant (e.g. {@code Course-Exercise-<participationId>-<login>.txt}) and its content becomes the manual feedback.
 * <p>
 * The upload is processed all-or-nothing: the whole zip is validated first and, only if no {@link AssessmentUploadErrorType error} is found, the assessments are
 * created. An existing manual assessment of a participant is overwritten in place — its score and feedback are replaced while the assessment itself is kept, so ratings,
 * participant scores and complaints that reference it stay valid. A participant whose current manual assessment has an open complaint is rejected instead of overwritten.
 *
 * @see AssessmentUploadResultDTO
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class AssessmentUploadService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentUploadService.class);

    private static final String ENTITY_NAME = "assessmentUpload";

    /** The extension (including the leading dot) of the per-participant feedback files inside the zip, used when reporting text files in errors. */
    private static final String TEXT_FILE_EXTENSION = ".txt";

    private final AssessmentUploadArchiveParsingService archiveParser;

    private final AssessmentUploadParticipationRepository assessmentUploadParticipationRepository;

    private final SubmissionRepository submissionRepository;

    private final AssessmentUploadResultService assessmentUploadResultService;

    private final SubmissionService submissionService;

    private final TransactionTemplate transactionTemplate;

    /**
     * Creates a service for validating and storing uploaded manual assessments.
     * <p>
     * <b>Preconditions:</b> all parameters are non-{@code null}.
     *
     * @param archiveParser                           the parser turning the uploaded zip into structured CSV and text-file contents
     * @param assessmentUploadParticipationRepository the repository used to resolve participants
     * @param submissionRepository                    the repository used to create missing submissions and to persist the ordered results collection
     * @param assessmentUploadResultService           the service used to replace manual assessment results
     * @param submissionService                       the service enforcing the shared assessment-availability gate
     * @param transactionManager                      the transaction manager used to store the complete upload atomically
     * @throws IllegalArgumentException if any parameter is {@code null}
     */
    public AssessmentUploadService(final AssessmentUploadArchiveParsingService archiveParser, final AssessmentUploadParticipationRepository assessmentUploadParticipationRepository,
            final SubmissionRepository submissionRepository, final AssessmentUploadResultService assessmentUploadResultService, final SubmissionService submissionService,
            final PlatformTransactionManager transactionManager) {
        if (Stream.of(archiveParser, assessmentUploadParticipationRepository, submissionRepository, assessmentUploadResultService, submissionService, transactionManager)
                .anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("The assessment upload service dependencies must not be null");
        }
        this.archiveParser = archiveParser;
        this.assessmentUploadParticipationRepository = assessmentUploadParticipationRepository;
        this.submissionRepository = submissionRepository;
        this.assessmentUploadResultService = assessmentUploadResultService;
        this.submissionService = submissionService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Builds a template zip an instructor can fill in and re-upload: an {@code assessment-scores.csv} with a row (participant identifier, login, empty points) for every
     * participation of the exercise, plus one empty {@code <participationId>.txt} feedback file per participation. The feedback files are named after the path-safe participation
     * id
     * (not the identifier, whose login/team-short-name part may contain a {@code /}) so the archive round-trips through {@link #importAssessments} once the points and feedback
     * have
     * been filled in.
     * <p>
     * <b>Precondition:</b> {@code exercise} is a persisted programming exercise.
     * <p>
     * <b>Postcondition:</b> read-only; returns the bytes of a zip containing one CSV file and one empty text file per participation of the exercise.
     *
     * @param exercise the programming exercise whose participants are exported into the template
     * @return the bytes of the generated template zip
     * @throws IllegalArgumentException if {@code exercise} is not persisted
     */
    public byte[] generateTemplateArchive(final ProgrammingExercise exercise) {
        if (exercise == null || exercise.getId() == null) {
            throw new IllegalArgumentException("The exercise must be a persisted programming exercise");
        }
        final List<AssessmentUploadParticipationDTO> participations = assessmentUploadParticipationRepository.findAllForAssessmentUploadTemplate(exercise.getId());
        // A generated template carries one CSV row plus one text entry per participant, so it must respect the same participant budget the importer enforces
        // (AssessmentUploadArchiveParsingService.MAX_PARTICIPANT_COUNT reserves one archive entry for the CSV). Rejecting up front avoids emitting a template that could never be
        // re-uploaded.
        if (participations.size() > AssessmentUploadArchiveParsingService.MAX_PARTICIPANT_COUNT) {
            throw new BadRequestAlertException("The exercise has more participants (" + participations.size() + ") than a single manual-assessment upload supports ("
                    + AssessmentUploadArchiveParsingService.MAX_PARTICIPANT_COUNT + ")", ENTITY_NAME, "assessmentUpload.tooManyParticipantsForTemplate");
        }

        final byte[] csvBytes = buildTemplateCsv(participations);
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream(); ZipOutputStream zipStream = new ZipOutputStream(byteStream, StandardCharsets.UTF_8)) {
            zipStream.putNextEntry(new ZipEntry("assessment-scores.csv"));
            zipStream.write(csvBytes);
            zipStream.closeEntry();
            for (final AssessmentUploadParticipationDTO participation : participations) {
                // One empty feedback file per participant, named after the numeric — and therefore path-safe — participation id rather than the identifier: the identifier's
                // login/team-short-name part may contain a '/' (TeamResource persists raw short names), which a zip reader would treat as a directory prefix and strip, leaving a
                // wrong base name. importAssessments matches this flat name back to the row by participation id.
                zipStream.putNextEntry(new ZipEntry(templateFeedbackFileName(participation)));
                zipStream.closeEntry();
            }
            zipStream.finish();
            log.debug("Generated an assessment-upload template with {} participation(s) for programming exercise {}", participations.size(), exercise.getId());
            return byteStream.toByteArray();
        }
        catch (final IOException exception) {
            throw new UncheckedIOException("Failed to generate the assessment-upload template for exercise " + exercise.getId(), exception);
        }
    }

    private static String templateIdentifier(final AssessmentUploadParticipationDTO participation) {
        return participation.participationId() + "-" + participation.participantIdentifier();
    }

    /**
     * The name of the generated feedback file for one participation: the numeric participation id plus the {@code .txt} extension. The participation id is always path-safe, so the
     * entry never becomes a nested zip path a reader would reduce to a wrong base name; {@link #findMatchingTextKeys} resolves this flat name back to its CSV row by participation
     * id.
     *
     * @param participation the participation the feedback file belongs to
     * @return the flat, path-safe feedback file name
     */
    private static String templateFeedbackFileName(final AssessmentUploadParticipationDTO participation) {
        return participation.participationId() + TEXT_FILE_EXTENSION;
    }

    /**
     * Serializes the template's {@code assessment-scores.csv} with Apache Commons CSV so that an identifier or login containing the CSV delimiter — most notably a team short name
     * with a comma, which {@code TeamResource} persists without escaping — is quoted and therefore round-trips through {@link #importAssessments} instead of shifting into extra
     * columns and breaking the generated template's own re-upload.
     * <p>
     * <b>Precondition:</b> {@code participations} is non-{@code null}.
     * <p>
     * <b>Postcondition:</b> pure function; returns the UTF-8 bytes of a CSV with the {@code Identifier,Login,Overall points} header and one row per participation, the points cell
     * left empty for the instructor to fill in.
     *
     * @param participations the participations exported into the template
     * @return the UTF-8 encoded CSV content
     */
    private static byte[] buildTemplateCsv(final List<AssessmentUploadParticipationDTO> participations) {
        assert participations != null : "participations must not be null";
        final StringBuilder csv = new StringBuilder();
        // The parser reads the CSV back with CSVFormat.DEFAULT; a plain '\n' record separator keeps the generated template the Unix-newline CSV it has always been.
        final CSVFormat format = CSVFormat.DEFAULT.builder().setRecordSeparator('\n').get();
        try (CSVPrinter printer = new CSVPrinter(csv, format)) {
            printer.printRecord("Identifier", "Login", "Overall points");
            for (final AssessmentUploadParticipationDTO participation : participations) {
                // The first column is the repository-export identifier <participationId>-<login> that importAssessments resolves; the login column is informational and ignored on
                // upload; the empty third column is the "Overall points" the instructor fills in.
                printer.printRecord(templateIdentifier(participation), participation.participantIdentifier(), "");
            }
        }
        catch (final IOException exception) {
            throw new UncheckedIOException("Failed to build the assessment-upload template CSV", exception);
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Parses the uploaded zip file and, if it is valid, stores a manual assessment (score + feedback) for every participant referenced by the CSV file.
     * <p>
     * <b>Preconditions:</b> {@code exercise} and {@code zipFile} are non-{@code null}, and {@code exercise} is a persisted programming exercise (it has an id, a course reachable
     * via
     * {@code getCourseViaExerciseGroupOrCourseMember()} and a positive {@code maxPoints}).
     * <p>
     * <b>Postconditions:</b> if the returned result has no {@link AssessmentUploadResultDTO#errors() errors}, then for every CSV row the participant's latest submission carries a
     * rated manual result with {@code score = overallPoints / maxPoints * 100} and a single {@code MANUAL_UNREFERENCED} feedback carrying the text-file content — an existing
     * manual result there was overwritten in place, otherwise a new one was created — while automatic results are kept; a manual result on an older, superseded submission is left
     * untouched (it never contributes to the score, which is always taken from the latest submission, mirroring the assessment editor). No other persistent state changed. If the
     * result has errors, the persistent state is left completely unchanged (all-or-nothing).
     *
     * @param exercise the programming exercise the assessments belong to; must not be {@code null}
     * @param zipFile  the uploaded zip file containing {@code assessment-scores.csv} and one {@code .txt} file per participant; must not be {@code null}
     * @return a result describing either the created assessments (on success) or the collected validation errors (on failure); nothing is stored in the latter case
     * @throws IllegalArgumentException if a precondition is violated
     */
    public AssessmentUploadResultDTO importAssessments(final ProgrammingExercise exercise, final MultipartFile zipFile) {
        if (exercise == null) {
            throw new IllegalArgumentException("The exercise for a manual assessment upload must not be null");
        }
        if (zipFile == null) {
            throw new IllegalArgumentException("The zip file for a manual assessment upload must not be null");
        }
        if (exercise.getId() == null) {
            throw new IllegalArgumentException("The exercise for a manual assessment upload must be persisted");
        }
        if (exercise.getCourseViaExerciseGroupOrCourseMember() == null) {
            throw new IllegalArgumentException("The exercise for a manual assessment upload must belong to a course");
        }
        if (exercise.getMaxPoints() == null || exercise.getMaxPoints() <= 0) {
            throw new IllegalArgumentException("The exercise for a manual assessment upload must have positive maximum points");
        }
        // A single uploaded result cannot represent several correction rounds, so uploads are limited to exercises with exactly one round (all course exercises, and exams
        // configured for one round). Checked before anything is read or stored.
        final Integer numberOfCorrectionRounds = exercise.getNumberOfCorrectionRounds();
        if (numberOfCorrectionRounds == null || numberOfCorrectionRounds != 1) {
            throw new BadRequestAlertException("Manual assessments can only be uploaded for exercises with exactly one correction round", ENTITY_NAME,
                    "assessmentUpload.multipleCorrectionRounds");
        }
        // Respect the same manual-result gate as the assessment editor (manual assessment configured and the relevant due date passed).
        if (!exercise.areManualResultsAllowed()) {
            throw new BadRequestAlertException("Manual assessments are not allowed for this exercise yet", ENTITY_NAME, "assessmentUpload.manualResultsNotAllowed");
        }

        final ZipContents contents = archiveParser.readZipContents(zipFile);
        if (!contents.duplicateTextFileBaseNames().isEmpty()) {
            final List<AssessmentUploadErrorDTO> duplicateErrors = contents.duplicateTextFileBaseNames().stream().sorted()
                    .map(baseName -> AssessmentUploadErrorDTO.of(baseName + TEXT_FILE_EXTENSION, AssessmentUploadErrorType.DUPLICATE_TEXT_FILE)).toList();
            return AssessmentUploadResultDTO.failure(duplicateErrors);
        }
        final CsvParseResult csvResult = archiveParser.parseCsv(contents);
        if (csvResult instanceof CsvParseError(AssessmentUploadErrorType error)) {
            return AssessmentUploadResultDTO.failure(List.of(AssessmentUploadErrorDTO.of(error)));
        }
        // The sealed type guarantees that a non-error result is a ParsedCsv.
        final ParsedCsv csv = (ParsedCsv) csvResult;

        final List<AssessmentUploadErrorDTO> errors = new ArrayList<>();
        final double maximumPoints = exercise.getMaxPoints() + Objects.requireNonNullElse(exercise.getBonusPoints(), 0.0);
        final List<ValidatedRow> validatedRows = validateRows(exercise, csv, contents.textContentsByBaseName(), maximumPoints, errors);
        if (!errors.isEmpty()) {
            return AssessmentUploadResultDTO.failure(errors);
        }

        return transactionTemplate.execute(status -> storeValidatedRows(exercise, validatedRows));
    }

    /**
     * Validates every CSV row against the participations of the exercise and the available text files. Collects all errors and returns the rows that passed validation. Cross-row
     * concerns (duplicate identifiers and text files that are not referenced by any row) are handled here; the per-row checks are delegated to {@link #validateRow}.
     * <p>
     * <b>Preconditions:</b> {@code errors} is a mutable, initially empty list used as an out-parameter to collect all findings; the reference arguments are non-{@code null}; and
     * {@code maximumPoints} is finite and positive.
     * <p>
     * <b>Postconditions:</b> read-only with respect to persistent state; one entry is appended to {@code errors} per invalid row and one per text file not referenced by any row.
     * The returned rows are the fully valid ones, but they must only be stored if {@code errors} is still empty afterwards (the caller enforces the all-or-nothing rule).
     *
     * @param exercise               the programming exercise the assessments belong to
     * @param csv                    the successfully parsed CSV
     * @param textContentsByBaseName the available text files keyed by base name
     * @param maximumPoints          maximum accepted points including bonus points
     * @param errors                 out-parameter collecting every validation error found
     * @return the rows that passed all per-row checks
     */
    private List<ValidatedRow> validateRows(final ProgrammingExercise exercise, final ParsedCsv csv, final Map<String, String> textContentsByBaseName, final double maximumPoints,
            final List<AssessmentUploadErrorDTO> errors) {
        assert exercise != null && csv != null && textContentsByBaseName != null : "exercise, csv and textContentsByBaseName must not be null";
        assert Double.isFinite(maximumPoints) && maximumPoints > 0 : "maximumPoints must be finite and positive";
        assert errors != null && errors.isEmpty() : "errors must be a mutable, initially empty list";
        final List<ValidatedRow> validatedRows = new ArrayList<>();
        final Set<String> seenIdentifiers = new HashSet<>();
        final Set<String> matchedTextKeys = new HashSet<>();
        final Set<Long> requestedParticipationIds = csv.records().stream().map(this::extractIdentifier).map(this::parseParticipationId).flatMap(Optional::stream)
                .collect(Collectors.toSet());
        final Map<Long, AssessmentUploadParticipationDTO> participationsById = requestedParticipationIds.isEmpty() ? Map.of()
                : assessmentUploadParticipationRepository.findAssessmentUploadParticipations(exercise.getId(), requestedParticipationIds).stream()
                        .collect(Collectors.toMap(AssessmentUploadParticipationDTO::participationId, Function.identity()));
        final Set<Long> unresolvedParticipationIds = new HashSet<>(requestedParticipationIds);
        unresolvedParticipationIds.removeAll(participationsById.keySet());
        final Set<Long> participationIdsOutsideExercise = unresolvedParticipationIds.isEmpty() ? Set.of()
                : assessmentUploadParticipationRepository.findIdsOutsideExercise(exercise.getId(), unresolvedParticipationIds);
        // A text file is "referenced" if some row's identifier matches it (exact or exported-folder suffix), even when that row fails another check (e.g. invalid points). Only
        // text files that no row references at all are reported as UNMATCHED_TEXT_FILE.
        final Set<String> referencedTextKeys = csv.records().stream().map(this::extractIdentifier).filter(identifier -> !identifier.isBlank())
                .flatMap(identifier -> findMatchingTextKeys(textContentsByBaseName.keySet(), identifier).stream()).collect(Collectors.toSet());

        for (final CSVRecord csvRecord : csv.records()) {
            final String identifier = extractIdentifier(csvRecord);
            if (identifier.isBlank()) {
                errors.add(AssessmentUploadErrorDTO.of(null, AssessmentUploadErrorType.MISSING_IDENTIFIER, "row " + csvRecord.getRecordNumber()));
                continue;
            }
            if (!seenIdentifiers.add(identifier)) {
                errors.add(AssessmentUploadErrorDTO.of(identifier, AssessmentUploadErrorType.DUPLICATE_IDENTIFIER));
                continue;
            }

            switch (validateRow(identifier, csvRecord, csv.pointsColumn(), textContentsByBaseName, matchedTextKeys, participationsById, participationIdsOutsideExercise,
                    maximumPoints)) {
                case ValidRow(ValidatedRow row, String matchedTextKey) -> {
                    matchedTextKeys.add(matchedTextKey);
                    validatedRows.add(row);
                }
                case InvalidRow(AssessmentUploadErrorDTO error) -> errors.add(error);
            }
        }

        addErrorsForUnmatchedTextFiles(textContentsByBaseName.keySet(), referencedTextKeys, errors);
        return validatedRows;
    }

    /**
     * Adds an {@code UNMATCHED_TEXT_FILE} error for every text file whose base name no CSV row referenced. A file referenced by a row that failed another check (e.g. invalid
     * points) is not reported here, because it is not orphaned; the row's own error already rejects the upload.
     * <p>
     * <b>Preconditions:</b> all parameters are non-{@code null} and {@code errors} is mutable.
     * <p>
     * <b>Postcondition:</b> one error has been appended for every key in {@code textKeys} that is absent from {@code referencedTextKeys}.
     *
     * @param textKeys           the base names of all text files in the upload
     * @param referencedTextKeys the base names referenced by at least one CSV row (regardless of that row's validity)
     * @param errors             out-parameter the unmatched-file errors are appended to
     */
    private void addErrorsForUnmatchedTextFiles(final Set<String> textKeys, final Set<String> referencedTextKeys, final List<AssessmentUploadErrorDTO> errors) {
        assert textKeys != null && referencedTextKeys != null && errors != null : "textKeys, referencedTextKeys and errors must not be null";
        textKeys.stream().filter(key -> !referencedTextKeys.contains(key)).sorted()
                .forEach(key -> errors.add(AssessmentUploadErrorDTO.of(key + TEXT_FILE_EXTENSION, AssessmentUploadErrorType.UNMATCHED_TEXT_FILE)));
    }

    /**
     * Validates a single CSV row: resolves its participation by id (scoped to the exercise and cross-checked against the login part of the identifier), parses the achieved points,
     * and finds the matching text file.
     * <p>
     * <b>Preconditions:</b> all reference parameters are non-{@code null}; {@code identifier} and {@code pointsColumn} are non-blank; {@code matchedTextKeys} contains the keys
     * assigned to earlier valid rows; and {@code maximumPoints} is finite and positive.
     * <p>
     * <b>Postcondition:</b> read-only; returns a {@link ValidRow} (with the matched text-file key) if all checks pass, otherwise an {@link InvalidRow} carrying the first error.
     *
     * @param identifier                      the student identifier from the first CSV column ({@code <participationId>-<login>})
     * @param csvRecord                       the CSV row being validated
     * @param pointsColumn                    the resolved header name of the {@code Overall points} column
     * @param textContentsByBaseName          the available text files keyed by base name
     * @param matchedTextKeys                 text-file keys already assigned to earlier valid rows
     * @param participationsById              exercise-scoped participation data resolved for the entire upload
     * @param participationIdsOutsideExercise ids that exist but belong to another exercise
     * @param maximumPoints                   maximum accepted points including bonus points
     * @return a {@link ValidRow} if the row is valid, otherwise an {@link InvalidRow}
     */
    private RowValidationResult validateRow(final String identifier, final CSVRecord csvRecord, final String pointsColumn, final Map<String, String> textContentsByBaseName,
            final Set<String> matchedTextKeys, final Map<Long, AssessmentUploadParticipationDTO> participationsById, final Set<Long> participationIdsOutsideExercise,
            final double maximumPoints) {
        assert csvRecord != null && textContentsByBaseName != null && matchedTextKeys != null : "CSV and text-file data must not be null";
        assert participationsById != null && participationIdsOutsideExercise != null : "resolved participation data must not be null";
        assert identifier != null && !identifier.isBlank() : "identifier must not be blank";
        assert pointsColumn != null && !pointsColumn.isBlank() : "pointsColumn must be resolved";
        assert Double.isFinite(maximumPoints) && maximumPoints > 0 : "maximumPoints must be finite and positive";

        final Optional<Long> participationId = parseParticipationId(identifier);
        if (participationId.isEmpty()) {
            return new InvalidRow(AssessmentUploadErrorDTO.of(identifier, AssessmentUploadErrorType.INVALID_IDENTIFIER_FORMAT));
        }
        final String login = identifier.substring(identifier.indexOf('-') + 1);

        final AssessmentUploadParticipationDTO participation = participationsById.get(participationId.get());
        if (participation == null && participationIdsOutsideExercise.contains(participationId.get())) {
            return new InvalidRow(AssessmentUploadErrorDTO.of(identifier, AssessmentUploadErrorType.PARTICIPATION_WRONG_EXERCISE));
        }
        if (participation == null) {
            return new InvalidRow(AssessmentUploadErrorDTO.of(identifier, AssessmentUploadErrorType.PARTICIPATION_NOT_FOUND));
        }
        if (!login.equals(participation.participantIdentifier())) {
            return new InvalidRow(AssessmentUploadErrorDTO.of(identifier, AssessmentUploadErrorType.IDENTIFIER_MISMATCH, participation.participantIdentifier()));
        }

        // An unset cell is represented as an empty string so no null is passed on; parsePoints then treats it as a missing value.
        final String rawPoints = csvRecord.isSet(pointsColumn) ? csvRecord.get(pointsColumn) : "";
        final Optional<Double> points = parsePoints(rawPoints, maximumPoints);
        if (points.isEmpty()) {
            return new InvalidRow(AssessmentUploadErrorDTO.of(identifier, AssessmentUploadErrorType.INVALID_POINTS, rawPoints));
        }

        final List<String> matchingTextKeys = findMatchingTextKeys(textContentsByBaseName.keySet(), identifier);
        if (matchingTextKeys.isEmpty()) {
            return new InvalidRow(AssessmentUploadErrorDTO.of(identifier, AssessmentUploadErrorType.MISSING_TEXT_FILE));
        }
        if (matchingTextKeys.size() > 1 || matchedTextKeys.contains(matchingTextKeys.getFirst())) {
            final String matchingFileNames = matchingTextKeys.stream().map(key -> key + TEXT_FILE_EXTENSION).collect(Collectors.joining(", "));
            return new InvalidRow(AssessmentUploadErrorDTO.of(identifier, AssessmentUploadErrorType.AMBIGUOUS_TEXT_FILE, matchingFileNames));
        }
        final String textKey = matchingTextKeys.getFirst();

        return new ValidRow(new ValidatedRow(identifier, participation.participationId(), points.get(), textContentsByBaseName.get(textKey)), textKey);
    }

    /**
     * Stores a manual assessment for every validated row and returns a success result listing the affected participants.
     * <p>
     * <b>Preconditions:</b> {@code exercise} is persisted and belongs to a course, {@code validatedRows} is non-empty, every row passed validation (participation resolved and
     * belonging to {@code exercise}, points parsed, matching text file present), and the upload as a whole was error-free.
     * <p>
     * <b>Postcondition:</b> if assessment is possible and no target participation has an open complaint on its current manual assessment, every row has a manual assessment — the
     * participant's existing manual assessment edited in place (score, feedback, assessor and completion date), or a new one attached to the submission's results collection —
     * and the returned result lists the stored identifiers and carries no errors. Otherwise nothing is stored at all (missing submissions are created only after the complaint gate
     * passes, so a structured failure return commits no new submission either): a complaint yields one
     * {@code EXISTING_COMPLAINT} error per affected participation (all-or-nothing), and a closed assessment window propagates an exception.
     *
     * @param exercise      the programming exercise the assessments belong to
     * @param validatedRows the fully validated rows to store
     * @return a success result listing the stored assessments, or a failure result if a complaint blocks the upload
     * @throws org.springframework.web.server.ResponseStatusException if assessment of the exercise is not currently possible (e.g. the exam is still running)
     */
    private AssessmentUploadResultDTO storeValidatedRows(final ProgrammingExercise exercise, final List<ValidatedRow> validatedRows) {
        assert exercise != null && exercise.getId() != null : "exercise must be persisted";
        assert validatedRows != null && !validatedRows.isEmpty() : "validatedRows must not be null or empty";
        final List<Long> participationIds = validatedRows.stream().map(ValidatedRow::participationId).toList();

        final Map<Long, StudentParticipation> participationsById = assessmentUploadParticipationRepository.findAllForAssessmentUpload(exercise.getId(), participationIds).stream()
                .collect(Collectors.toMap(StudentParticipation::getId, Function.identity()));
        // Enforce the shared assessment-availability gate (e.g. an exam that is not over for all students yet) for every target participation before touching any result.
        participationsById.values().forEach(participation -> submissionService.checkThatAssessmentIsPossibleElseThrow(exercise, participation));

        final Map<Long, Submission> latestSubmissionsByParticipationId = submissionRepository.findLatestSubmissionsForAssessmentUpload(exercise.getId(), participationIds).stream()
                .collect(Collectors.toMap(submission -> submission.getParticipation().getId(), Function.identity()));
        // First pass: find the manual assessment that already exists for each row, reading only the already existing latest submissions and without mutating anything yet. Only
        // the latest submission is assessed; a manual result on an older, superseded submission is intentionally left untouched — it never contributes to the score (the grade is
        // always taken from the latest submission) and is not the participation's latest result, exactly as the normal assessment editor behaves. A participation without a
        // submission has no assessment to overwrite; its submission is created only in the second pass, so a complaint-blocked upload that returns a structured failure below
        // persists nothing.
        final Map<Long, Result> existingManualResultsByParticipationId = new HashMap<>();
        for (final ValidatedRow row : validatedRows) {
            final Submission existingSubmission = latestSubmissionsByParticipationId.get(row.participationId());
            if (existingSubmission == null) {
                continue;
            }
            // The upload is limited to exercises with a single correction round, so there is at most one manual result here; picking the latest keeps the behavior well defined if
            // a submission ever carries several.
            existingSubmission.getResults().stream().filter(result -> result != null && result.isManual() && result.getId() != null).max(Comparator.comparing(Result::getId))
                    .ifPresent(result -> existingManualResultsByParticipationId.put(row.participationId(), result));
        }

        // Reject (instead of silently changing) participations whose current manual assessment is referenced by a complaint: the student is contesting exactly that assessment, so
        // overwriting its score and feedback while the complaint is open would leave the complaint and any response referring to an assessment that no longer exists in that form.
        // The check is scoped to the results that would actually be overwritten (the manual results on the latest submission), so a complaint on a superseded submission's result,
        // which the upload leaves untouched, does not block the upload.
        final List<Long> overwrittenResultIds = existingManualResultsByParticipationId.values().stream().map(Result::getId).toList();
        final Set<Long> participationsWithComplaint = assessmentUploadResultService.findParticipationsWithComplaintOnResults(overwrittenResultIds);
        if (!participationsWithComplaint.isEmpty()) {
            return AssessmentUploadResultDTO.failure(buildComplaintErrors(validatedRows, participationsWithComplaint));
        }

        // Second pass, reached only once the upload is guaranteed to succeed (so a rejected upload persists nothing): edit the participant's existing manual assessment in place,
        // or — for a participant assessed for the first time — create one on the latest submission, creating and persisting a submitted external submission for a participant
        // without one. Editing instead of deleting and re-creating keeps everything that references the assessment (ratings, participant scores, complaints on superseded
        // results) intact and mirrors how the assessment editor updates an existing assessment.
        final List<Result> newResults = new ArrayList<>();
        final List<Result> updatedResults = new ArrayList<>();
        for (final ValidatedRow row : validatedRows) {
            final Result existingManualResult = existingManualResultsByParticipationId.get(row.participationId());
            if (existingManualResult != null) {
                updateManualResult(existingManualResult, exercise, row);
                updatedResults.add(existingManualResult);
                continue;
            }
            final StudentParticipation participation = Optional.ofNullable(participationsById.get(row.participationId()))
                    .orElseThrow(() -> new IllegalStateException("Validated participation %d is no longer available".formatted(row.participationId())));
            final Submission submission = Optional.ofNullable(latestSubmissionsByParticipationId.get(row.participationId()))
                    .orElseGet(() -> initializeSubmittedExternalSubmission(participation, exercise));
            final Result manualResult = buildManualResult(exercise, submission, row);
            submission.addResult(manualResult);
            newResults.add(manualResult);
        }
        assessmentUploadResultService.saveManualResults(newResults, updatedResults, true);

        final List<String> createdIdentifiers = validatedRows.stream().map(ValidatedRow::identifier).toList();
        log.info("Stored {} manual assessments ({} newly created, {} overwritten) for programming exercise {} from an upload", createdIdentifiers.size(), newResults.size(),
                updatedResults.size(), exercise.getId());
        return AssessmentUploadResultDTO.success(createdIdentifiers);
    }

    /**
     * Builds one {@code EXISTING_COMPLAINT} error per validated row whose participation still has a complaint on its current manual assessment, sorted by identifier for a
     * deterministic response.
     * <p>
     * <b>Preconditions:</b> {@code validatedRows} and {@code participationsWithComplaint} are non-{@code null}, and {@code participationsWithComplaint} is non-empty.
     * <p>
     * <b>Postcondition:</b> pure function (no side effects); returns a non-empty list with one error per affected row.
     *
     * @param validatedRows               the rows that would have been stored
     * @param participationsWithComplaint the participation ids blocked by an existing complaint
     * @return the collected complaint errors
     */
    private List<AssessmentUploadErrorDTO> buildComplaintErrors(final List<ValidatedRow> validatedRows, final Set<Long> participationsWithComplaint) {
        assert validatedRows != null && participationsWithComplaint != null && !participationsWithComplaint.isEmpty() : "rows and blocked participations must be present";
        return validatedRows.stream().filter(row -> participationsWithComplaint.contains(row.participationId())).map(ValidatedRow::identifier).sorted()
                .map(identifier -> AssessmentUploadErrorDTO.of(identifier, AssessmentUploadErrorType.EXISTING_COMPLAINT)).toList();
    }

    /**
     * Creates an empty external submission for a participation that has none yet and marks it as submitted with the current date.
     * <p>
     * This mirrors the established external-submission path in {@link de.tum.cit.aet.artemis.exercise.service.ParticipationService}: an uploaded assessment must be attached to a
     * submitted, dated submission. A submission left unsubmitted and undated is omitted from finished-assessment queries and result views that require
     * {@code submission.submitted = TRUE}, which would hide the imported assessment for participants who never pushed a submission.
     * <p>
     * <b>Preconditions:</b> {@code participation} has no submission yet and {@code exercise} is the persisted programming exercise it belongs to.
     * <p>
     * <b>Postcondition:</b> a persisted, submitted external submission connected to {@code participation} is returned.
     *
     * @param participation the participation without a prior submission
     * @param exercise      the programming exercise the submission belongs to
     * @return the persisted, submitted external submission
     */
    private Submission initializeSubmittedExternalSubmission(final StudentParticipation participation, final ProgrammingExercise exercise) {
        assert participation != null && exercise != null : "participation and exercise must not be null";
        final Submission submission = submissionRepository.initializeSubmission(participation, exercise, SubmissionType.EXTERNAL);
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now());
        return submissionRepository.save(submission);
    }

    /**
     * Builds a manual result with all required exercise and submission references, the uploaded score, and the uploaded feedback.
     * <p>
     * <b>Preconditions:</b> all parameters are non-{@code null}, and {@code row} contains validated assessment data for the exercise.
     * <p>
     * <b>Postcondition:</b> returns a transient result attached to {@code submission}, with the uploaded score and exactly one manual unreferenced feedback.
     *
     * @param exercise   the programming exercise the result belongs to
     * @param submission the submission the result belongs to
     * @param row        the validated assessment data
     * @return the initialized manual result
     */
    private Result buildManualResult(final ProgrammingExercise exercise, final Submission submission, final ValidatedRow row) {
        assert exercise != null && submission != null && row != null : "exercise, submission and row must not be null";
        assert exercise.getId() != null && exercise.getMaxPoints() != null && exercise.getMaxPoints() > 0 : "exercise must be persisted and have positive maximum points";
        assert submission.getParticipation() != null && submission.getParticipation().getId() != null
                && submission.getParticipation().getId() == row.participationId() : "submission must belong to the validated participation";
        final Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        final Result result = new Result();
        result.setSubmission(submission);
        result.setExerciseId(exercise.getId());
        result.setScore(row.points(), exercise.getMaxPoints(), course);
        result.addFeedback(buildManualFeedback(row.feedbackText(), row.points()));
        return result;
    }

    /**
     * Overwrites an existing manual assessment with the uploaded score and feedback instead of deleting and re-creating it.
     * <p>
     * The previous feedback is cleared from the result's own feedback collection, which cascades and orphan-removes it (including its long feedback text). Everything that
     * references the result itself — ratings, participant scores and complaints on it — keeps pointing at a valid assessment, and the {@code @PostUpdate}
     * {@link de.tum.cit.aet.artemis.assessment.ResultListener} schedules the participant-score recomputation.
     * <p>
     * <b>Preconditions:</b> all parameters are non-{@code null}, {@code result} is a persisted manual result of {@code exercise} whose submission belongs to the row's
     * participation, and {@code row} contains validated assessment data for the exercise.
     * <p>
     * <b>Postcondition:</b> the result carries the uploaded score and exactly one manual unreferenced feedback; its identity and everything referencing it are preserved.
     *
     * @param result   the existing manual result to overwrite
     * @param exercise the programming exercise the result belongs to
     * @param row      the validated assessment data
     */
    private void updateManualResult(final Result result, final ProgrammingExercise exercise, final ValidatedRow row) {
        assert result != null && result.getId() != null : "result must be persisted";
        assert exercise != null && row != null : "exercise and row must not be null";
        assert exercise.getMaxPoints() != null && exercise.getMaxPoints() > 0 : "exercise must have positive maximum points";
        final Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        result.setExerciseId(exercise.getId());
        result.setScore(row.points(), exercise.getMaxPoints(), course);
        // Mutate the managed collection instead of replacing it: Hibernate rejects a swapped reference on an attached entity whose collection uses orphan removal, and the
        // in-place clear is what deletes the previous feedback (and its long feedback text) as an orphan.
        result.getFeedbacks().clear();
        result.addFeedback(buildManualFeedback(row.feedbackText(), row.points()));
    }

    /**
     * Builds the single unreferenced manual feedback that carries the uploaded text and the achieved points.
     * <p>
     * <b>Preconditions:</b> {@code feedbackText} is non-{@code null}, and {@code points} is finite and non-negative.
     * <p>
     * <b>Postcondition:</b> returns positive-via-credits {@code MANUAL_UNREFERENCED} feedback with the supplied text and points.
     *
     * @param feedbackText the content of the participant's text file, stored as the feedback detail text
     * @param points       the achieved points, stored as the feedback credits
     * @return the manual feedback
     */
    private Feedback buildManualFeedback(final String feedbackText, final double points) {
        assert feedbackText != null : "feedbackText must not be null";
        assert Double.isFinite(points) && points >= 0 : "points must be finite and non-negative";
        final Feedback feedback = new Feedback();
        feedback.setType(FeedbackType.MANUAL_UNREFERENCED);
        feedback.setDetailText(feedbackText);
        feedback.setCredits(points);
        feedback.setPositiveViaCredits();
        return feedback;
    }

    /**
     * Extracts the trimmed student identifier from the first column of a CSV row, or an empty string if the row has no first column or a {@code null} value there.
     * <p>
     * <b>Precondition:</b> {@code csvRecord} is non-{@code null}.
     * <p>
     * <b>Postcondition:</b> pure function (no side effects); returns a non-{@code null} string.
     *
     * @param csvRecord the CSV row
     * @return the trimmed first-column value, or an empty string
     */
    private String extractIdentifier(final CSVRecord csvRecord) {
        assert csvRecord != null : "csvRecord must not be null";
        return csvRecord.size() > 0 && csvRecord.get(0) != null ? csvRecord.get(0).trim() : "";
    }

    /**
     * Extracts the participation id (the numeric part before the first {@code -}) from an identifier of the form {@code <participationId>-<login>}.
     * <p>
     * <b>Precondition:</b> {@code identifier} is non-{@code null}.
     * <p>
     * <b>Postcondition:</b> pure function (no side effects); returns the parsed id, or {@link Optional#empty()} if there is no numeric part before a non-terminal first {@code -}.
     *
     * @param identifier the student identifier
     * @return the participation id, or {@link Optional#empty()} if the identifier is not in the expected format
     */
    private Optional<Long> parseParticipationId(final String identifier) {
        assert identifier != null : "identifier must not be null";
        final int firstDashIndex = identifier.indexOf('-');
        if (firstDashIndex <= 0 || firstDashIndex == identifier.length() - 1) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(identifier.substring(0, firstDashIndex)));
        }
        catch (final NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Parses the achieved points as a finite number between zero and the supplied maximum.
     * <p>
     * <b>Preconditions:</b> {@code rawPoints} is non-{@code null} (a missing cell is passed as an empty string), and {@code maximumPoints} is finite and positive.
     * <p>
     * <b>Postcondition:</b> pure function (no side effects); any present value is finite and between zero and {@code maximumPoints}, inclusive.
     *
     * @param rawPoints     the raw cell value of the {@code Overall points} column (empty string if the cell is missing)
     * @param maximumPoints maximum accepted points including bonus points
     * @return the parsed points, or {@link Optional#empty()} if the value is missing or outside the accepted range
     */
    private Optional<Double> parsePoints(final String rawPoints, final double maximumPoints) {
        assert rawPoints != null : "rawPoints must not be null";
        assert Double.isFinite(maximumPoints) && maximumPoints > 0 : "maximumPoints must be finite and positive";
        if (rawPoints.isBlank()) {
            return Optional.empty();
        }
        try {
            final double points = Double.parseDouble(rawPoints.trim());
            if (points < 0 || points > maximumPoints || !Double.isFinite(points)) {
                return Optional.empty();
            }
            return Optional.of(points);
        }
        catch (final NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Finds the text-file base names that can belong to a CSV identifier, in decreasing precedence: an exact identifier match, then the flat participation-id name the generated
     * template uses, then exported-folder suffix matches. The participation-id rule only ever matches a bare-numeric key (the participation id has no {@code -}, so such a key can
     * never be an exact identifier or an exported-folder suffix), which keeps it from colliding with an instructor's exported-repository upload.
     * <p>
     * <b>Precondition:</b> {@code textKeys} and {@code identifier} are non-{@code null}.
     * <p>
     * <b>Postcondition:</b> pure function (no side effects); returns the single exact key if present, otherwise the single participation-id key if present, otherwise all keys
     * ending
     * with {@code "-" + identifier} in sorted order.
     *
     * @param textKeys   the available text-file base names
     * @param identifier the student identifier to match
     * @return the preferred matching base names, or an empty list if no text file matches
     */
    private List<String> findMatchingTextKeys(final Set<String> textKeys, final String identifier) {
        assert textKeys != null && identifier != null : "textKeys and identifier must not be null";
        if (textKeys.contains(identifier)) {
            return List.of(identifier);
        }
        // The generated template names each feedback file after the path-safe participation id (see templateFeedbackFileName), because the identifier's login/team-short-name part
        // may contain a '/' that a zip reader strips. Match that flat name back to the row here.
        final Optional<Long> participationId = parseParticipationId(identifier);
        if (participationId.isPresent()) {
            final String participationIdKey = Long.toString(participationId.get());
            if (textKeys.contains(participationIdKey)) {
                return List.of(participationIdKey);
            }
        }
        return textKeys.stream().filter(key -> key.endsWith("-" + identifier)).sorted().toList();
    }

    /**
     * A CSV row that passed validation and can be turned into a manual assessment.
     * <p>
     * Invariant: all reference components are non-{@code null}, {@code participationId} identifies a participation in the target exercise, and {@code points >= 0}.
     *
     * @param identifier      the student identifier from the CSV ({@code <participationId>-<login>})
     * @param participationId the resolved participation id
     * @param points          the achieved points
     * @param feedbackText    the content of the matching text file, used as the manual feedback
     */
    private record ValidatedRow(String identifier, long participationId, double points, String feedbackText) {

        /**
         * <b>Preconditions:</b> {@code identifier} is non-blank, {@code participationId} identifies a persisted participation, {@code feedbackText} is non-{@code null}, and
         * {@code points} is finite and non-negative.
         *
         * @throws IllegalArgumentException if a precondition is violated
         */
        ValidatedRow {
            if (identifier == null || identifier.isBlank()) {
                throw new IllegalArgumentException("The student identifier must not be null or blank");
            }
            if (participationId <= 0) {
                throw new IllegalArgumentException("The participation id must identify a persisted participation");
            }
            if (!Double.isFinite(points) || points < 0) {
                throw new IllegalArgumentException("The achieved points must be finite and non-negative");
            }
            if (feedbackText == null) {
                throw new IllegalArgumentException("The feedback text must not be null");
            }
        }
    }

    /**
     * Outcome of validating a single CSV row: either a row that can be stored or the error that was found. Modeled as a sealed type so callers pattern-match instead of inspecting
     * nullable fields.
     */
    private sealed interface RowValidationResult permits ValidRow, InvalidRow {
    }

    /**
     * A CSV row that passed validation.
     *
     * @param row            the validated row to store
     * @param matchedTextKey the base name of the text file matched by the row
     */
    private record ValidRow(ValidatedRow row, String matchedTextKey) implements RowValidationResult {

        /**
         * <b>Preconditions:</b> {@code row} and {@code matchedTextKey} are non-{@code null}.
         *
         * @throws IllegalArgumentException if a parameter is {@code null}
         */
        ValidRow {
            if (row == null || matchedTextKey == null) {
                throw new IllegalArgumentException("The validated row and matched text key must not be null");
            }
        }
    }

    /**
     * A CSV row that failed validation.
     *
     * @param error the validation error found for the row
     */
    private record InvalidRow(AssessmentUploadErrorDTO error) implements RowValidationResult {

        /**
         * <b>Precondition:</b> {@code error} is non-{@code null}.
         *
         * @throws IllegalArgumentException if {@code error} is {@code null}
         */
        InvalidRow {
            if (error == null) {
                throw new IllegalArgumentException("The row validation error must not be null");
            }
        }
    }
}
