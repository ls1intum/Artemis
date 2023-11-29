package de.tum.in.www1.artemis.service.programming;

import java.util.Optional;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.lti.LtiNewResultService;
import de.tum.in.www1.artemis.service.exam.ExamDateService;

@Service
public class ProgrammingAssessmentService extends AssessmentService {

    public ProgrammingAssessmentService(ComplaintResponseService complaintResponseService, ComplaintRepository complaintRepository, FeedbackRepository feedbackRepository,
            ResultRepository resultRepository, StudentParticipationRepository studentParticipationRepository, ResultService resultService, SubmissionService submissionService,
            SubmissionRepository submissionRepository, ExamDateService examDateService, UserRepository userRepository, GradingCriterionRepository gradingCriterionRepository,
            Optional<LtiNewResultService> ltiNewResultService) {
        super(complaintResponseService, complaintRepository, feedbackRepository, resultRepository, studentParticipationRepository, resultService, submissionService,
                submissionRepository, examDateService, gradingCriterionRepository, userRepository, ltiNewResultService);
    }

    /**
     * This function is used for saving a manual assessment/result. It sets the assessment type to SEMI_AUTOMATIC and sets the assessor attribute.
     * Furthermore, it saves the result in the database.
     *
     * @param result   the new result of a programming exercise
     * @param assessor the user who created the assessment
     * @return result that was saved in the database
     */
    public Result saveManualAssessment(Result result, User assessor) {
        var participation = result.getParticipation();

        result.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        result.setAssessor(assessor);
        result.setCompletionDate(null);

        Result finalResult = resultService.storeFeedbackInResult(result, result.getFeedbacks(), true);

        finalResult.setParticipation(participation);
        return finalResult;
    }

}
