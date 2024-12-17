package de.tum.cit.aet.artemis.text.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Map;
import java.util.Objects;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.repository.TextSubmissionRepository;
import de.tum.cit.aet.artemis.text.service.TextExerciseImportService;
import de.tum.cit.aet.artemis.text.service.TextSubmissionService;

@Controller
@Profile(PROFILE_CORE)
public class TextSubmissionApi extends AbstractTextApi {

    private final TextSubmissionRepository textSubmissionRepository;

    private final TextExerciseImportService textExerciseImportService;

    private final TextSubmissionService textSubmissionService;

    public TextSubmissionApi(TextSubmissionRepository textSubmissionRepository, TextExerciseImportService textExerciseImportService, TextSubmissionService textSubmissionService) {
        this.textSubmissionRepository = textSubmissionRepository;
        this.textExerciseImportService = textExerciseImportService;
        this.textSubmissionService = textSubmissionService;
    }

    public TextSubmission importStudentSubmission(long submissionId, long exerciseId, Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
        TextSubmission textSubmission = textSubmissionRepository.findByIdWithEagerResultsAndFeedbackAndTextBlocksElseThrow(submissionId);
        checkGivenExerciseIdSameForSubmissionParticipation(exerciseId, textSubmission.getParticipation().getExercise().getId());

        // example submission does not need participation
        textSubmission.setParticipation(null);
        return textExerciseImportService.copySubmission(textSubmission, gradingInstructionCopyTracker);
    }

    public TextSubmission saveTextSubmission(TextSubmission textSubmission) {
        return textSubmissionRepository.save(textSubmission);
    }

    public TextSubmission handleTextSubmission(TextSubmission textSubmission, TextExercise exercise, User user) {
        var submission = textSubmissionService.handleTextSubmission(textSubmission, exercise, user);
        textSubmissionService.hideDetails(submission, user);
        return submission;
    }

    private void checkGivenExerciseIdSameForSubmissionParticipation(long originalExerciseId, long exerciseIdInSubmission) {
        if (!Objects.equals(originalExerciseId, exerciseIdInSubmission)) {
            throw new IllegalArgumentException("ExerciseId does not match with the exerciseId in submission participation");
        }
    }
}
