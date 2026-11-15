package de.tum.cit.aet.artemis.admin.service.export;

import static de.tum.cit.aet.artemis.admin.service.export.DataExportExerciseCreationService.CSV_FILE_EXTENSION;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentExportDTO;
import de.tum.cit.aet.artemis.assessment.repository.PresentationAssessmentInstanceRepository;

/**
 * Service for creating the presentation assessment part of the personal data export for GDPR compliance.
 * <p>
 * The export contains the presentations of the user across all courses, including the points and the remark the instructors entered.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class DataExportPresentationAssessmentService {

    private static final String[] HEADER = { "course_title", "presentation_title", "presentation_date", "mode", "language", "location", "meeting_link", "max_points",
            "result_points", "remark" };

    private final PresentationAssessmentInstanceRepository presentationAssessmentInstanceRepository;

    public DataExportPresentationAssessmentService(PresentationAssessmentInstanceRepository presentationAssessmentInstanceRepository) {
        this.presentationAssessmentInstanceRepository = presentationAssessmentInstanceRepository;
    }

    /**
     * Creates the CSV file containing all presentation assessment instances of the user.
     * No file is created if the user has no presentation assessment instances.
     *
     * @param userId           the ID of the user for which the data should be exported
     * @param workingDirectory the directory where the export file should be created
     * @throws IOException if the file cannot be created
     */
    public void createPresentationAssessmentExport(long userId, Path workingDirectory) throws IOException {
        List<PresentationAssessmentExportDTO> rows = presentationAssessmentInstanceRepository.findExportRowsByStudentId(userId);
        if (rows.isEmpty()) {
            return;
        }

        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(HEADER).get();
        try (final CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(workingDirectory.resolve("presentation_assessments" + CSV_FILE_EXTENSION)), csvFormat)) {
            for (var row : rows) {
                printer.printRecord(row.courseTitle(), row.presentationTitle(), row.presentationDate(), row.mode(), row.language(), row.location(), row.meetingLink(),
                        row.maxPoints(), row.resultPoints(), row.remark());
            }
        }
    }
}
