package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.scores.TutorScore;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.TutorScoreService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class TutorScoreIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    ResultRepository resultRepo;

    @Autowired
    TutorScoreRepository tutorScoresRepo;

    @Autowired
    StudentParticipationRepository studentParticipationRepo;

    @Autowired
    UserRepository userRepo;

    @Autowired
    ComplaintRepository complaintRepo;

    @Autowired
    ComplaintResponseRepository complaintResponseRepo;

    @Autowired
    TutorScoreService tutorScoreService;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    private TutorScore tutorScore;

    private User user;

    private Course course;

    private Exercise exercise;

    private StudentParticipation studentParticipation;

    private Result result;

    private Complaint complaint;

    private Complaint feedbackRequest;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(4, 3, 2);

        // course1
        course = database.addCourseWithOneFinishedTextExercise();
        course.setStudentGroupName("tumuser");
        course.setTeachingAssistantGroupName("tutor");
        course.setInstructorGroupName("instructor");
        courseRepo.save(course);
        // exercise1
        exercise = course.getExercises().stream().findFirst().get();
        exercise.setMaxScore(5.0);
        exerciseRepo.save(exercise);

        // score for tutor1 in exercise1 in course1
        user = userRepo.findAllInGroup("tumuser").get(0);
        studentParticipation = new StudentParticipation().exercise(exercise);
        studentParticipation.setParticipant(user);
        studentParticipation.setInitializationDate(ZonedDateTime.now());
        studentParticipationRepo.save(studentParticipation);
        user = userRepo.findAllInGroup("tutor").get(0);
        result = database.addResultToParticipation(studentParticipation, null);
        result.setParticipation(studentParticipation);
        result.setRated(true);
        result.setAssessor(user);
        resultRepo.save(result);

        // score for tutor2 in exercise1 in course1
        user = userRepo.findAllInGroup("tumuser").get(1);
        studentParticipation = new StudentParticipation().exercise(exercise);
        studentParticipation.setParticipant(user);
        studentParticipation.setInitializationDate(ZonedDateTime.now());
        studentParticipationRepo.save(studentParticipation);
        user = userRepo.findAllInGroup("tutor").get(1);
        result = database.addResultToParticipation(studentParticipation, null);
        result.setParticipation(studentParticipation);
        result.setRated(true);
        result.setAssessor(user);
        resultRepo.save(result);

        // course2
        course = database.addCourseWithOneFinishedTextExercise();
        course.setStudentGroupName("tumuser");
        course.setTeachingAssistantGroupName("tutor");
        course.setInstructorGroupName("instructor");
        courseRepo.save(course);
        // exercise2
        exercise = course.getExercises().stream().findFirst().get();
        exercise.setMaxScore(8.0);
        exerciseRepo.save(exercise);

        // score for tutor1 in exercise2 in course2
        user = userRepo.findAllInGroup("tumuser").get(0);
        studentParticipation = new StudentParticipation().exercise(exercise);
        studentParticipation.setParticipant(user);
        studentParticipation.setInitializationDate(ZonedDateTime.now());
        studentParticipationRepo.save(studentParticipation);
        user = userRepo.findAllInGroup("tutor").get(0);
        result = database.addResultToParticipation(studentParticipation, null);
        result.setParticipation(studentParticipation);
        result.setRated(true);
        result.setAssessor(user);
        resultRepo.save(result);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void tutorScoresForExerciseTest() throws Exception {
        user = userRepo.findAllInGroup("tutor").get(0);

        List responseExerciseOne = request.get("/api/tutor-scores/exercise/" + exerciseRepo.findAll().get(0).getId(), HttpStatus.OK, List.class);
        assertThat(responseExerciseOne.isEmpty()).as("response is not empty").isFalse();
        assertThat(responseExerciseOne.size()).as("response has length 2").isEqualTo(2);
        List responseExerciseTwo = request.get("/api/tutor-scores/exercise/" + exerciseRepo.findAll().get(1).getId(), HttpStatus.OK, List.class);
        assertThat(responseExerciseTwo.isEmpty()).as("response is not empty").isFalse();
        assertThat(responseExerciseTwo.size()).as("response has length 1").isEqualTo(1);

        // exercise3 in course1
        course = courseRepo.findAll().get(0);
        exercise = new TextExercise().course(course);
        exercise.setMaxScore(3.0);
        exerciseRepo.save(exercise);
        // score for tutor0 in exercise3 in course1
        studentParticipation = new StudentParticipation().exercise(exercise);
        studentParticipation.setParticipant(user);
        studentParticipation.setInitializationDate(ZonedDateTime.now());
        studentParticipationRepo.save(studentParticipation);
        result = database.addResultToParticipation(studentParticipation, null);
        result.setParticipation(studentParticipation);
        result.setRated(true);
        result.setAssessor(user);
        resultRepo.save(result);

        responseExerciseOne = request.get("/api/tutor-scores/exercise/" + exerciseRepo.findAll().get(0).getId(), HttpStatus.OK, List.class);
        assertThat(responseExerciseOne.isEmpty()).as("response is not empty").isFalse();
        assertThat(responseExerciseOne.size()).as("response has length 2").isEqualTo(2);
        responseExerciseTwo = request.get("/api/tutor-scores/exercise/" + exerciseRepo.findAll().get(1).getId(), HttpStatus.OK, List.class);
        assertThat(responseExerciseTwo.isEmpty()).as("response is not empty").isFalse();
        assertThat(responseExerciseTwo.size()).as("response has length 1").isEqualTo(1);
        List responseExerciseThree = request.get("/api/tutor-scores/exercise/" + exercise.getId(), HttpStatus.OK, List.class);
        assertThat(responseExerciseThree.isEmpty()).as("response is not empty").isFalse();
        assertThat(responseExerciseThree.size()).as("response has length 1").isEqualTo(1);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void tutorScoresForExerciseTestAccessForbiddenStudent() throws Exception {
        request.get("/api/tutor-scores/exercise/" + exerciseRepo.findAll().get(0).getId(), HttpStatus.FORBIDDEN, List.class);
        request.get("/api/tutor-scores/exercise/" + exerciseRepo.findAll().get(1).getId(), HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void tutorScoresForExerciseTestAccessForbiddenTutor() throws Exception {
        course = courseRepo.findAll().get(0);
        course.setTeachingAssistantGroupName("instructor");
        courseRepo.save(course);

        request.get("/api/tutor-scores/exercise/" + exerciseRepo.findAll().get(0).getId(), HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void tutorScoresForCourseTest() throws Exception {
        user = userRepo.findAllInGroup("tutor").get(0);

        List responseCourseOne = request.get("/api/tutor-scores/course/" + courseRepo.findAll().get(0).getId(), HttpStatus.OK, List.class);
        assertThat(responseCourseOne.isEmpty()).as("response is not empty").isFalse();
        assertThat(responseCourseOne.size()).as("response has length 2").isEqualTo(2);
        List responseCourseTwo = request.get("/api/tutor-scores/course/" + courseRepo.findAll().get(1).getId(), HttpStatus.OK, List.class);
        assertThat(responseCourseTwo.isEmpty()).as("response is not empty").isFalse();
        assertThat(responseCourseTwo.size()).as("response has length 1").isEqualTo(1);

        // exercise3 in course1
        course = courseRepo.findAll().get(0);
        exercise = new TextExercise().course(course);
        exercise.setMaxScore(6.0);
        exerciseRepo.save(exercise);
        // score for tutor1 in exercise3 in course1
        studentParticipation = new StudentParticipation().exercise(exercise);
        studentParticipation.setParticipant(user);
        studentParticipation.setInitializationDate(ZonedDateTime.now());
        studentParticipationRepo.save(studentParticipation);
        result = database.addResultToParticipation(studentParticipation, null);
        result.setParticipation(studentParticipation);
        result.setRated(true);
        result.setAssessor(user);
        resultRepo.save(result);

        responseCourseOne = request.get("/api/tutor-scores/course/" + courseRepo.findAll().get(0).getId(), HttpStatus.OK, List.class);
        assertThat(responseCourseOne.isEmpty()).as("response is not empty").isFalse();
        assertThat(responseCourseOne.size()).as("response has length 3").isEqualTo(3);
        responseCourseTwo = request.get("/api/tutor-scores/course/" + courseRepo.findAll().get(1).getId(), HttpStatus.OK, List.class);
        assertThat(responseCourseTwo.isEmpty()).as("response is not empty").isFalse();
        assertThat(responseCourseTwo.size()).as("response has length 1").isEqualTo(1);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void tutorScoresForCourseTestAccessForbiddenStudent() throws Exception {
        request.get("/api/tutor-scores/course/" + courseRepo.findAll().get(0).getId(), HttpStatus.FORBIDDEN, List.class);
        request.get("/api/tutor-scores/course/" + courseRepo.findAll().get(1).getId(), HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void tutorScoresForCourseTestAccessForbiddenTutor() throws Exception {
        course = courseRepo.findAll().get(0);
        course.setTeachingAssistantGroupName("instructor");
        courseRepo.save(course);

        request.get("/api/tutor-scores/course/" + course.getId(), HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void tutorScoreForExerciseAndTutor() throws Exception {
        user = userRepo.findAllInGroup("tutor").get(0);
        exercise = exerciseRepo.findAll().get(0);

        List<TutorScore> list = tutorScoresRepo.findAll();
        TutorScore tutorScore = list.get(0);

        var response = request.get("/api/tutor-scores/exercise/" + exercise.getId() + "/tutor/" + user.getLogin(), HttpStatus.OK, TutorScore.class);
        assertThat(response.getId()).as("response id is as expected").isEqualTo(tutorScore.getId());
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void tutorScoreForExerciseAndTutorAccessForbiddenTutor() throws Exception {
        user = userRepo.findOneByLogin("tutor1").get();
        exercise = exerciseRepo.findAll().get(0);
        course = courseRepo.findAll().get(0);
        course.setTeachingAssistantGroupName("instructor");
        courseRepo.save(course);

        request.get("/api/tutor-scores/exercise/" + exercise.getId() + "/tutor/" + user.getLogin(), HttpStatus.FORBIDDEN, TutorScore.class);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void tutorScoreDifferentAssessor() throws Exception {
        user = userRepo.findOneByLogin("tutor1").get();
        User user2 = userRepo.findOneByLogin("tutor3").get();
        exercise = exerciseRepo.findAll().get(0);
        result = resultRepo.findAll().get(0);
        result = resultRepo.findByIdWithEagerFeedbacksAndAssessor(result.getId()).get();

        var response = request.get("/api/tutor-scores/exercise/" + exercise.getId() + "/tutor/" + user.getLogin(), HttpStatus.OK, TutorScore.class);
        assertThat(response.getAssessments()).as("assessments are as expected").isEqualTo(1);

        result.setAssessor(user2);
        resultRepo.save(result);

        // manual because deleting from old is done in services
        tutorScore = request.get("/api/tutor-scores/exercise/" + exercise.getId() + "/tutor/" + user.getLogin(), HttpStatus.OK, TutorScore.class);
        tutorScore.setAssessments(tutorScore.getAssessments() - 1);
        tutorScore.setAssessmentsPoints(tutorScore.getAssessmentsPoints() - exercise.getMaxScore());
        tutorScoresRepo.save(tutorScore);

        response = request.get("/api/tutor-scores/exercise/" + exercise.getId() + "/tutor/" + user.getLogin(), HttpStatus.OK, TutorScore.class);
        assertThat(response.getAssessments()).as("assessments are as expected").isEqualTo(0);
        response = request.get("/api/tutor-scores/exercise/" + exercise.getId() + "/tutor/" + user2.getLogin(), HttpStatus.OK, TutorScore.class);
        assertThat(response.getAssessments()).as("assessments are as expected").isEqualTo(1);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void removeTutorScore() throws Exception {
        user = userRepo.findAllInGroup("tutor").get(0);
        exercise = exerciseRepo.findAll().get(0);

        TutorScore response = request.get("/api/tutor-scores/exercise/" + exercise.getId() + "/tutor/" + user.getLogin(), HttpStatus.OK, TutorScore.class);
        assertThat(response.getAssessmentsPoints()).as("assessment points are as expected").isEqualTo(exercise.getMaxScore());

        exercise.getTutorScores().add(response);
        exerciseRepo.save(exercise);

        result = resultRepo.findAll().get(0);
        resultRepo.delete(result);

        response = request.get("/api/tutor-scores/exercise/" + exercise.getId() + "/tutor/" + user.getLogin(), HttpStatus.OK, TutorScore.class);
        assertThat(response.getAssessmentsPoints()).as("assessment points are as expected").isEqualTo(0);
    }

    @Test
    @WithMockUser(value = "tutor3", roles = "TA")
    public void updateTutorScoreWithComplaint() throws Exception {
        user = userRepo.findAllInGroup("tutor").get(2);
        exercise = exerciseRepo.findAll().get(0);
        studentParticipation = new StudentParticipation().exercise(exercise);
        studentParticipation.setParticipant(userRepo.findAllInGroup("tumuser").get(2));
        studentParticipation.setInitializationDate(ZonedDateTime.now());
        studentParticipationRepo.save(studentParticipation);
        result = database.addResultToParticipation(studentParticipation, null);
        result.setParticipation(studentParticipation);
        result.setRated(true);
        result.setAssessor(user);
        resultRepo.save(result);

        // removal is somewhere else
        tutorScoreService.removeResult(result);

        TutorScore response = request.get("/api/tutor-scores/exercise/" + exercise.getId() + "/tutor/" + user.getLogin(), HttpStatus.OK, TutorScore.class);
        assertThat(response.getAllComplaints()).as("complaints amount is as expected").isEqualTo(0);

        exercise.getTutorScores().add(response);
        exerciseRepo.save(exercise);
        result.getParticipation().setExercise(exercise);
        result.setHasComplaint(true);
        resultRepo.save(result);

        // complaint
        complaint = new Complaint().result(result).complaintType(ComplaintType.COMPLAINT);
        complaintRepo.save(complaint);

        response = request.get("/api/tutor-scores/exercise/" + exercise.getId() + "/tutor/" + user.getLogin(), HttpStatus.OK, TutorScore.class);
        assertThat(response.getAllComplaints()).as("complaints amount is as expected").isEqualTo(1);

        ComplaintResponse complaintResponse = new ComplaintResponse().complaint(complaint.accepted(false)).responseText("rejected").reviewer(database.getUserByLogin("tutor1"));
        complaintResponseRepo.save(complaintResponse);

        response = request.get("/api/tutor-scores/exercise/" + exercise.getId() + "/tutor/" + user.getLogin(), HttpStatus.OK, TutorScore.class);
        assertThat(response.getAllComplaints()).as("complaints amount is as expected").isEqualTo(1);
    }

    @Test
    @WithMockUser(value = "tutor3", roles = "TA")
    public void updateTutorScoreWithFeedbackRequest() throws Exception {
        user = userRepo.findAllInGroup("tutor").get(2);
        exercise = exerciseRepo.findAll().get(0);
        studentParticipation = new StudentParticipation().exercise(exercise);
        studentParticipation.setParticipant(userRepo.findAllInGroup("tumuser").get(2));
        studentParticipation.setInitializationDate(ZonedDateTime.now());
        studentParticipationRepo.save(studentParticipation);
        result = database.addResultToParticipation(studentParticipation, null);
        result.setParticipation(studentParticipation);
        result.setRated(true);
        result.setAssessor(user);
        resultRepo.save(result);

        // removal is somewhere else
        tutorScoreService.removeResult(result);

        TutorScore response = request.get("/api/tutor-scores/exercise/" + exercise.getId() + "/tutor/" + user.getLogin(), HttpStatus.OK, TutorScore.class);
        assertThat(response.getAllFeedbackRequests()).as("feedback request amount is as expected").isEqualTo(0);

        exercise.getTutorScores().add(response);
        exerciseRepo.save(exercise);
        result.getParticipation().setExercise(exercise);
        result.setHasComplaint(true);
        resultRepo.save(result);

        // feedback request
        feedbackRequest = new Complaint().result(result).complaintType(ComplaintType.MORE_FEEDBACK);
        complaintRepo.save(feedbackRequest);

        response = request.get("/api/tutor-scores/exercise/" + exercise.getId() + "/tutor/" + user.getLogin(), HttpStatus.OK, TutorScore.class);
        assertThat(response.getAllFeedbackRequests()).as("feedback request amount is as expected").isEqualTo(1);
        assertThat(response.getNotAnsweredFeedbackRequests()).as("not answered feedback request amount is as expected").isEqualTo(1);

        ComplaintResponse answeredFeedbackRequest = new ComplaintResponse().complaint(feedbackRequest.accepted(true)).reviewer(user);
        complaintResponseRepo.save(answeredFeedbackRequest);

        response = request.get("/api/tutor-scores/exercise/" + exercise.getId() + "/tutor/" + user.getLogin(), HttpStatus.OK, TutorScore.class);
        assertThat(response.getAllFeedbackRequests()).as("feedback request amount is as expected").isEqualTo(1);
    }

    @Test
    @WithMockUser(value = "tutor3", roles = "TA")
    public void removeTutorScoreWithComplaint() throws Exception {
        user = userRepo.findAllInGroup("tutor").get(2);
        exercise = exerciseRepo.findAll().get(0);
        studentParticipation = new StudentParticipation().exercise(exercise);
        studentParticipation.setParticipant(userRepo.findAllInGroup("tumuser").get(2));
        studentParticipation.setInitializationDate(ZonedDateTime.now());
        studentParticipationRepo.save(studentParticipation);
        result = database.addResultToParticipation(studentParticipation, null);
        result.setParticipation(studentParticipation);
        result.setRated(true);
        result.setAssessor(user);
        resultRepo.save(result);

        var response = request.get("/api/tutor-scores/exercise/" + exercise.getId() + "/tutor/" + user.getLogin(), HttpStatus.OK, TutorScore.class);

        // removal is somewhere else
        tutorScoreService.removeResult(result);

        exercise.getTutorScores().add(response);
        exerciseRepo.save(exercise);
        result.getParticipation().setExercise(exercise);
        result.setHasComplaint(true);
        resultRepo.save(result);

        // complaint
        complaint = new Complaint().result(result).complaintType(ComplaintType.COMPLAINT);
        complaintRepo.save(complaint);

        response = request.get("/api/tutor-scores/exercise/" + exercise.getId() + "/tutor/" + user.getLogin(), HttpStatus.OK, TutorScore.class);
        assertThat(response.getAssessmentsPoints()).as("assessment points are as expected").isEqualTo(exercise.getMaxScore());
        assertThat(response.getAllComplaints()).as("complaints amount is as expected").isEqualTo(1);

        complaintRepo.delete(complaint);
        result.setHasComplaint(false);
        resultRepo.save(result);
        // manual removal because of missing complaint -> no idea how else to test
        tutorScore = tutorScoresRepo.findById(response.getId()).get();
        tutorScore.setAllComplaints(tutorScore.getAllComplaints() - 1);
        tutorScoresRepo.save(tutorScore);
        resultRepo.delete(result);

        // removal is somewhere else
        tutorScore = request.get("/api/tutor-scores/exercise/" + exercise.getId() + "/tutor/" + user.getLogin(), HttpStatus.OK, TutorScore.class);
        tutorScore.setAssessments(tutorScore.getAssessments() - 1);
        tutorScore.setAssessmentsPoints(tutorScore.getAssessmentsPoints() - exercise.getMaxScore());
        tutorScoresRepo.save(tutorScore);

        response = request.get("/api/tutor-scores/exercise/" + exercise.getId() + "/tutor/" + user.getLogin(), HttpStatus.OK, TutorScore.class);
        assertThat(response.getAssessmentsPoints()).as("assessment points are as expected").isEqualTo(0);
        assertThat(response.getAllComplaints()).as("complaints amount is as expected").isEqualTo(0);
    }

    @Test
    @WithMockUser(value = "tutor3", roles = "TA")
    public void removeTutorScoreWithFeedbackRequest() throws Exception {
        user = userRepo.findAllInGroup("tutor").get(2);
        exercise = exerciseRepo.findAll().get(0);
        studentParticipation = new StudentParticipation().exercise(exercise);
        studentParticipation.setParticipant(userRepo.findAllInGroup("tumuser").get(2));
        studentParticipation.setInitializationDate(ZonedDateTime.now());
        studentParticipationRepo.save(studentParticipation);
        result = database.addResultToParticipation(studentParticipation, null);
        result.setParticipation(studentParticipation);
        result.setRated(true);
        result.setAssessor(user);
        resultRepo.save(result);

        var response = request.get("/api/tutor-scores/exercise/" + exercise.getId() + "/tutor/" + user.getLogin(), HttpStatus.OK, TutorScore.class);

        exercise.getTutorScores().add(response);
        exerciseRepo.save(exercise);
        result.getParticipation().setExercise(exercise);
        result.setHasComplaint(true);
        resultRepo.save(result);

        // feedback request
        feedbackRequest = new Complaint().result(result).complaintType(ComplaintType.MORE_FEEDBACK);
        complaintRepo.save(feedbackRequest);

        response = request.get("/api/tutor-scores/exercise/" + exercise.getId() + "/tutor/" + user.getLogin(), HttpStatus.OK, TutorScore.class);
        assertThat(response.getAssessmentsPoints()).as("assessment points are as expected").isEqualTo(exercise.getMaxScore());
        assertThat(response.getAllFeedbackRequests()).as("feedback requests amount is as expected").isEqualTo(1);
        assertThat(response.getNotAnsweredFeedbackRequests()).as("not answered feedback requests amount is as expected").isEqualTo(1);

        complaintRepo.delete(feedbackRequest);
        result.setHasComplaint(false);
        resultRepo.save(result);
        // remove missing complaint
        tutorScore = tutorScoresRepo.findById(response.getId()).get();
        tutorScore.setAllFeedbackRequests(tutorScore.getAllFeedbackRequests() - 1);
        tutorScoresRepo.save(tutorScore);
        resultRepo.delete(result);

        // removal is somewhere else
        tutorScore = request.get("/api/tutor-scores/exercise/" + exercise.getId() + "/tutor/" + user.getLogin(), HttpStatus.OK, TutorScore.class);
        tutorScore.setAssessments(tutorScore.getAssessments() - 1);
        tutorScore.setAssessmentsPoints(tutorScore.getAssessmentsPoints() - exercise.getMaxScore());
        tutorScoresRepo.save(tutorScore);

        response = request.get("/api/tutor-scores/exercise/" + exercise.getId() + "/tutor/" + user.getLogin(), HttpStatus.OK, TutorScore.class);
        assertThat(response.getAssessmentsPoints()).as("assessment points are as expected").isEqualTo(0);
        assertThat(response.getAllComplaints()).as("feedback requests amount is as expected").isEqualTo(0);
    }
}
