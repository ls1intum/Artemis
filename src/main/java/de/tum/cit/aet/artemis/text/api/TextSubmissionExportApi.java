package de.tum.cit.aet.artemis.text.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.service.ArchivalReportEntry;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionExportOptionsDTO;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.service.TextExerciseWithSubmissionsExportService;
import de.tum.cit.aet.artemis.text.service.TextSubmissionExportService;

@Controller
@Profile(PROFILE_CORE)
public class TextSubmissionExportApi extends AbstractTextApi {

    private final TextSubmissionExportService textSubmissionExportService;

    private final TextExerciseWithSubmissionsExportService textExerciseWithSubmissionsExportService;

    public TextSubmissionExportApi(TextSubmissionExportService textSubmissionExportService, TextExerciseWithSubmissionsExportService textExerciseWithSubmissionsExportService) {
        this.textSubmissionExportService = textSubmissionExportService;
        this.textExerciseWithSubmissionsExportService = textExerciseWithSubmissionsExportService;
    }

    public void saveSubmissionToFile(TextSubmission submission, String studentLogin, String submissionsFolderName) throws IOException {
        textSubmissionExportService.saveSubmissionToFile(submission, studentLogin, submissionsFolderName);
    }

    public Path exportTextExerciseWithSubmissions(TextExercise exercise, SubmissionExportOptionsDTO optionsDTO, Path exportDir, List<String> exportErrors,
            List<ArchivalReportEntry> reportEntries) {
        return textExerciseWithSubmissionsExportService.exportTextExerciseWithSubmissions(exercise, optionsDTO, exportDir, exportErrors, reportEntries);
    }
}
