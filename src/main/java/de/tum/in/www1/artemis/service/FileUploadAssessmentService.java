package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class FileUploadAssessmentService extends AssessmentService {

    private final FileUploadSubmissionRepository fileUploadSubmissionRepository;

    private final FileUploadSubmissionService fileUploadSubmissionService;

    public FileUploadAssessmentService(UserService userService, ComplaintResponseService complaintResponseService, ComplaintRepository complaintRepository,
            ResultRepository resultRepository, FileUploadSubmissionRepository fileUploadSubmissionRepository, StudentParticipationRepository studentParticipationRepository,
            ResultService resultService, AuthorizationCheckService authCheckService, FileUploadSubmissionService fileUploadSubmissionService) {
        super(complaintResponseService, complaintRepository, resultRepository, studentParticipationRepository, resultService, authCheckService, userService);
        this.fileUploadSubmissionRepository = fileUploadSubmissionRepository;
        this.fileUploadSubmissionService = fileUploadSubmissionService;
    }

    /**
     * This function is used for submitting a manual assessment/result. It updates the completion date, sets the assessment type to MANUAL and sets the assessor attribute.
     * Furthermore, it saves the result in the database.
     *
     * @param resultId   the result the assessment belongs to
     * @param fileUploadExercise the exercise the assessment belongs to
     * @param submissionDate the date manual assessment was submitted
     * @return the ResponseEntity with result as body
     */
    @Transactional
    public Result submitAssessment(long resultId, FileUploadExercise fileUploadExercise, ZonedDateTime submissionDate) {
        Result result = resultRepository.findWithEagerSubmissionAndFeedbackAndAssessorById(resultId)
                .orElseThrow(() -> new EntityNotFoundException("No result for the given resultId could be found"));
        result.setRatedIfNotExceeded(fileUploadExercise.getDueDate(), submissionDate);
        result.setCompletionDate(ZonedDateTime.now());
        result.evaluateFeedback(fileUploadExercise.getMaxScore());
        resultRepository.save(result);
        return result;
    }

    /**
     * This function is used for saving a manual assessment/result. It sets the assessment type to MANUAL and sets the assessor attribute. Furthermore, it saves the result in the
     * database.
     *
     * @param fileUploadSubmission the file upload submission to which the feedback belongs to
     * @param fileUploadAssessment the assessment as a feedback list that should be added to the result of the corresponding submission
     * @param fileUploadExercise the file upload exercise for which assessment due date is checked
     * @return result that was saved in the database
     */
    @Transactional
    public Result saveAssessment(FileUploadSubmission fileUploadSubmission, List<Feedback> fileUploadAssessment, FileUploadExercise fileUploadExercise) {
        Result result = fileUploadSubmission.getResult();
        if (result == null) {
            result = fileUploadSubmissionService.setNewResult(fileUploadSubmission, fileUploadSubmissionRepository);
        }
        final long generalFeedbackCount = fileUploadAssessment.stream().filter(feedback -> feedback.getCredits() == 0).count();
        if (generalFeedbackCount > 1) {
            throw new BadRequestAlertException("There cannot be more than one general Feedback per Assessment", "assessment", "moreThanOneGeneralFeedback");
        }
        return saveAssessment(result, fileUploadAssessment, fileUploadSubmission, fileUploadExercise, fileUploadSubmissionRepository);
    }

}
