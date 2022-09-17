package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.UserRepository;

class ComplaintResponseServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ComplaintResponseService complaintResponseService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private ResultRepository resultRepository;

    private TextExercise textExercise;

    private TextExercise teamTextExercise;

    private User student1;

    private User student2;

    private User tutor1;

    private User tutor2;

    private User instructor;

    private Team team;

    @BeforeEach
    void initTestCase() throws Exception {
        this.database.addUsers(2, 2, 0, 1);
        Course course = this.database.createCourse();

        this.student1 = this.database.getUserByLogin("student1");
        this.student1.setGroups(Set.of(course.getStudentGroupName()));
        userRepository.save(this.student1);

        this.student2 = this.database.getUserByLogin("student2");
        this.student2.setGroups(Set.of(course.getStudentGroupName()));
        userRepository.save(this.student2);

        this.tutor1 = this.database.getUserByLogin("tutor1");
        this.tutor1.setGroups(Set.of(course.getTeachingAssistantGroupName()));
        userRepository.save(this.tutor1);

        this.tutor2 = this.database.getUserByLogin("tutor2");
        this.tutor2.setGroups(Set.of(course.getTeachingAssistantGroupName()));
        userRepository.save(this.tutor2);

        this.instructor = this.database.getUserByLogin("instructor1");
        this.instructor.setGroups(Set.of(course.getInstructorGroupName()));
        userRepository.save(this.instructor);

        this.textExercise = this.database.createIndividualTextExercise(course, null, null, null);
        this.teamTextExercise = this.database.createTeamTextExercise(course, null, null, null);

        this.team = this.database.createTeam(Set.of(this.student1), this.tutor1, this.teamTextExercise, "Team");
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    void testIsUserAuthorizedToRespondToComplaintCheckInput() {
        Complaint complaintWithoutResult = new Complaint();
        Complaint complaintWithResult = new Complaint();
        complaintWithResult.setResult(new Result());
        User user = new User();

        assertThatThrownBy(() -> complaintResponseService.isUserAuthorizedToRespondToComplaint(complaintWithResult, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> complaintResponseService.isUserAuthorizedToRespondToComplaint(null, user)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> complaintResponseService.isUserAuthorizedToRespondToComplaint(complaintWithoutResult, user)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testIsUserAuthorizedToRespondToComplaintForInstructor() {
        Result textExerciseResult = this.database.createParticipationSubmissionAndResult(textExercise.getId(), student1, 0d, 0d, 10, true);
        this.database.addComplaintToSubmission(textExerciseResult.getSubmission(), student1.getLogin(), ComplaintType.COMPLAINT);
        Complaint textExerciseComplaint = this.complaintRepository.findByResultSubmissionId(textExerciseResult.getSubmission().getId()).orElseThrow();

        assertThat(complaintResponseService.isUserAuthorizedToRespondToComplaint(textExerciseComplaint, instructor)).isTrue();
    }

    @Test
    @WithMockUser(username = "student2", roles = "INSTRUCTOR")
    void testIsUserAuthorizedToRespondToComplaintForStudent() {
        Result textExerciseResult = this.database.createParticipationSubmissionAndResult(textExercise.getId(), student1, 0d, 0d, 10, true);
        this.database.addComplaintToSubmission(textExerciseResult.getSubmission(), student1.getLogin(), ComplaintType.COMPLAINT);
        Complaint textExerciseComplaint = this.complaintRepository.findByResultSubmissionId(textExerciseResult.getSubmission().getId()).orElseThrow();

        assertThat(complaintResponseService.isUserAuthorizedToRespondToComplaint(textExerciseComplaint, student2)).isFalse();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testIsUserAuthorizedToRespondToComplaintForTeamOwner() {
        Result textExerciseResult = this.database.createParticipationSubmissionAndResult(teamTextExercise.getId(), team, 0d, 0d, 10, true);
        this.database.addComplaintToSubmission(textExerciseResult.getSubmission(), student1.getLogin(), ComplaintType.COMPLAINT);
        Complaint textExerciseComplaint = this.complaintRepository.findByResultSubmissionId(textExerciseResult.getSubmission().getId()).orElseThrow();

        assertThat(complaintResponseService.isUserAuthorizedToRespondToComplaint(textExerciseComplaint, tutor1)).isTrue();
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    void testIsUserAuthorizedToRespondToComplaintForNotTeamOwner() {
        Result textExerciseResult = this.database.createParticipationSubmissionAndResult(teamTextExercise.getId(), team, 0d, 0d, 10, true);
        this.database.addComplaintToSubmission(textExerciseResult.getSubmission(), student1.getLogin(), ComplaintType.COMPLAINT);
        Complaint textExerciseComplaint = this.complaintRepository.findByResultSubmissionId(textExerciseResult.getSubmission().getId()).orElseThrow();

        assertThat(complaintResponseService.isUserAuthorizedToRespondToComplaint(textExerciseComplaint, tutor2)).isFalse();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testIsUserAuthorizedToRespondToComplaintForNoComplaintType() {
        Result textExerciseResult = this.database.createParticipationSubmissionAndResult(textExercise.getId(), student1, 0d, 0d, 10, true);
        Submission submission = textExerciseResult.getSubmission();
        textExerciseResult.setAssessor(tutor1);
        resultRepository.save(textExerciseResult);
        this.database.addComplaintToSubmission(submission, student1.getLogin(), ComplaintType.COMPLAINT);
        Complaint textExerciseComplaint = this.complaintRepository.findByResultSubmissionId(submission.getId()).orElseThrow();
        textExerciseComplaint = this.complaintRepository.findByIdWithEagerAssessor(textExerciseComplaint.getId()).orElseThrow();

        assertThat(complaintResponseService.isUserAuthorizedToRespondToComplaint(textExerciseComplaint, tutor1)).isFalse();
        assertThat(complaintResponseService.isUserAuthorizedToRespondToComplaint(textExerciseComplaint, tutor2)).isTrue();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testIsUserAuthorizedToRespondToComplaintForComplaints() {
        Result textExerciseResult = this.database.createParticipationSubmissionAndResult(textExercise.getId(), student1, 0d, 0d, 10, true);
        Submission submission = textExerciseResult.getSubmission();
        textExerciseResult.setAssessor(tutor1);
        resultRepository.save(textExerciseResult);
        this.database.addComplaintToSubmission(submission, student1.getLogin(), ComplaintType.COMPLAINT);
        Complaint textExerciseComplaint = this.complaintRepository.findByResultSubmissionId(submission.getId()).orElseThrow();
        textExerciseComplaint = this.complaintRepository.findByIdWithEagerAssessor(textExerciseComplaint.getId()).orElseThrow();

        assertThat(complaintResponseService.isUserAuthorizedToRespondToComplaint(textExerciseComplaint, tutor1)).isFalse();
        assertThat(complaintResponseService.isUserAuthorizedToRespondToComplaint(textExerciseComplaint, tutor2)).isTrue();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testIsUserAuthorizedToRespondToComplaintForComplaintsWithAutomaticAssessment() {
        Result textExerciseResult = this.database.createParticipationSubmissionAndResult(textExercise.getId(), student1, 0d, 0d, 10, true);
        this.database.addComplaintToSubmission(textExerciseResult.getSubmission(), student1.getLogin(), ComplaintType.COMPLAINT);
        Complaint textExerciseComplaint = this.complaintRepository.findByResultSubmissionId(textExerciseResult.getSubmission().getId()).orElseThrow();

        assertThat(complaintResponseService.isUserAuthorizedToRespondToComplaint(textExerciseComplaint, tutor1)).isTrue();
        assertThat(complaintResponseService.isUserAuthorizedToRespondToComplaint(textExerciseComplaint, tutor2)).isTrue();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testIsUserAuthorizedToRespondToComplaintForFeedbackRequest() {
        Result textExerciseResult = this.database.createParticipationSubmissionAndResult(textExercise.getId(), student1, 0d, 0d, 10, true);
        Submission submission = textExerciseResult.getSubmission();
        textExerciseResult.setAssessor(tutor1);
        resultRepository.save(textExerciseResult);
        this.database.addComplaintToSubmission(submission, student1.getLogin(), ComplaintType.MORE_FEEDBACK);
        Complaint textExerciseComplaint = this.complaintRepository.findByResultSubmissionId(submission.getId()).orElseThrow();
        textExerciseComplaint = this.complaintRepository.findByIdWithEagerAssessor(textExerciseComplaint.getId()).orElseThrow();

        assertThat(complaintResponseService.isUserAuthorizedToRespondToComplaint(textExerciseComplaint, tutor1)).isTrue();
        assertThat(complaintResponseService.isUserAuthorizedToRespondToComplaint(textExerciseComplaint, tutor2)).isFalse();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testIsUserAuthorizedToRespondToComplaintForFeedbackRequestWithAutomaticAssessment() {
        Result textExerciseResult = this.database.createParticipationSubmissionAndResult(textExercise.getId(), student1, 0d, 0d, 10, true);
        this.database.addComplaintToSubmission(textExerciseResult.getSubmission(), student1.getLogin(), ComplaintType.MORE_FEEDBACK);
        Complaint textExerciseComplaint = this.complaintRepository.findByResultSubmissionId(textExerciseResult.getSubmission().getId()).orElseThrow();

        assertThat(complaintResponseService.isUserAuthorizedToRespondToComplaint(textExerciseComplaint, tutor1)).isTrue();
        assertThat(complaintResponseService.isUserAuthorizedToRespondToComplaint(textExerciseComplaint, tutor2)).isTrue();
    }
}
