package de.tum.cit.aet.artemis.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.assessment.util.ComplaintUtilService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.team.TeamUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class ComplaintResponseServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "complaintresponseservice";

    @Autowired
    private ComplaintResponseService complaintResponseService;

    @Autowired
    private UserTestRepository userRepository;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private ResultTestRepository resultRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ComplaintUtilService complaintUtilService;

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
        userUtilService.addUsers(TEST_PREFIX, 2, 2, 0, 1);
        Course course = courseUtilService.createCourse();

        this.student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        this.student1.setGroups(Set.of(course.getStudentGroupName()));
        userRepository.save(this.student1);

        this.student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        this.student2.setGroups(Set.of(course.getStudentGroupName()));
        userRepository.save(this.student2);

        this.tutor1 = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        this.tutor1.setGroups(Set.of(course.getTeachingAssistantGroupName()));
        userRepository.save(this.tutor1);

        this.tutor2 = userUtilService.getUserByLogin(TEST_PREFIX + "tutor2");
        this.tutor2.setGroups(Set.of(course.getTeachingAssistantGroupName()));
        userRepository.save(this.tutor2);

        this.instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        this.instructor.setGroups(Set.of(course.getInstructorGroupName()));
        userRepository.save(this.instructor);

        this.textExercise = this.textExerciseUtilService.createIndividualTextExercise(course, null, null, null);
        this.teamTextExercise = this.textExerciseUtilService.createTeamTextExercise(course, null, null, null);

        this.team = this.teamUtilService.createTeam(Set.of(this.student1), this.tutor1, this.teamTextExercise, TEST_PREFIX + "Team");
    }

    @Test
    void testIsUserAuthorizedToRespondToComplaintCheckInput() {
        Complaint complaintWithoutResult = new Complaint();
        Complaint complaintWithResult = new Complaint();
        complaintWithResult.setResult(new Result());
        User user = new User();

        assertThatIllegalArgumentException().isThrownBy(() -> complaintResponseService.isUserAuthorizedToRespondToComplaint(complaintWithResult, null));
        assertThatIllegalArgumentException().isThrownBy(() -> complaintResponseService.isUserAuthorizedToRespondToComplaint(null, user));
        assertThatIllegalArgumentException().isThrownBy(() -> complaintResponseService.isUserAuthorizedToRespondToComplaint(complaintWithoutResult, user));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testIsUserAuthorizedToRespondToComplaintForInstructor() {
        Result textExerciseResult = this.participationUtilService.createParticipationSubmissionAndResult(textExercise.getId(), student1, 0d, 0d, 10, true);
        this.complaintUtilService.addComplaintToSubmission(textExerciseResult.getSubmission(), student1.getLogin(), ComplaintType.COMPLAINT);
        Complaint textExerciseComplaint = this.complaintRepository.findByResultSubmissionId(textExerciseResult.getSubmission().getId()).orElseThrow();

        assertThat(complaintResponseService.isUserAuthorizedToRespondToComplaint(textExerciseComplaint, instructor)).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "INSTRUCTOR")
    void testIsUserAuthorizedToRespondToComplaintForStudent() {
        Result textExerciseResult = this.participationUtilService.createParticipationSubmissionAndResult(textExercise.getId(), student1, 0d, 0d, 10, true);
        this.complaintUtilService.addComplaintToSubmission(textExerciseResult.getSubmission(), student1.getLogin(), ComplaintType.COMPLAINT);
        Complaint textExerciseComplaint = this.complaintRepository.findByResultSubmissionId(textExerciseResult.getSubmission().getId()).orElseThrow();

        assertThat(complaintResponseService.isUserAuthorizedToRespondToComplaint(textExerciseComplaint, student2)).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testIsUserAuthorizedToRespondToComplaintForTeamOwner() {
        Result textExerciseResult = this.participationUtilService.createParticipationSubmissionAndResult(teamTextExercise.getId(), team, 0d, 0d, 10, true);
        this.complaintUtilService.addComplaintToSubmission(textExerciseResult.getSubmission(), student1.getLogin(), ComplaintType.COMPLAINT);
        Complaint textExerciseComplaint = this.complaintRepository.findByResultSubmissionId(textExerciseResult.getSubmission().getId()).orElseThrow();

        assertThat(complaintResponseService.isUserAuthorizedToRespondToComplaint(textExerciseComplaint, tutor1)).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void testIsUserAuthorizedToRespondToComplaintForNotTeamOwner() {
        Result textExerciseResult = this.participationUtilService.createParticipationSubmissionAndResult(teamTextExercise.getId(), team, 0d, 0d, 10, true);
        this.complaintUtilService.addComplaintToSubmission(textExerciseResult.getSubmission(), student1.getLogin(), ComplaintType.COMPLAINT);
        Complaint textExerciseComplaint = this.complaintRepository.findByResultSubmissionId(textExerciseResult.getSubmission().getId()).orElseThrow();

        assertThat(complaintResponseService.isUserAuthorizedToRespondToComplaint(textExerciseComplaint, tutor2)).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testIsUserAuthorizedToRespondToComplaintForNoComplaintType() {
        Result textExerciseResult = this.participationUtilService.createParticipationSubmissionAndResult(textExercise.getId(), student1, 0d, 0d, 10, true);
        Submission submission = textExerciseResult.getSubmission();
        textExerciseResult.setAssessor(tutor1);
        resultRepository.save(textExerciseResult);
        this.complaintUtilService.addComplaintToSubmission(submission, student1.getLogin(), ComplaintType.COMPLAINT);
        Complaint textExerciseComplaint = this.complaintRepository.findByResultSubmissionId(submission.getId()).orElseThrow();
        textExerciseComplaint = this.complaintRepository.findByIdWithEagerAssessor(textExerciseComplaint.getId()).orElseThrow();

        assertThat(complaintResponseService.isUserAuthorizedToRespondToComplaint(textExerciseComplaint, tutor1)).isFalse();
        assertThat(complaintResponseService.isUserAuthorizedToRespondToComplaint(textExerciseComplaint, tutor2)).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testIsUserAuthorizedToRespondToComplaintForComplaints() {
        Result textExerciseResult = this.participationUtilService.createParticipationSubmissionAndResult(textExercise.getId(), student1, 0d, 0d, 10, true);
        Submission submission = textExerciseResult.getSubmission();
        textExerciseResult.setAssessor(tutor1);
        resultRepository.save(textExerciseResult);
        this.complaintUtilService.addComplaintToSubmission(submission, student1.getLogin(), ComplaintType.COMPLAINT);
        Complaint textExerciseComplaint = this.complaintRepository.findByResultSubmissionId(submission.getId()).orElseThrow();
        textExerciseComplaint = this.complaintRepository.findByIdWithEagerAssessor(textExerciseComplaint.getId()).orElseThrow();

        assertThat(complaintResponseService.isUserAuthorizedToRespondToComplaint(textExerciseComplaint, tutor1)).isFalse();
        assertThat(complaintResponseService.isUserAuthorizedToRespondToComplaint(textExerciseComplaint, tutor2)).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testIsUserAuthorizedToRespondToComplaintForComplaintsWithAutomaticAssessment() {
        Result textExerciseResult = this.participationUtilService.createParticipationSubmissionAndResult(textExercise.getId(), student1, 0d, 0d, 10, true);
        this.complaintUtilService.addComplaintToSubmission(textExerciseResult.getSubmission(), student1.getLogin(), ComplaintType.COMPLAINT);
        Complaint textExerciseComplaint = this.complaintRepository.findByResultSubmissionId(textExerciseResult.getSubmission().getId()).orElseThrow();

        assertThat(complaintResponseService.isUserAuthorizedToRespondToComplaint(textExerciseComplaint, tutor1)).isTrue();
        assertThat(complaintResponseService.isUserAuthorizedToRespondToComplaint(textExerciseComplaint, tutor2)).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testIsUserAuthorizedToRespondToComplaintForFeedbackRequest() {
        Result textExerciseResult = this.participationUtilService.createParticipationSubmissionAndResult(textExercise.getId(), student1, 0d, 0d, 10, true);
        Submission submission = textExerciseResult.getSubmission();
        textExerciseResult.setAssessor(tutor1);
        resultRepository.save(textExerciseResult);
        this.complaintUtilService.addComplaintToSubmission(submission, student1.getLogin(), ComplaintType.MORE_FEEDBACK);
        Complaint textExerciseComplaint = this.complaintRepository.findByResultSubmissionId(submission.getId()).orElseThrow();
        textExerciseComplaint = this.complaintRepository.findByIdWithEagerAssessor(textExerciseComplaint.getId()).orElseThrow();

        assertThat(complaintResponseService.isUserAuthorizedToRespondToComplaint(textExerciseComplaint, tutor1)).isTrue();
        assertThat(complaintResponseService.isUserAuthorizedToRespondToComplaint(textExerciseComplaint, tutor2)).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testIsUserAuthorizedToRespondToComplaintForFeedbackRequestWithAutomaticAssessment() {
        Result textExerciseResult = this.participationUtilService.createParticipationSubmissionAndResult(textExercise.getId(), student1, 0d, 0d, 10, true);
        this.complaintUtilService.addComplaintToSubmission(textExerciseResult.getSubmission(), student1.getLogin(), ComplaintType.MORE_FEEDBACK);
        Complaint textExerciseComplaint = this.complaintRepository.findByResultSubmissionId(textExerciseResult.getSubmission().getId()).orElseThrow();

        assertThat(complaintResponseService.isUserAuthorizedToRespondToComplaint(textExerciseComplaint, tutor1)).isTrue();
        assertThat(complaintResponseService.isUserAuthorizedToRespondToComplaint(textExerciseComplaint, tutor2)).isTrue();
    }
}
