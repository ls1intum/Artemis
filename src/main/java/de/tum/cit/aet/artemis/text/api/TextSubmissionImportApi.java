package de.tum.cit.aet.artemis.text.api;

import java.util.Map;
import java.util.Objects;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.assessment.domain.ExampleParticipation;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.text.config.TextEnabled;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.repository.TextSubmissionRepository;
import de.tum.cit.aet.artemis.text.service.TextExerciseImportService;

@Conditional(TextEnabled.class)
@Controller
@Lazy
public class TextSubmissionImportApi extends AbstractTextApi {

    private final TextSubmissionRepository textSubmissionRepository;

    private final TextExerciseImportService textExerciseImportService;

    public TextSubmissionImportApi(TextSubmissionRepository textSubmissionRepository, TextExerciseImportService textExerciseImportService) {
        this.textSubmissionRepository = textSubmissionRepository;
        this.textExerciseImportService = textExerciseImportService;
    }

    /**
     * Imports a student submission as an example participation.
     *
     * @param submissionId                  of the submission to be imported
     * @param exerciseId                    of the exercise to import the submission into
     * @param gradingInstructionCopyTracker mapping of the gradingInstructionID to the gradingInstruction
     * @param targetParticipation           the example participation to associate with the new submission
     * @return the imported text submission
     */
    public TextSubmission importStudentSubmission(long submissionId, long exerciseId, Map<Long, GradingInstruction> gradingInstructionCopyTracker,
            ExampleParticipation targetParticipation) {
        TextSubmission textSubmission = textSubmissionRepository.findByIdWithEagerResultsAndFeedbackAndTextBlocksElseThrow(submissionId);
        checkGivenExerciseIdSameForSubmissionParticipation(exerciseId, textSubmission.getParticipation().getExercise().getId());
        return textExerciseImportService.copySubmission(textSubmission, gradingInstructionCopyTracker, targetParticipation);
    }

    private void checkGivenExerciseIdSameForSubmissionParticipation(long originalExerciseId, long exerciseIdInSubmission) {
        if (!Objects.equals(originalExerciseId, exerciseIdInSubmission)) {
            throw new BadRequestAlertException("ExerciseId does not match with the exerciseId in submission participation", "exampleSubmission", "idNotMatched");
        }
    }
}
