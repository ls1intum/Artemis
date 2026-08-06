package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;

import de.tum.cit.aet.artemis.assessment.dto.AssessmentUploadParticipationDTO;
import de.tum.cit.aet.artemis.assessment.repository.AssessmentUploadParticipationRepository;
import de.tum.cit.aet.artemis.assessment.service.AssessmentUploadArchiveParsingService;
import de.tum.cit.aet.artemis.assessment.service.AssessmentUploadArchiveParsingService.ParsedCsv;
import de.tum.cit.aet.artemis.assessment.service.AssessmentUploadArchiveParsingService.ZipContents;
import de.tum.cit.aet.artemis.assessment.service.AssessmentUploadResultService;
import de.tum.cit.aet.artemis.assessment.service.AssessmentUploadService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.SubmissionService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Pure unit tests for {@link AssessmentUploadService#generateTemplateArchive} that need neither a database nor a Spring context: the participation repository is mocked so template
 * generation can be exercised with hand-picked participants (a comma-containing identifier and boundary-sized participant lists) without persisting anything.
 */
class AssessmentUploadTemplateGenerationTest {

    private static final long EXERCISE_ID = 1L;

    private static final String CSV_FILE_NAME = "assessment-scores.csv";

    @Test
    void shouldQuoteTemplateFieldsContainingTheCsvDelimiter() throws IOException {
        // TeamResource validates a sanitized copy of the short name but persists the raw value (see TeamResource#createTeam), so a team short name — surfaced as the participant
        // identifier by findAllForAssessmentUploadTemplate — can contain the CSV delimiter. The template must quote such a field so the generated archive round-trips through the
        // importer instead of shifting into extra columns.
        final long participationId = 42L;
        final String teamShortName = "team,1";
        final AssessmentUploadService service = templateServiceReturning(List.of(new AssessmentUploadParticipationDTO(participationId, teamShortName)));

        final Map<String, String> entries = readZipEntries(service.generateTemplateArchive(exerciseWithId(EXERCISE_ID)));

        // The comma-containing identifier and login must survive as single CSV fields; the feedback file is named after the path-safe participation id.
        final String expectedIdentifier = participationId + "-" + teamShortName;
        assertThat(entries).containsKey(participationId + ".txt");
        final CSVRecord firstRow = parseFirstDataRow(entries.get(CSV_FILE_NAME));
        assertThat(firstRow.get("Identifier")).isEqualTo(expectedIdentifier);
        assertThat(firstRow.get("Login")).isEqualTo(teamShortName);
    }

    @Test
    void shouldNameTemplateFeedbackFilesPathSafelyForSlashIdentifiers() throws IOException {
        // TeamResource persists raw short names containing '/', so a team such as "team/1" would otherwise produce a nested entry (42-team/1.txt) whose prefix the importer strips,
        // leaving the wrong base name. The feedback file must be the flat, path-safe participation id so the generated template round-trips.
        final long participationId = 42L;
        final AssessmentUploadService service = templateServiceReturning(List.of(new AssessmentUploadParticipationDTO(participationId, "team/1")));

        final Map<String, String> entries = readZipEntries(service.generateTemplateArchive(exerciseWithId(EXERCISE_ID)));

        // The feedback entry is the flat participation id with no nested path, while the CSV still carries the full identifier for row resolution.
        assertThat(entries).containsKey(participationId + ".txt");
        assertThat(entries.keySet()).noneMatch(name -> name.contains("/"));
        assertThat(parseFirstDataRow(entries.get(CSV_FILE_NAME)).get("Identifier")).isEqualTo(participationId + "-team/1");
    }

    @Test
    void shouldRejectTemplateGenerationAboveTheParticipantLimit() {
        final AssessmentUploadService service = templateServiceReturning(participations(AssessmentUploadArchiveParsingService.MAX_PARTICIPANT_COUNT + 1));

        // A template larger than the shared participant budget could never be re-uploaded, so generation must fail up front instead of emitting an un-importable archive.
        assertThatThrownBy(() -> service.generateTemplateArchive(exerciseWithId(EXERCISE_ID))).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("more participants");
    }

    @Test
    void shouldGenerateAMaximumSizedTemplateThatStaysWithinTheImporterBudget() throws IOException {
        // Regression test for the aligned budgets: a template for the maximum supported number of participants must pass the importer's own archive-entry and CSV-row limits,
        // otherwise the download could never be re-uploaded.
        final int maximum = AssessmentUploadArchiveParsingService.MAX_PARTICIPANT_COUNT;
        final AssessmentUploadService service = templateServiceReturning(participations(maximum));

        final byte[] template = service.generateTemplateArchive(exerciseWithId(EXERCISE_ID));

        // One CSV entry plus one feedback file per participant.
        assertThat(countZipEntries(template)).isEqualTo(maximum + 1);
        // The stateless parser enforces the archive-entry and CSV-row limits by throwing; a maximum-sized template must pass both without a limit exception and expose every row.
        final AssessmentUploadArchiveParsingService parser = new AssessmentUploadArchiveParsingService();
        final ZipContents contents = parser.readZipContents(new MockMultipartFile("file", "template.zip", "application/zip", template));
        assertThat(parser.parseCsv(contents)).isInstanceOfSatisfying(ParsedCsv.class, parsed -> assertThat(parsed.records()).hasSize(maximum));
    }

    /** Builds an {@link AssessmentUploadService} with mocked collaborators whose template query returns the given participations. */
    private static AssessmentUploadService templateServiceReturning(final List<AssessmentUploadParticipationDTO> participations) {
        final AssessmentUploadParticipationRepository participationRepository = mock(AssessmentUploadParticipationRepository.class);
        when(participationRepository.findAllForAssessmentUploadTemplate(EXERCISE_ID)).thenReturn(participations);
        return new AssessmentUploadService(mock(AssessmentUploadArchiveParsingService.class), participationRepository, mock(SubmissionRepository.class),
                mock(AssessmentUploadResultService.class), mock(SubmissionService.class), mock(PlatformTransactionManager.class));
    }

    private static ProgrammingExercise exerciseWithId(final long id) {
        final ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(id);
        return exercise;
    }

    /** Creates {@code count} distinct participations with strictly positive ids and non-blank identifiers, as required by {@link AssessmentUploadParticipationDTO}. */
    private static List<AssessmentUploadParticipationDTO> participations(final int count) {
        final List<AssessmentUploadParticipationDTO> participations = new ArrayList<>(count);
        for (int index = 1; index <= count; index++) {
            participations.add(new AssessmentUploadParticipationDTO(index, "student" + index));
        }
        return participations;
    }

    private static CSVRecord parseFirstDataRow(final String csv) throws IOException {
        final CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get();
        try (CSVParser parser = CSVParser.parse(new StringReader(csv), format)) {
            return parser.getRecords().getFirst();
        }
    }

    private static int countZipEntries(final byte[] zipBytes) throws IOException {
        int count = 0;
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            while (zipInputStream.getNextEntry() != null) {
                count++;
            }
        }
        return count;
    }

    private static Map<String, String> readZipEntries(final byte[] zipBytes) throws IOException {
        final Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
