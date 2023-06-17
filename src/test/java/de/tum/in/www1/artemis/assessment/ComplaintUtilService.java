package de.tum.in.www1.artemis.assessment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.user.UserUtilService;

/**
 * Service responsible for initializing the database with specific testdata related to complaints for use in integration tests.
 */
@Service
public class ComplaintUtilService {

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ResultRepository resultRepo;

    @Autowired
    private ComplaintRepository complaintRepo;

    @Autowired
    private ComplaintResponseRepository complaintResponseRepo;

    @Autowired
    private UserRepository userRepo;

    public void generateComplaintAndResponses(String userPrefix, int j, int numberOfComplaints, int numberComplaintResponses, boolean typeComplaint, Result result,
            User currentUser) {
        result = resultRepo.save(result);
        if (numberOfComplaints >= j) {
            Complaint complaint = typeComplaint ? new Complaint().complaintType(ComplaintType.COMPLAINT) : new Complaint().complaintType(ComplaintType.MORE_FEEDBACK);
            complaint.setResult(result);
            complaint = complaintRepo.save(complaint);
            if (numberComplaintResponses >= j) {
                ComplaintResponse complaintResponse = createInitialEmptyResponse(typeComplaint ? userPrefix + "tutor5" : currentUser.getLogin(), complaint);
                complaintResponse.getComplaint().setAccepted(true);
                complaintResponse.setResponseText(typeComplaint ? "Accepted" : "SomeMoreFeedback");
                complaintResponseRepo.save(complaintResponse);
                complaint.setComplaintResponse(complaintResponse);
                complaintRepo.save(complaint);
            }
        }
    }

    public ComplaintResponse createInitialEmptyResponse(String loginOfTutor, Complaint complaint) {
        ComplaintResponse complaintResponse = new ComplaintResponse();
        complaintResponse.setComplaint(complaint);
        User tutor = userRepo.findOneByLogin(loginOfTutor).get();
        complaintResponse.setReviewer(tutor);
        complaintResponse = complaintResponseRepo.saveAndFlush(complaintResponse);
        return complaintResponse;
    }

    public void addComplaints(String studentLogin, Participation participation, int numberOfComplaints, ComplaintType complaintType) {
        for (int i = 0; i < numberOfComplaints; i++) {
            Result dummyResult = new Result().participation(participation);
            dummyResult = resultRepo.save(dummyResult);
            Complaint complaint = new Complaint().participant(userUtilService.getUserByLogin(studentLogin)).result(dummyResult).complaintType(complaintType);
            complaintRepo.save(complaint);
        }
    }

    public void addComplaintToSubmission(Submission submission, String userLogin, ComplaintType type) {
        Result result = submission.getLatestResult();
        if (result != null) {
            result.hasComplaint(true);
            resultRepo.save(result);
        }
        Complaint complaint = new Complaint().participant(userUtilService.getUserByLogin(userLogin)).result(result).complaintType(type);
        complaintRepo.save(complaint);
    }

    public void addTeamComplaints(Team team, Participation participation, int numberOfComplaints, ComplaintType complaintType) {
        for (int i = 0; i < numberOfComplaints; i++) {
            Result dummyResult = new Result().participation(participation);
            dummyResult = resultRepo.save(dummyResult);
            Complaint complaint = new Complaint().participant(team).result(dummyResult).complaintType(complaintType);
            complaintRepo.save(complaint);
        }
    }
}
