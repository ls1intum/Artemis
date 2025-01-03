package de.tum.cit.aet.artemis.text.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.service.ArchivalReportEntry;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionExportOptionsDTO;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.repository.TextSubmissionRepository;
import de.tum.cit.aet.artemis.text.service.TextBlockService;
import de.tum.cit.aet.artemis.text.service.TextExerciseWithSubmissionsExportService;
import de.tum.cit.aet.artemis.text.service.TextSubmissionExportService;

@Controller
@Profile(PROFILE_CORE)
public class TextSubmissionExportApi extends AbstractTextApi {

    private final TextSubmissionExportService textSubmissionExportService;

    private final TextExerciseWithSubmissionsExportService textExerciseWithSubmissionsExportService;

    private final TextSubmissionRepository textSubmissionRepository;

    private final TextBlockService textBlockService;

    public TextSubmissionExportApi(TextSubmissionExportService textSubmissionExportService, TextExerciseWithSubmissionsExportService textExerciseWithSubmissionsExportService,
            TextSubmissionRepository textSubmissionRepository, TextBlockService textBlockService) {
        this.textSubmissionExportService = textSubmissionExportService;
        this.textExerciseWithSubmissionsExportService = textExerciseWithSubmissionsExportService;
        this.textSubmissionRepository = textSubmissionRepository;
        this.textBlockService = textBlockService;
    }

    public void saveSubmissionToFile(TextSubmission submission, String studentLogin, String submissionsFolderName) throws IOException {
        textSubmissionExportService.saveSubmissionToFile(submission, studentLogin, submissionsFolderName);
    }

    public Path exportTextExerciseWithSubmissions(TextExercise exercise, SubmissionExportOptionsDTO optionsDTO, Path exportDir, List<String> exportErrors,
            List<ArchivalReportEntry> reportEntries) {
        return textExerciseWithSubmissionsExportService.exportTextExerciseWithSubmissions(exercise, optionsDTO, exportDir, exportErrors, reportEntries);
    }

    /**
     * Prepares a text block for export as an example submission
     *
     * @param exampleSubmissionId the submission id to be exported
     */
    public void prepareTextBlockForExampleSubmission(long exampleSubmissionId) {
        Optional<TextSubmission> textSubmission = textSubmissionRepository.findWithEagerResultsAndFeedbackAndTextBlocksById(exampleSubmissionId);
        if (textSubmission.isPresent() && textSubmission.get().getLatestResult() == null
                && (textSubmission.get().getBlocks() == null || textSubmission.get().getBlocks().isEmpty())) {
            TextSubmission submission = textSubmission.get();
            textBlockService.computeTextBlocksForSubmissionBasedOnSyntax(submission);
            textBlockService.saveAll(submission.getBlocks());
        }
    }

    public Optional<TextSubmission> getSubmissionForExampleSubmission(long exampleSubmissionId) {
        return textSubmissionRepository.findWithEagerResultsAndFeedbackAndTextBlocksById(exampleSubmissionId);
    }
}
