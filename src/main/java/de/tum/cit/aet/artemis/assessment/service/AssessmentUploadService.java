package de.tum.cit.aet.artemis.assessment.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
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
 * created. An existing manual assessment of a participant is overwritten.
 *
 * @see AssessmentUploadResultDTO
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class AssessmentUploadService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentUploadService.class);

    private static final String ENTITY_NAME = "assessmentUpload";

    /**
     * The mandatory name of the CSV file inside the uploaded zip (matched case-insensitively).
     */
    private static final String CSV_FILE_NAME = "assessment-scores.csv";

    /**
     * The mandatory column holding the achieved points (matched case-insensitively, ignoring surrounding whitespace).
     */
    private static final String OVERALL_POINTS_COLUMN = "Overall points";

    /** The extension (including the leading dot) of the per-participant feedback files inside the zip. */
    private static final String TEXT_FILE_EXTENSION = ".txt";

    /** Maximum number of entries processed from one archive, including ignored metadata entries. */
    private static final int MAX_ARCHIVE_ENTRY_COUNT = 10_000;

    /** Maximum uncompressed size of one archive entry. */
    private static final long MAX_ENTRY_UNCOMPRESSED_SIZE = 10L * 1024 * 1024;

    /** Maximum aggregate uncompressed size of all entries in one archive. */
    private static final long MAX_ARCHIVE_UNCOMPRESSED_SIZE = 100L * 1024 * 1024;

    /**
     * Maximum number of data rows read from the CSV file. Bounds the in-memory record list and the size of the derived id sets and {@code IN} queries, so a CSV with millions of
     * tiny rows cannot exhaust the heap or exceed database parameter limits.
     */
    private static final int MAX_CSV_ROW_COUNT = 10_000;

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
     * @param assessmentUploadParticipationRepository the repository used to resolve participants
     * @param submissionRepository                    the repository used to create missing submissions and to persist the ordered results collection
     * @param assessmentUploadResultService           the service used to replace manual assessment results
     * @param submissionService                       the service enforcing the shared assessment-availability gate
     * @param transactionManager                      the transaction manager used to store the complete upload atomically
     * @throws IllegalArgumentException if any parameter is {@code null}
     */
    public AssessmentUploadService(final AssessmentUploadParticipationRepository assessmentUploadParticipationRepository, final SubmissionRepository submissionRepository,
            final AssessmentUploadResultService assessmentUploadResultService, final SubmissionService submissionService, final PlatformTransactionManager transactionManager) {
        if (Stream.of(assessmentUploadParticipationRepository, submissionRepository, assessmentUploadResultService, submissionService, transactionManager)
                .anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("The assessment upload service dependencies must not be null");
        }
        this.assessmentUploadParticipationRepository = assessmentUploadParticipationRepository;
        this.submissionRepository = submissionRepository;
        this.assessmentUploadResultService = assessmentUploadResultService;
        this.submissionService = submissionService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Parses the uploaded zip file and, if it is valid, stores a manual assessment (score + feedback) for every participant referenced by the CSV file.
     * <p>
     * <b>Preconditions:</b> {@code exercise} and {@code zipFile} are non-{@code null}, and {@code exercise} is a persisted programming exercise (it has an id, a course reachable
     * via
     * {@code getCourseViaExerciseGroupOrCourseMember()} and a positive {@code maxPoints}).
     * <p>
     * <b>Postconditions:</b> if the returned result has no {@link AssessmentUploadResultDTO#errors() errors}, then for every CSV row a rated manual result with
     * {@code score = overallPoints / maxPoints * 100} and a single {@code MANUAL_UNREFERENCED} feedback carrying the text-file content has been created, replacing any previous
     * manual result of that participant, while automatic results are kept; no other persistent state changed. If the result has errors, the persistent state is left completely
     * unchanged (all-or-nothing).
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

        final ZipContents contents = readZipContents(zipFile);
        if (!contents.duplicateTextFileBaseNames().isEmpty()) {
            final List<AssessmentUploadErrorDTO> duplicateErrors = contents.duplicateTextFileBaseNames().stream().sorted()
                    .map(baseName -> AssessmentUploadErrorDTO.of(baseName + TEXT_FILE_EXTENSION, AssessmentUploadErrorType.DUPLICATE_TEXT_FILE)).toList();
            return AssessmentUploadResultDTO.failure(duplicateErrors);
        }
        final CsvParseResult csvResult = parseCsv(contents);
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
     * Reads the uploaded zip file into memory, collecting the bytes of every {@code assessment-scores.csv} file and the content of every {@code .txt} file keyed by its base name
     * (file name without the {@code .txt} extension). Directories, hidden files and macOS resource-fork entries are ignored.
     * <p>
     * <b>Precondition:</b> {@code zipFile} is non-{@code null}.
     * <p>
     * <b>Postcondition:</b> returns an in-memory, read-only view of the archive (no persistent state is changed).
     *
     * @param zipFile the uploaded zip file
     * @return the relevant zip contents
     * @throws BadRequestAlertException if the argument cannot be read as a zip file
     */
    private ZipContents readZipContents(final MultipartFile zipFile) {
        assert zipFile != null : "zipFile must not be null";
        final List<byte[]> csvFiles = new ArrayList<>();
        final Map<String, String> textContentsByBaseName = new LinkedHashMap<>();
        final Set<String> duplicateTextFileBaseNames = new HashSet<>();
        final ArchiveReadState archiveReadState = new ArchiveReadState();

        try (ZipInputStream zipInputStream = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                archiveReadState.incrementEntryCount();
                collectEntry(entry, zipInputStream, csvFiles, textContentsByBaseName, duplicateTextFileBaseNames, archiveReadState);
                zipInputStream.closeEntry();
            }
        }
        catch (final IOException e) {
            throw new BadRequestAlertException("The uploaded file could not be read as a zip file: " + e.getMessage(), ENTITY_NAME, "assessmentUpload.invalidZipFile");
        }

        return new ZipContents(csvFiles, textContentsByBaseName, duplicateTextFileBaseNames);
    }

    /**
     * Collects a single zip entry into the accumulators: appends the bytes of an {@code assessment-scores.csv} entry to {@code csvFiles}, and stores the content of a {@code .txt}
     * entry in {@code textContentsByBaseName} keyed by its name without the extension. Directories, macOS resource-fork entries and hidden files are ignored.
     * <p>
     * <b>Preconditions:</b> all parameters are non-{@code null}; {@code zipInputStream} is positioned at {@code entry}; the collections and archive state are mutable.
     * <p>
     * <b>Postcondition:</b> the complete entry was counted against the archive limits; at most one relevant entry was appended to its accumulator, and a repeated text-file base
     * name was added to {@code duplicateTextFileBaseNames} without replacing the first content.
     *
     * @param entry                      the current zip entry
     * @param zipInputStream             the stream positioned at the entry, used to read its bytes
     * @param csvFiles                   out-parameter collecting the bytes of every {@code assessment-scores.csv} entry
     * @param textContentsByBaseName     out-parameter collecting the text-file contents keyed by base name
     * @param duplicateTextFileBaseNames out-parameter collecting duplicate text-file base names
     * @param archiveReadState           cumulative archive entry and size counters
     * @throws IOException if reading the entry fails
     */
    private void collectEntry(final ZipEntry entry, final ZipInputStream zipInputStream, final List<byte[]> csvFiles, final Map<String, String> textContentsByBaseName,
            final Set<String> duplicateTextFileBaseNames, final ArchiveReadState archiveReadState) throws IOException {
        assert entry != null && zipInputStream != null : "entry and zipInputStream must not be null";
        assert csvFiles != null && textContentsByBaseName != null && duplicateTextFileBaseNames != null : "zip content accumulators must not be null";
        assert archiveReadState != null : "archiveReadState must not be null";
        if (entry.isDirectory()) {
            readEntryBytes(zipInputStream, archiveReadState, false);
            return;
        }
        final String entryName = entry.getName();
        // Skip macOS metadata that is added when creating zip archives on a Mac.
        if (entryName.contains("__MACOSX/")) {
            readEntryBytes(zipInputStream, archiveReadState, false);
            return;
        }
        final String baseName = entryName.substring(entryName.lastIndexOf('/') + 1);
        // Skip hidden files and macOS resource forks (e.g. "._file.txt").
        if (baseName.isBlank() || baseName.startsWith(".")) {
            readEntryBytes(zipInputStream, archiveReadState, false);
            return;
        }
        final String lowerCaseBaseName = baseName.toLowerCase(Locale.ROOT);
        if (lowerCaseBaseName.equals(CSV_FILE_NAME)) {
            csvFiles.add(readEntryBytes(zipInputStream, archiveReadState, true));
        }
        else if (lowerCaseBaseName.endsWith(TEXT_FILE_EXTENSION)) {
            final String baseNameWithoutExtension = baseName.substring(0, baseName.length() - TEXT_FILE_EXTENSION.length());
            final String textContent = new String(readEntryBytes(zipInputStream, archiveReadState, true), StandardCharsets.UTF_8);
            if (textContentsByBaseName.putIfAbsent(baseNameWithoutExtension, textContent) != null) {
                duplicateTextFileBaseNames.add(baseNameWithoutExtension);
            }
        }
        else {
            readEntryBytes(zipInputStream, archiveReadState, false);
        }
    }

    /**
     * Reads the current archive entry with per-entry and aggregate uncompressed-size enforcement.
     * <p>
     * <b>Preconditions:</b> {@code zipInputStream} and {@code archiveReadState} are non-{@code null}, the stream is positioned at an open entry, and the state belongs to the same
     * archive.
     * <p>
     * <b>Postcondition:</b> every read byte was added to the aggregate counter; returns all entry bytes when retained and an empty array otherwise. An exceeded uncompressed-size
     * limit prevents a normal return.
     *
     * @param zipInputStream   stream positioned at the entry
     * @param archiveReadState cumulative archive counters
     * @param retainBytes      whether to return the bytes or discard them after counting
     * @return the entry bytes when retained, otherwise an empty array
     * @throws IOException if reading the entry fails
     */
    private byte[] readEntryBytes(final ZipInputStream zipInputStream, final ArchiveReadState archiveReadState, final boolean retainBytes) throws IOException {
        assert zipInputStream != null && archiveReadState != null : "zipInputStream and archiveReadState must not be null";
        final ByteArrayOutputStream output = retainBytes ? new ByteArrayOutputStream() : null;
        final byte[] buffer = new byte[8192];
        long entrySize = 0;
        int bytesRead;
        while ((bytesRead = zipInputStream.read(buffer)) != -1) {
            entrySize += bytesRead;
            archiveReadState.addUncompressedBytes(bytesRead);
            if (entrySize > MAX_ENTRY_UNCOMPRESSED_SIZE) {
                throw archiveLimitExceeded("A zip entry exceeds the maximum uncompressed size");
            }
            if (output != null) {
                output.write(buffer, 0, bytesRead);
            }
        }
        return output != null ? output.toByteArray() : new byte[0];
    }

    /**
     * Builds the client-visible bad-request exception used when an archive exceeds a processing limit.
     * <p>
     * <b>Precondition:</b> {@code message} is non-{@code null} and non-blank.
     * <p>
     * <b>Postcondition:</b> returns a new exception associated with the assessment-upload invalid-zip error key.
     *
     * @param message description of the exceeded limit
     * @return the exception to throw
     */
    private BadRequestAlertException archiveLimitExceeded(final String message) {
        assert message != null && !message.isBlank() : "message must not be null or blank";
        return new BadRequestAlertException(message, ENTITY_NAME, "assessmentUpload.invalidZipFile");
    }

    /**
     * Runs all structural checks on the archive that must pass before any row can be validated: there has to be exactly one {@code assessment-scores.csv} file that can be parsed,
     * has at least one data row, and contains the mandatory {@code Overall points} column.
     * <p>
     * <b>Precondition:</b> {@code contents} is non-{@code null}.
     * <p>
     * <b>Postcondition:</b> read-only with respect to persistent state; returns a {@link ParsedCsv} if the archive is well-formed, otherwise a {@link CsvParseError} describing the
     * structural problem.
     *
     * @param contents the in-memory zip contents
     * @return the parsed CSV, or a terminal parse error
     */
    private CsvParseResult parseCsv(final ZipContents contents) {
        assert contents != null : "contents must not be null";
        if (contents.csvFiles().isEmpty()) {
            return new CsvParseError(AssessmentUploadErrorType.MISSING_CSV);
        }
        if (contents.csvFiles().size() > 1) {
            return new CsvParseError(AssessmentUploadErrorType.MULTIPLE_CSV);
        }
        return parseCsvContent(stripByteOrderMark(new String(contents.csvFiles().getFirst(), StandardCharsets.UTF_8)));
    }

    /**
     * Parses the text of the single CSV file into records, resolving the mandatory {@code Overall points} column.
     * <p>
     * <b>Precondition:</b> {@code csvContent} is non-{@code null}.
     * <p>
     * <b>Postcondition:</b> returns a {@link ParsedCsv} with non-empty records and a resolved points column, or a {@link CsvParseError} if the column is missing, there are no data
     * rows, or the text cannot be parsed as CSV.
     *
     * @param csvContent the CSV text (already stripped of a leading byte-order mark)
     * @return the parsed CSV, or a terminal parse error
     */
    private CsvParseResult parseCsvContent(final String csvContent) {
        assert csvContent != null : "csvContent must not be null";
        final CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreSurroundingSpaces(true).setIgnoreEmptyLines(true).setTrim(true).get();
        try (CSVParser parser = CSVParser.parse(new StringReader(csvContent), format)) {
            final Optional<String> pointsColumn = parser.getHeaderNames().stream().filter(header -> header != null && header.trim().equalsIgnoreCase(OVERALL_POINTS_COLUMN))
                    .findFirst();
            if (pointsColumn.isEmpty()) {
                return new CsvParseError(AssessmentUploadErrorType.MISSING_OVERALL_POINTS_COLUMN);
            }
            final List<CSVRecord> records = readBoundedRecords(parser);
            if (records.isEmpty()) {
                return new CsvParseError(AssessmentUploadErrorType.EMPTY_CSV);
            }
            return new ParsedCsv(records, pointsColumn.get());
        }
        catch (final IOException | UncheckedIOException | IllegalArgumentException e) {
            return new CsvParseError(AssessmentUploadErrorType.MALFORMED_CSV);
        }
    }

    /**
     * Reads the data rows of the CSV parser into memory, rejecting a file that exceeds {@link #MAX_CSV_ROW_COUNT} before materializing or querying the excess rows. Iterating the
     * parser lazily (instead of {@link CSVParser#getRecords()}) keeps at most {@link #MAX_CSV_ROW_COUNT} records in memory.
     * <p>
     * <b>Precondition:</b> {@code parser} is non-{@code null} and positioned after the header record.
     * <p>
     * <b>Postcondition:</b> returns at most {@link #MAX_CSV_ROW_COUNT} records; a CSV with more rows is rejected via {@link BadRequestAlertException}.
     *
     * @param parser the CSV parser to drain
     * @return the parsed data rows, at most {@link #MAX_CSV_ROW_COUNT}
     * @throws BadRequestAlertException if the CSV file contains more than {@link #MAX_CSV_ROW_COUNT} rows
     */
    private List<CSVRecord> readBoundedRecords(final CSVParser parser) {
        assert parser != null : "parser must not be null";
        final List<CSVRecord> records = new ArrayList<>();
        for (final CSVRecord csvRecord : parser) {
            if (records.size() >= MAX_CSV_ROW_COUNT) {
                throw archiveLimitExceeded("The CSV file contains more than the maximum of " + MAX_CSV_ROW_COUNT + " rows");
            }
            records.add(csvRecord);
        }
        return records;
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
     * <b>Postcondition:</b> if assessment is possible and no target participation has a complaint on its current manual assessment, a manual assessment has been created (or
     * overwritten) for each row — attached to the submission's ordered results collection so {@code results_order} stays unique and contiguous — and the returned result lists the
     * stored identifiers and carries no errors. Otherwise nothing is stored: a complaint yields one {@code EXISTING_COMPLAINT} error per affected participation (all-or-nothing),
     * and a closed assessment window propagates an exception.
     *
     * @param exercise      the programming exercise the assessments belong to
     * @param validatedRows the fully validated rows to store
     * @return a success result listing the created assessments, or a failure result if a complaint blocks the upload
     * @throws org.springframework.web.server.ResponseStatusException if assessment of the exercise is not currently possible (e.g. the exam is still running)
     */
    private AssessmentUploadResultDTO storeValidatedRows(final ProgrammingExercise exercise, final List<ValidatedRow> validatedRows) {
        assert exercise != null && exercise.getId() != null : "exercise must be persisted";
        assert validatedRows != null && !validatedRows.isEmpty() : "validatedRows must not be null or empty";
        final List<Long> participationIds = validatedRows.stream().map(ValidatedRow::participationId).toList();
        assessmentUploadParticipationRepository.lockAllForAssessmentUpload(exercise.getId(), participationIds);

        // Reject (instead of silently destroying) participations whose current manual assessment is referenced by a complaint. Checked inside the locked transaction, before any
        // deletion, so nothing is stored (all-or-nothing).
        final Set<Long> participationsWithComplaint = assessmentUploadResultService.findParticipationsWithComplaint(exercise.getId(), participationIds);
        if (!participationsWithComplaint.isEmpty()) {
            return AssessmentUploadResultDTO.failure(buildComplaintErrors(validatedRows, participationsWithComplaint));
        }

        final Map<Long, StudentParticipation> participationsById = assessmentUploadParticipationRepository.findAllForAssessmentUpload(exercise.getId(), participationIds).stream()
                .collect(Collectors.toMap(StudentParticipation::getId, Function.identity()));
        // Enforce the shared assessment-availability gate (e.g. an exam that is not over for all students yet) for every target participation before touching any result.
        participationsById.values().forEach(participation -> submissionService.checkThatAssessmentIsPossibleElseThrow(exercise, participation));

        final Map<Long, Submission> latestSubmissionsByParticipationId = submissionRepository.findLatestSubmissionsForAssessmentUpload(exercise.getId(), participationIds).stream()
                .collect(Collectors.toMap(submission -> submission.getParticipation().getId(), Function.identity()));
        final List<Result> manualResults = new ArrayList<>();
        final List<Long> replacedResultIds = new ArrayList<>();
        for (final ValidatedRow row : validatedRows) {
            final StudentParticipation participation = Optional.ofNullable(participationsById.get(row.participationId()))
                    .orElseThrow(() -> new IllegalStateException("Validated participation %d is no longer available".formatted(row.participationId())));
            final Submission submission = Optional.ofNullable(latestSubmissionsByParticipationId.get(row.participationId()))
                    .orElseGet(() -> submissionRepository.initializeSubmission(participation, exercise, SubmissionType.EXTERNAL));
            // Remove the existing manual result(s) from the submission's ordered results collection so Hibernate deletes them via orphan removal and keeps results_order unique and
            // contiguous, then attach the replacement to the same collection.
            final List<Result> existingManualResults = submission.getResults().stream().filter(result -> result != null && result.isManual()).toList();
            existingManualResults.stream().map(Result::getId).filter(Objects::nonNull).forEach(replacedResultIds::add);
            submission.getResults().removeAll(existingManualResults);
            final Result manualResult = buildManualResult(exercise, submission, row);
            submission.addResult(manualResult);
            manualResults.add(manualResult);
        }
        // Remove the references Hibernate cannot cascade-delete from a Result (complaints/responses, ratings, participant scores) before the orphan removal is flushed.
        assessmentUploadResultService.deleteNonCascadedResultReferences(replacedResultIds);
        assessmentUploadResultService.createNewManualResults(manualResults, true);

        final List<String> createdIdentifiers = validatedRows.stream().map(ValidatedRow::identifier).toList();
        log.info("Stored {} manual assessments for programming exercise {} from an upload", createdIdentifiers.size(), exercise.getId());
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
     * Finds the text-file base names that can belong to a CSV identifier. An exact match takes precedence over exported-folder suffix matches.
     * <p>
     * <b>Precondition:</b> {@code textKeys} and {@code identifier} are non-{@code null}.
     * <p>
     * <b>Postcondition:</b> pure function (no side effects); returns only the exact key when it exists, otherwise all keys ending with {@code "-" + identifier} in sorted order.
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
        return textKeys.stream().filter(key -> key.endsWith("-" + identifier)).sorted().toList();
    }

    /**
     * Removes a leading UTF-8 byte-order mark that spreadsheet applications (e.g. Excel) tend to prepend, so it does not become part of the first header name.
     * <p>
     * <b>Precondition:</b> {@code content} is non-{@code null}.
     * <p>
     * <b>Postcondition:</b> pure function (no side effects); the result never starts with a {@code U+FEFF} character.
     *
     * @param content the raw CSV text
     * @return the text without a leading byte-order mark
     */
    private String stripByteOrderMark(final String content) {
        assert content != null : "content must not be null";
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
            return content.substring(1);
        }
        return content;
    }

    /**
     * In-memory representation of the relevant zip contents.
     * <p>
     * Invariant: all components are non-{@code null} (each may be empty).
     *
     * @param csvFiles                   the bytes of every {@code assessment-scores.csv} file found (used to detect a missing or ambiguous CSV)
     * @param textContentsByBaseName     the content of each {@code .txt} file, keyed by its file name without the {@code .txt} extension
     * @param duplicateTextFileBaseNames base names that occurred more than once
     */
    private record ZipContents(List<byte[]> csvFiles, Map<String, String> textContentsByBaseName, Set<String> duplicateTextFileBaseNames) {

        /**
         * <b>Precondition:</b> all components are non-{@code null}.
         *
         * @throws IllegalArgumentException if a parameter is {@code null}
         */
        ZipContents {
            if (csvFiles == null || textContentsByBaseName == null || duplicateTextFileBaseNames == null) {
                throw new IllegalArgumentException("CSV files, text contents and duplicate text file names must not be null");
            }
        }
    }

    /** Mutable counters used while streaming one archive. */
    private final class ArchiveReadState {

        private int entryCount;

        private long totalUncompressedBytes;

        /**
         * <b>Postcondition:</b> the entry count increased by one and does not exceed {@link #MAX_ARCHIVE_ENTRY_COUNT}; otherwise an invalid-zip exception was thrown.
         */
        private void incrementEntryCount() {
            if (++entryCount > MAX_ARCHIVE_ENTRY_COUNT) {
                throw archiveLimitExceeded("The zip file contains too many entries");
            }
        }

        /**
         * <b>Precondition:</b> {@code bytesRead} is positive.
         * <p>
         * <b>Postcondition:</b> the aggregate byte count increased by {@code bytesRead} and does not exceed {@link #MAX_ARCHIVE_UNCOMPRESSED_SIZE}; otherwise an invalid-zip
         * exception was thrown.
         *
         * @param bytesRead number of newly decompressed bytes
         */
        private void addUncompressedBytes(final int bytesRead) {
            assert bytesRead > 0 : "bytesRead must be positive";
            totalUncompressedBytes += bytesRead;
            if (totalUncompressedBytes > MAX_ARCHIVE_UNCOMPRESSED_SIZE) {
                throw archiveLimitExceeded("The zip file exceeds the maximum total uncompressed size");
            }
        }
    }

    /**
     * Outcome of parsing the CSV file: either the parsed content or a terminal error. Modeled as a sealed type so callers pattern-match instead of inspecting nullable fields.
     */
    private sealed interface CsvParseResult permits ParsedCsv, CsvParseError {
    }

    /**
     * The successfully parsed CSV content.
     * <p>
     * Invariant: {@code records} is non-empty and {@code pointsColumn} is non-{@code null}.
     *
     * @param records      the parsed data rows
     * @param pointsColumn the resolved header name of the {@code Overall points} column
     */
    private record ParsedCsv(List<CSVRecord> records, String pointsColumn) implements CsvParseResult {

        /**
         * <b>Preconditions:</b> {@code records} is non-{@code null} and non-empty, and {@code pointsColumn} is non-{@code null}.
         *
         * @throws IllegalArgumentException if a precondition is violated
         */
        ParsedCsv {
            if (records == null || records.isEmpty()) {
                throw new IllegalArgumentException("CSV records must not be null or empty");
            }
            if (pointsColumn == null) {
                throw new IllegalArgumentException("The points column must not be null");
            }
        }
    }

    /**
     * A terminal error that prevented the CSV from being parsed into rows.
     *
     * @param error the terminal error
     */
    private record CsvParseError(AssessmentUploadErrorType error) implements CsvParseResult {

        /**
         * <b>Precondition:</b> {@code error} is non-{@code null}.
         *
         * @throws IllegalArgumentException if {@code error} is {@code null}
         */
        CsvParseError {
            if (error == null) {
                throw new IllegalArgumentException("The CSV parse error must not be null");
            }
        }
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
