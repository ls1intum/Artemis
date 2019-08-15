package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.repository.*;

@Service
public class FileUploadAssessmentService extends AssessmentService {

    private final FileUploadSubmissionRepository fileUploadSubmissionRepository;

    private final UserService userService;

    private final FileUploadSubmissionService fileUploadSubmissionService;

    public FileUploadAssessmentService(UserService userService, ComplaintResponseService complaintResponseService, ComplaintRepository complaintRepository,
            ResultRepository resultRepository, FileUploadSubmissionRepository fileUploadSubmissionRepository, StudentParticipationRepository studentParticipationRepository,
            ResultService resultService, AuthorizationCheckService authCheckService, FileUploadSubmissionService fileUploadSubmissionService) {
        super(complaintResponseService, complaintRepository, resultRepository, studentParticipationRepository, resultService, authCheckService);
        this.fileUploadSubmissionRepository = fileUploadSubmissionRepository;
        this.fileUploadSubmissionService = fileUploadSubmissionService;
        this.userService = userService;
    }

    /**
     * This function is used for submitting a manual assessment/result. It updates the completion date, sets the assessment type to MANUAL and sets the assessor attribute.
     * Furthermore, it saves the result in the database.
     *
     * @param result   the result the assessment belongs to
     * @param fileUploadExercise the exercise the assessment belongs to
     * @param submissionDate the date manual assessment was submitted
     * @return the ResponseEntity with result as body
     */
    @Transactional
    public Result submitAssessment(Result result, FileUploadExercise fileUploadExercise, ZonedDateTime submissionDate) {
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
            result = fileUploadSubmissionService.setNewResult(fileUploadSubmission);
        }
        // check the assessment due date if the user tries to override an existing submitted result
        if (result.getCompletionDate() != null) {
            checkAssessmentDueDate(fileUploadExercise);
        }
        checkGeneralFeedback(fileUploadAssessment);

        result.setHasComplaint(false);
        result.setExampleResult(fileUploadSubmission.isExampleSubmission());
        result.setAssessmentType(AssessmentType.MANUAL);
        User user = userService.getUser();
        result.setAssessor(user);
        result.updateAllFeedbackItems(fileUploadAssessment);
        // Note: this boolean flag is only used for programming exercises
        result.setHasFeedback(false);

        if (result.getSubmission() == null) {
            result.setSubmission(fileUploadSubmission);
            fileUploadSubmission.setResult(result);
            fileUploadSubmissionRepository.save(fileUploadSubmission);
        }
        // Note: This also saves the feedback objects in the database because of the 'cascade =
        // CascadeType.ALL' option.
        return resultRepository.save(result);
    }

}
