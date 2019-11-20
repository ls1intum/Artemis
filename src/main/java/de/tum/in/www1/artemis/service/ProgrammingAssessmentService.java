package de.tum.in.www1.artemis.service;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;

@Service
public class ProgrammingAssessmentService extends AssessmentService {

    public ProgrammingAssessmentService(ComplaintResponseService complaintResponseService, ComplaintRepository complaintRepository, ResultRepository resultRepository,
            StudentParticipationRepository studentParticipationRepository, ResultService resultService, AuthorizationCheckService authCheckService) {
        super(complaintResponseService, complaintRepository, resultRepository, studentParticipationRepository, resultService, authCheckService);
    }

    /**
     * Handles an assessment update after a complaint. It first saves the corresponding complaint response and then updates the Result that was complaint about. Note, that it
     * updates the score and the feedback of the original Result, but NOT the assessor. The user that is responsible for the update can be found in the 'reviewer' field of the
     * complaint.
     *
     * @param originalResult   the original assessment that was complained about
     * @param exercise programming exercise
     * @param assessmentUpdate the assessment update
     * @return the updated Result
     */
    public Result updateAssessmentAfterComplaint(Result originalResult, Exercise exercise, ProgrammingAssessmentUpdate assessmentUpdate) {
        super.updateAssessmentAfterComplaint(originalResult, exercise, assessmentUpdate);
        originalResult.setResultString(assessmentUpdate.getResultString());
        originalResult.setScore(assessmentUpdate.getScore());
        return resultRepository.save(originalResult);
    }
}
