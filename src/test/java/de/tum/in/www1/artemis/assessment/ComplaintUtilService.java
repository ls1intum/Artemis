package de.tum.in.www1.artemis.assessment;

import java.util.ArrayList;

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

    /**
     * Crates and saves a complaint and response for the given result and user.
     *
     * @param userPrefix               The tutor's login prefix.
     * @param currentIndex             The current index of the loop this method is called in. Skips creation of complaints and responses if the current index is greater or
     *                                     equal to the {@code numberOfComplaints} or {@code numberComplaintResponses} respectively.
     * @param numberOfComplaints       The max number of complaints to generate.
     * @param numberComplaintResponses The max number of complaint responses to generate.
     * @param typeComplaint            Whether the complaint is a complaint (true) or a more feedback request (false).
     * @param result                   The result to generate the complaint for.
     * @param currentUser              The user that replies to the complaint.
     */
    public void generateComplaintAndResponses(String userPrefix, int currentIndex, int numberOfComplaints, int numberComplaintResponses, boolean typeComplaint, Result result,
            User currentUser) {
        result = resultRepo.save(result);
        if (numberOfComplaints >= currentIndex) {
            Complaint complaint = typeComplaint ? new Complaint().complaintType(ComplaintType.COMPLAINT) : new Complaint().complaintType(ComplaintType.MORE_FEEDBACK);
            complaint.setResult(result);
            complaint = complaintRepo.save(complaint);
            if (numberComplaintResponses >= currentIndex) {
                ComplaintResponse complaintResponse = createInitialEmptyResponse(typeComplaint ? userPrefix + "tutor5" : currentUser.getLogin(), complaint);
                complaintResponse.getComplaint().setAccepted(true);
                complaintResponse.setResponseText(typeComplaint ? "Accepted" : "SomeMoreFeedback");
                complaintResponseRepo.save(complaintResponse);
                complaint.setComplaintResponse(complaintResponse);
                complaintRepo.save(complaint);
            }
        }
    }

    /**
     * Creates and saves an empty complaint response for the given complaint and tutor.
     *
     * @param loginOfTutor The tutor's login.
     * @param complaint    The complaint to create the response for.
     * @return The saved complaint response.
     */
    public ComplaintResponse createInitialEmptyResponse(String loginOfTutor, Complaint complaint) {
        ComplaintResponse complaintResponse = new ComplaintResponse();
        complaintResponse.setComplaint(complaint);
        User tutor = userRepo.findOneByLogin(loginOfTutor).orElseThrow();
        complaintResponse.setReviewer(tutor);
        complaintResponse = complaintResponseRepo.saveAndFlush(complaintResponse);
        return complaintResponse;
    }

    /**
     * Creates and saves a given number of complaints for the given participation and student.
     *
     * @param studentLogin       The student's login.
     * @param participation      The participation to create the complaints for.
     * @param numberOfComplaints The number of complaints to create.
     * @param complaintType      The type of the complaints to create.
     */
    public void addComplaints(String studentLogin, Participation participation, int numberOfComplaints, ComplaintType complaintType) {
        for (int i = 0; i < numberOfComplaints; i++) {
            Result dummyResult = new Result().participation(participation);
            dummyResult = resultRepo.save(dummyResult);
            Complaint complaint = new Complaint().participant(userUtilService.getUserByLogin(studentLogin)).result(dummyResult).complaintType(complaintType);
            complaintRepo.save(complaint);
        }
    }

    /**
     * Creates and saves a complaint for the given submission and student.
     *
     * @param submission    The submission to create the complaint for.
     * @param userLogin     The student's login.
     * @param complaintType The type of the complaint to create.
     */
    public void addComplaintToSubmission(Submission submission, String userLogin, ComplaintType complaintType) {
        Result result = submission.getLatestResult();
        if (result != null) {
            result.hasComplaint(true);
            resultRepo.save(result);
        }
        Complaint complaint = new Complaint().participant(userUtilService.getUserByLogin(userLogin)).result(result).complaintType(complaintType);
        complaintRepo.save(complaint);
    }

    /**
     * Creates and saves a given number of complaints for the given team and participation.
     *
     * @param team               The team to create the complaints for.
     * @param participation      The participation to create the complaints for.
     * @param numberOfComplaints The number of complaints to create.
     * @param complaintType      The type of the complaints to create.
     */
    public void addTeamComplaints(Team team, Participation participation, int numberOfComplaints, ComplaintType complaintType) {
        for (int i = 0; i < numberOfComplaints; i++) {
            Result dummyResult = new Result().participation(participation);
            dummyResult = resultRepo.save(dummyResult);
            Complaint complaint = new Complaint().participant(team).result(dummyResult).complaintType(complaintType);
            complaintRepo.save(complaint);
        }
    }

    /**
     * Creates a complaint and a response for the passed text submission. The response is returned in a form of an assessment update.
     *
     * @param textResult result of the complaint.
     * @param tutorLogin login of the tutor responding to the complaint.
     * @return an assessment update with the complaint response.
     */
    public AssessmentUpdate createComplaintAndResponse(Result textResult, String tutorLogin) {
        Complaint complaint = new Complaint().result(textResult).complaintText("This is not fair");
        complaintRepo.save(complaint);
        complaint.getResult().setParticipation(null); // Break infinite reference chain

        ComplaintResponse complaintResponse = createInitialEmptyResponse(tutorLogin, complaint);
        complaintResponse.getComplaint().setAccepted(false);
        complaintResponse.setResponseText("rejected");
        return new AssessmentUpdate().feedbacks(new ArrayList<>()).complaintResponse(complaintResponse);
    }
}
