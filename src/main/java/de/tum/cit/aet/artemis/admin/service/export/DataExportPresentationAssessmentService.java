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
import de.tum.cit.aet.artemis.assessment.dto.PresentationRubricRatingExportDTO;
import de.tum.cit.aet.artemis.assessment.repository.PresentationAssessmentInstanceRepository;
import de.tum.cit.aet.artemis.assessment.repository.PresentationRubricRatingRepository;

/**
 * Service for creating the presentation assessment part of the personal data export for GDPR compliance.
 * <p>
 * The export contains the presentations of the user across all courses with their result and the remark the instructors entered,
 * and for presentations graded by rubric the rating and comment of every criterion.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class DataExportPresentationAssessmentService {

    private static final String[] HEADER = { "course_title", "presentation_title", "presentation_date", "mode", "language", "location", "meeting_link", "grading_method",
            "max_points", "result_points", "passed", "rubric_score", "remark" };

    private static final String[] RATING_HEADER = { "course_title", "presentation_title", "presentation_date", "criterion_name", "weight", "scale", "level_value", "comment" };

    private final PresentationAssessmentInstanceRepository presentationAssessmentInstanceRepository;

    private final PresentationRubricRatingRepository presentationRubricRatingRepository;

    public DataExportPresentationAssessmentService(PresentationAssessmentInstanceRepository presentationAssessmentInstanceRepository,
            PresentationRubricRatingRepository presentationRubricRatingRepository) {
        this.presentationAssessmentInstanceRepository = presentationAssessmentInstanceRepository;
        this.presentationRubricRatingRepository = presentationRubricRatingRepository;
    }

    /**
     * Creates the CSV files containing all presentation assessment instances of the user and their rubric ratings.
     * A file is only created if the user has rows for it.
     *
     * @param userId           the ID of the user for which the data should be exported
     * @param workingDirectory the directory where the export files should be created
     * @throws IOException if a file cannot be created
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
                        row.gradingMethod(), row.maxPoints(), row.resultPoints(), row.passed(), row.rubricScore(), row.remark());
            }
        }
        createRubricRatingExport(userId, workingDirectory);
    }

    private void createRubricRatingExport(long userId, Path workingDirectory) throws IOException {
        List<PresentationRubricRatingExportDTO> ratings = presentationRubricRatingRepository.findExportRowsByStudentId(userId);
        if (ratings.isEmpty()) {
            return;
        }

        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(RATING_HEADER).get();
        try (final CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(workingDirectory.resolve("presentation_rubric_ratings" + CSV_FILE_EXTENSION)), csvFormat)) {
            for (var rating : ratings) {
                // Levels are stored from position 0, but shown and graded from value 1
                printer.printRecord(rating.courseTitle(), rating.presentationTitle(), rating.presentationDate(), rating.criterionName(), rating.weight(), rating.scale(),
                        rating.levelPosition() + 1, rating.comment());
            }
        }
    }
}
