// package de.tum.in.www1.artemis;
//
// import de.tum.in.www1.artemis.domain.*;
// import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
// import de.tum.in.www1.artemis.domain.scores.StudentScore;
// import de.tum.in.www1.artemis.repository.*;
// import de.tum.in.www1.artemis.service.StudentScoreService;
// import de.tum.in.www1.artemis.util.DatabaseUtilService;
// import de.tum.in.www1.artemis.util.RequestUtilService;
// import org.junit.jupiter.api.AfterEach;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.http.HttpStatus;
// import org.springframework.security.test.context.support.WithMockUser;
//
// import java.time.ZonedDateTime;
// import java.util.List;
//
// import static org.assertj.core.api.Assertions.assertThat;
//
// public class StudentScoreIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {
//
// @Autowired
// CourseRepository courseRepo;
//
// @Autowired
// ExerciseRepository exerciseRepo;
//
// @Autowired
// ResultRepository resultRepo;
//
// @Autowired
// StudentScoreRepository studentScoresRepo;
//
// @Autowired
// StudentParticipationRepository studentParticipationRepo;
//
// @Autowired
// UserRepository userRepo;
//
// @Autowired
// DatabaseUtilService database;
//
// @Autowired
// RequestUtilService request;
//
// @Autowired
// StudentScoreService studentScoreService;
//
// private User user;
//
// private Course course;
//
// private Exercise exercise;
//
// private StudentParticipation studentParticipation;
//
// private Result result;
//
// @BeforeEach
// public void initTestCase() {
// database.addUsers(3, 3, 3);
//
// // course1
// course = database.addCourseWithOneFinishedTextExercise();
// course.setStudentGroupName("tumuser");
// course.setTeachingAssistantGroupName("tutor");
// course.setInstructorGroupName("instructor");
// courseRepo.save(course);
// // exercise1
// exercise = course.getExercises().stream().findFirst().get();
// exerciseRepo.save(exercise);
//
// // score for student1 in exercise1 in course1
// user = userRepo.findAllInGroup("tumuser").get(0);
// studentParticipation = new StudentParticipation().exercise(exercise);
// studentParticipation.setParticipant(user);
// studentParticipation.setInitializationDate(ZonedDateTime.now());
// studentParticipationRepo.save(studentParticipation);
// result = new Result();
// result.setParticipation(studentParticipation);
// result.setRated(true);
// result.setScore(70L);
// resultRepo.save(result);
//
// // score for student2 in exercise1 in course1
// user = userRepo.findAllInGroup("tumuser").get(1);
// studentParticipation = new StudentParticipation().exercise(exercise);
// studentParticipation.setParticipant(user);
// studentParticipation.setInitializationDate(ZonedDateTime.now());
// studentParticipationRepo.save(studentParticipation);
// result = new Result();
// result.setParticipation(studentParticipation);
// result.setRated(true);
// result.setScore(80L);
// resultRepo.save(result);
//
// // course2
// course = database.addCourseWithOneFinishedTextExercise();
// course.setStudentGroupName("tumuser");
// course.setTeachingAssistantGroupName("tutor");
// course.setInstructorGroupName("instructor");
// courseRepo.save(course);
// // exercise2
// exercise = course.getExercises().stream().findFirst().get();
// exerciseRepo.save(exercise);
//
// // score for student1 in exercise2 in course2
// user = userRepo.findAllInGroup("tumuser").get(0);
// studentParticipation = new StudentParticipation().exercise(exercise);
// studentParticipation.setParticipant(user);
// studentParticipation.setInitializationDate(ZonedDateTime.now());
// studentParticipationRepo.save(studentParticipation);
// result = new Result();
// result.setParticipation(studentParticipation);
// result.setRated(true);
// result.setScore(60L);
// resultRepo.save(result);
// }
//
// @AfterEach
// public void tearDown() {
// database.resetDatabase();
// }
//
// @Test
// @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
// public void studentScoresForExerciseTest() throws Exception {
// // TODO: change back to student1 USER after releasing feature for students
// List responseExerciseOne = request.get("/api/student-scores/exercise/" + exerciseRepo.findAll().get(0).getId(), HttpStatus.OK, List.class);
// assertThat(responseExerciseOne.isEmpty()).as("response is not empty").isFalse();
// assertThat(responseExerciseOne.size()).as("response has length 2").isEqualTo(2);
//
// course = courseRepo.findAll().get(0);
// // exercise3
// exercise = new TextExercise().course(course);
// exerciseRepo.save(exercise);
// // score for student2 in exercise3 in course1
// studentParticipation = new StudentParticipation().exercise(exercise);
// studentParticipation.setParticipant(user);
// studentParticipation.setInitializationDate(ZonedDateTime.now());
// studentParticipationRepo.save(studentParticipation);
// result = new Result();
// result.setParticipation(studentParticipation);
// result.setRated(true);
// result.setScore(75L);
// resultRepo.save(result);
//
// responseExerciseOne = request.get("/api/student-scores/exercise/" + exerciseRepo.findAll().get(0).getId(), HttpStatus.OK, List.class);
// assertThat(responseExerciseOne.isEmpty()).as("response is not empty").isFalse();
// assertThat(responseExerciseOne.size()).as("response has length 2").isEqualTo(2);
//
// List responseExerciseTwo = request.get("/api/student-scores/exercise/" + exercise.getId(), HttpStatus.OK, List.class);
// assertThat(responseExerciseTwo.isEmpty()).as("response is not empty").isFalse();
// assertThat(responseExerciseTwo.size()).as("response has length 1").isEqualTo(1);
// }
//
// @Test
// @WithMockUser(value = "student1", roles = "USER")
// public void studentScoresForExerciseTestAccessForbidden() throws Exception {
// course = courseRepo.findAll().get(0);
// course.setStudentGroupName("tutor");
// courseRepo.save(course);
//
// request.get("/api/student-scores/exercise/" + exerciseRepo.findAll().get(0).getId(), HttpStatus.FORBIDDEN, List.class);
// }
//
// @Test
// @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
// public void studentScoresForExerciseTestAccessForbiddenInstructor() throws Exception {
// // TODO: change back to student1 USER after releasing feature for students
// course = courseRepo.findAll().get(0);
// course.setInstructorGroupName("test");
// courseRepo.save(course);
//
// request.get("/api/student-scores/exercise/" + exerciseRepo.findAll().get(0).getId(), HttpStatus.FORBIDDEN, List.class);
// }
//
// @Test
// @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
// public void studentScoresForCourseTest() throws Exception {
// // TODO: change back to student1 USER after releasing feature for students
// List responseCourseOne = request.get("/api/student-scores/course/" + courseRepo.findAll().get(0).getId(), HttpStatus.OK, List.class);
// assertThat(responseCourseOne.isEmpty()).as("response is not empty").isFalse();
// assertThat(responseCourseOne.size()).as("response has length 2").isEqualTo(2);
// List responseCourseTwo = request.get("/api/student-scores/course/" + courseRepo.findAll().get(1).getId(), HttpStatus.OK, List.class);
// assertThat(responseCourseTwo.isEmpty()).as("response is not empty").isFalse();
// assertThat(responseCourseTwo.size()).as("response has length 1").isEqualTo(1);
//
// course = courseRepo.findAll().get(0);
// // exercise3
// exercise = new TextExercise().course(course);
// exerciseRepo.save(exercise);
// // score for student2 in exercise3 in course1
// studentParticipation = new StudentParticipation().exercise(exercise);
// studentParticipation.setParticipant(user);
// studentParticipation.setInitializationDate(ZonedDateTime.now());
// studentParticipationRepo.save(studentParticipation);
// result = new Result();
// result.setParticipation(studentParticipation);
// result.setRated(true);
// result.setScore(30L);
// resultRepo.save(result);
//
// responseCourseOne = request.get("/api/student-scores/course/" + courseRepo.findAll().get(0).getId(), HttpStatus.OK, List.class);
// assertThat(responseCourseOne.isEmpty()).as("response is not empty").isFalse();
// assertThat(responseCourseOne.size()).as("response has length 3").isEqualTo(3);
// responseCourseTwo = request.get("/api/student-scores/course/" + courseRepo.findAll().get(1).getId(), HttpStatus.OK, List.class);
// assertThat(responseCourseTwo.isEmpty()).as("response is not empty").isFalse();
// assertThat(responseCourseTwo.size()).as("response has length 1").isEqualTo(1);
// }
//
// @Test
// @WithMockUser(value = "student1", roles = "USER")
// public void studentScoresForCourseTestAccessForbidden() throws Exception {
// course = courseRepo.findAll().get(0);
// course.setStudentGroupName("tutor");
// courseRepo.save(course);
//
// request.get("/api/student-scores/course/" + course.getId(), HttpStatus.FORBIDDEN, List.class);
// }
//
// @Test
// @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
// public void studentScoresForCourseTestAccessForbiddenInstructor() throws Exception {
// // TODO: change back to student1 USER after releasing feature for students
// course = courseRepo.findAll().get(0);
// course.setInstructorGroupName("test");
// courseRepo.save(course);
//
// request.get("/api/student-scores/course/" + course.getId(), HttpStatus.FORBIDDEN, List.class);
// }
//
// @Test
// @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
// public void studentScoreCreationAfterNewResult() throws Exception {
// user = userRepo.findAllInGroup("tumuser").get(2);
// exercise = exerciseRepo.findAll().get(0);
//
// List responseExerciseOne = request.get("/api/student-scores/exercise/" + exercise.getId(), HttpStatus.OK, List.class);
// assertThat(responseExerciseOne.isEmpty()).as("response is not empty").isFalse();
// assertThat(responseExerciseOne.size()).as("response has length 2").isEqualTo(2);
//
// // score for student3 in exercise1 in course1
// studentParticipation = new StudentParticipation().exercise(exercise);
// studentParticipation.setParticipant(user);
// studentParticipation.setInitializationDate(ZonedDateTime.now());
// studentParticipationRepo.save(studentParticipation);
// result = new Result();
// result.setParticipation(studentParticipation);
// result.setRated(true);
// result.setScore(90L);
// resultRepo.save(result);
//
// responseExerciseOne = request.get("/api/student-scores/exercise/" + exercise.getId(), HttpStatus.OK, List.class);
//
// assertThat(responseExerciseOne.isEmpty()).as("response is not empty").isFalse();
// assertThat(responseExerciseOne.size()).as("response has length 3").isEqualTo(3);
// }
//
// @Test
// @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
// public void studentScoreForStudentAndExerciseTest() throws Exception {
// // TODO: change back to student1 USER after releasing feature for students
// user = userRepo.findAllInGroup("tumuser").get(0);
// exercise = exerciseRepo.findAll().get(0);
//
// List<StudentScore> list = studentScoresRepo.findAll();
// StudentScore studentScore = list.get(0);
//
// StudentScore response = request.get("/api/student-scores/exercise/" + exercise.getId() + "/student/" + user.getLogin(), HttpStatus.OK, StudentScore.class);
// assertThat(response.getId()).as("response id is as expected").isEqualTo(studentScore.getId());
// }
//
// @Test
// @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
// public void studentScoreForStudentAndExerciseTestAccessForbiddenInstructor() throws Exception {
// // TODO: change back to student1 USER after releasing feature for students
// course = courseRepo.findAll().get(0);
// course.setInstructorGroupName("test");
// courseRepo.save(course);
// user = userRepo.findAllInGroup("tumuser").get(0);
// exercise = exerciseRepo.findAll().get(0);
//
// request.get("/api/student-scores/exercise/" + exercise.getId() + "/student/" + user.getLogin(), HttpStatus.FORBIDDEN, List.class);
// }
//
// @Test
// @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
// public void studentScoreUpdateTest() throws Exception {
// user = userRepo.findAllInGroup("tumuser").get(0);
// exercise = exerciseRepo.findAll().get(0);
// result = resultRepo.findAll().get(0);
//
// StudentScore response = request.get("/api/student-scores/exercise/" + exercise.getId() + "/student/" + user.getLogin(), HttpStatus.OK, StudentScore.class);
// assertThat(response.getLastScore()).as("response score is old score").isEqualTo(result.getScore());
//
// result.setScore(100L);
// resultRepo.save(result);
//
// response = request.get("/api/student-scores/exercise/" + exercise.getId() + "/student/" + user.getLogin(), HttpStatus.OK, StudentScore.class);
// assertThat(response.getLastScore()).as("response score is new score").isEqualTo(result.getScore());
// }
//
// @Test
// @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
// public void studentScoreDeleteByResultTest() throws Exception {
// List responseExerciseOne = request.get("/api/student-scores/exercise/" + exerciseRepo.findAll().get(0).getId(), HttpStatus.OK, List.class);
// assertThat(responseExerciseOne.isEmpty()).as("response is not empty").isFalse();
// assertThat(responseExerciseOne.size()).as("response has length 2").isEqualTo(2);
//
// result = resultRepo.findAll().get(0);
// resultRepo.deleteById(result.getId());
//
// responseExerciseOne = request.get("/api/student-scores/exercise/" + exerciseRepo.findAll().get(0).getId(), HttpStatus.OK, List.class);
// assertThat(responseExerciseOne.isEmpty()).as("response is not empty").isFalse();
// assertThat(responseExerciseOne.size()).as("response has length 1").isEqualTo(1);
// }
//
// @Test
// @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
// public void studentScoreNewResultForExistingScore() throws Exception {
// user = userRepo.findAllInGroup("tumuser").get(0);
// exercise = exerciseRepo.findAll().get(0);
// Result oldResult = resultRepo.findAll().get(0);
// studentParticipation = studentParticipationRepo.findAll().get(0);
//
// StudentScore response = request.get("/api/student-scores/exercise/" + exercise.getId() + "/student/" + user.getLogin(), HttpStatus.OK, StudentScore.class);
// assertThat(response.getResult().getId()).as("response result id is old result id").isEqualTo(oldResult.getId());
//
// Result newResult = new Result();
// newResult.setParticipation(studentParticipation);
// newResult.setRated(true);
// newResult.setScore(15L);
// resultRepo.save(newResult);
// // kleiner umweg
// resultRepo.delete(oldResult);
//
// response = request.get("/api/student-scores/exercise/" + exercise.getId() + "/student/" + user.getLogin(), HttpStatus.OK, StudentScore.class);
// assertThat(response.getResult().getId()).as("response result id is new result id").isEqualTo(newResult.getId());
// }
// }
