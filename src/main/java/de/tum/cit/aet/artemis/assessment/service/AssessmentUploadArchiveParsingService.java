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
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentUploadErrorType;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

/**
 * Reads an uploaded manual-assessment archive into memory and parses its {@code assessment-scores.csv} into structured data. This class is intentionally free of any persistence
 * dependency: it only turns the multipart upload into either the parsed contents ({@link ZipContents} plus {@link ParsedCsv}) or a terminal {@link CsvParseError}, while enforcing
 * the archive and CSV size limits that protect the server from a maliciously large upload.
 * <p>
 * The {@link AssessmentUploadService} owns the surrounding workflow (validating the rows against the exercise's participations and storing the assessments); this parser is the
 * stateless input stage in front of it.
 *
 * @see AssessmentUploadService
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class AssessmentUploadArchiveParsingService {

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

    /**
     * Maximum number of participants a single manual-assessment upload — and therefore a {@link AssessmentUploadService#generateTemplateArchive generated template} — can carry. A
     * valid archive holds one {@code assessment-scores.csv} row and one {@code .txt} feedback entry per participant, so this value bounds both the CSV row count and, together with
     * the one reserved CSV entry, the archive entry count. It is shared with {@link AssessmentUploadService} so a downloaded template always fits within the importer's limits and
     * round-trips.
     */
    public static final int MAX_PARTICIPANT_COUNT = 10_000;

    /** Maximum number of entries processed from one archive, including ignored metadata entries. One entry on top of the per-participant text files is reserved for the CSV. */
    private static final int MAX_ARCHIVE_ENTRY_COUNT = MAX_PARTICIPANT_COUNT + 1;

    /** Maximum uncompressed size of one archive entry. */
    private static final long MAX_ENTRY_UNCOMPRESSED_SIZE = 10L * 1024 * 1024;

    /** Maximum aggregate uncompressed size of all entries in one archive. */
    private static final long MAX_ARCHIVE_UNCOMPRESSED_SIZE = 100L * 1024 * 1024;

    /**
     * Maximum number of data rows read from the CSV file: one per participant ({@link #MAX_PARTICIPANT_COUNT}). Bounds the in-memory record list and the size of the derived id
     * sets
     * and {@code IN} queries, so a CSV with millions of tiny rows cannot exhaust the heap or exceed database parameter limits.
     */
    private static final int MAX_CSV_ROW_COUNT = MAX_PARTICIPANT_COUNT;

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
    public ZipContents readZipContents(final MultipartFile zipFile) {
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
    public CsvParseResult parseCsv(final ZipContents contents) {
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
    public record ZipContents(List<byte[]> csvFiles, Map<String, String> textContentsByBaseName, Set<String> duplicateTextFileBaseNames) {

        /**
         * <b>Precondition:</b> all components are non-{@code null}.
         *
         * @throws IllegalArgumentException if a parameter is {@code null}
         */
        public ZipContents {
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
    public sealed interface CsvParseResult permits ParsedCsv, CsvParseError {
    }

    /**
     * The successfully parsed CSV content.
     * <p>
     * Invariant: {@code records} is non-empty and {@code pointsColumn} is non-{@code null}.
     *
     * @param records      the parsed data rows
     * @param pointsColumn the resolved header name of the {@code Overall points} column
     */
    public record ParsedCsv(List<CSVRecord> records, String pointsColumn) implements CsvParseResult {

        /**
         * <b>Preconditions:</b> {@code records} is non-{@code null} and non-empty, and {@code pointsColumn} is non-{@code null}.
         *
         * @throws IllegalArgumentException if a precondition is violated
         */
        public ParsedCsv {
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
    public record CsvParseError(AssessmentUploadErrorType error) implements CsvParseResult {

        /**
         * <b>Precondition:</b> {@code error} is non-{@code null}.
         *
         * @throws IllegalArgumentException if {@code error} is {@code null}
         */
        public CsvParseError {
            if (error == null) {
                throw new IllegalArgumentException("The CSV parse error must not be null");
            }
        }
    }
}
