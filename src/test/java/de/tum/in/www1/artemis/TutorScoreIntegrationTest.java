package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import de.tum.in.www1.artemis.domain.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.scores.StudentScore;
import de.tum.in.www1.artemis.domain.scores.TutorScore;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.TutorScoreRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
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
    ParticipationRepository participationRepo;

    @Autowired
    UserRepository userRepo;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    // private TutorScore tutorScore;

    private User user;

    private Course course;

    private Exercise exercise;

    private Participation participation;

    private Result result;

    private List<Feedback> feedbacks;

    private Complaint complaint;

    private ComplaintResponse complaintResponse;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(2, 2, 2);

        // course1
        course = ModelFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "tumuser", "tutor", "instructor");
        courseRepo.save(course);
        // exercise1
        exercise = ModelFactory.generateTextExercise(ZonedDateTime.now(), ZonedDateTime.now(), ZonedDateTime.now(), course);
        exercise.setMaxScore(8.0);
        exerciseRepo.save(exercise);

        // score for tutor1 in exercise1 in course1
        user = userRepo.findAllInGroup("tumuser").get(0);
        participation = database.addParticipationForExercise(exercise, user.getLogin());
        result = ModelFactory.generateResult(true, 75).resultString("Good effort!").participation(participation);
        user = userRepo.findAllInGroup("tutor").get(0);
        feedbacks = ModelFactory.generateFeedback().stream().peek(feedback -> feedback.setText("Nice work here")).collect(Collectors.toList());
        result.setFeedbacks(feedbacks);
        result.setAssessor(userRepo.findAllInGroup("tutor").get(0));
        resultRepo.save(result);

        // score for tutor2 in exercise1 in course1
        user = userRepo.findAllInGroup("tumuser").get(1);
        participation = database.addParticipationForExercise(exercise, user.getLogin());
        result = ModelFactory.generateResult(true, 80).resultString("Good effort!").participation(participation);
        user = userRepo.findAllInGroup("tutor").get(1);
        feedbacks = ModelFactory.generateFeedback().stream().peek(feedback -> feedback.setText("Good work here")).collect(Collectors.toList());
        result.setFeedbacks(feedbacks);
        result.setAssessor(userRepo.findAllInGroup("tutor").get(1));
        resultRepo.save(result);

        // course2
        course = ModelFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "tumuser", "tutor", "instructor");
        courseRepo.save(course);
        // exercise2
        exercise = ModelFactory.generateTextExercise(ZonedDateTime.now(), ZonedDateTime.now(), ZonedDateTime.now(), course);
        exerciseRepo.save(exercise);

        // score for tutor1 in exercise2 in course2
        user = userRepo.findAllInGroup("tumuser").get(0);
        participation = database.addParticipationForExercise(exercise, user.getLogin());
        result = ModelFactory.generateResult(true, 20).resultString("At least you tried!").participation(participation);
        user = userRepo.findAllInGroup("tutor").get(0);
        feedbacks = ModelFactory.generateFeedback().stream().peek(feedback -> feedback.setText("Not so good")).collect(Collectors.toList());
        result.setFeedbacks(feedbacks);
        result.setAssessor(userRepo.findAllInGroup("tutor").get(0));
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

        course = courseRepo.findAll().get(0);
        // exercise3
        exercise = ModelFactory.generateTextExercise(ZonedDateTime.now(), ZonedDateTime.now(), ZonedDateTime.now(), course);
        exerciseRepo.save(exercise);
        // score for tutor0 in exercise3 in course1
        participation = database.addParticipationForExercise(exercise, user.getLogin());
        result = ModelFactory.generateResult(true, 80).resultString("Nice effort!").participation(participation);
        feedbacks = ModelFactory.generateFeedback().stream().peek(feedback -> feedback.setText("Really good!")).collect(Collectors.toList());
        result.setFeedbacks(feedbacks);
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

        course = courseRepo.findAll().get(0);
        // exercise3
        exercise = ModelFactory.generateTextExercise(ZonedDateTime.now(), ZonedDateTime.now(), ZonedDateTime.now(), course);
        exerciseRepo.save(exercise);
        // score for tutor1 in exercise3 in course1
        participation = database.addParticipationForExercise(exercise, user.getLogin());
        result = ModelFactory.generateResult(true, 60).resultString("Nice try!").participation(participation);
        feedbacks = ModelFactory.generateFeedback().stream().peek(feedback -> feedback.setText("Pretty good!")).collect(Collectors.toList());
        result.setFeedbacks(feedbacks);
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
    public void tutorScoreForCourseTutorAndExercise() throws Exception {
        user = userRepo.findAllInGroup("tutor").get(0);
        exercise = exerciseRepo.findAll().get(0);

        List<TutorScore> list = tutorScoresRepo.findAll();
        TutorScore tutorScore = list.get(0);

        TutorScore response = request.get("/api/tutor-scores/exercise/" + exercise.getId() + "/tutor/" + user.getLogin(), HttpStatus.OK, TutorScore.class);
        assertThat(response.getId()).as("response id is as expected").isEqualTo(tutorScore.getId());
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void removeTutorScore() throws Exception {
        user = userRepo.findAllInGroup("tutor").get(0);
        exercise = exerciseRepo.findAll().get(0);

        TutorScore response = request.get("/api/tutor-scores/exercise/" + exercise.getId() + "/tutor/" + user.getLogin(), HttpStatus.OK, TutorScore.class);
        response.getAssessmentsPoints();

        result = resultRepo.findAll().get(0);

        


        resultRepo.delete(result);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void newTutorScoreWithComplaintAndResponse() throws Exception {

    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void updateTutorScoreWithComplaintAndResponse() throws Exception {

    }
}
