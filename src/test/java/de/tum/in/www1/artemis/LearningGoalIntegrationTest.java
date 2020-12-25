package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.lecture.TextUnit;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.CourseLearningGoalProgress;
import de.tum.in.www1.artemis.web.rest.dto.IndividualLearningGoalProgress;

public class LearningGoalIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    LectureRepository lectureRepository;

    @Autowired
    ExerciseRepository exerciseRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CourseStudentViewRepository courseStudentViewRepository;

    @Autowired
    StudentParticipationRepository studentParticipationRepository;

    @Autowired
    SubmissionRepository submissionRepository;

    @Autowired
    ResultRepository resultRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    TextUnitRepository textUnitRepository;

    @Autowired
    ExerciseUnitRepository exerciseUnitRepository;

    @Autowired
    LearningGoalRepository learningGoalRepository;

    @Autowired
    LectureUnitRepository lectureUnitRepository;

    Long idOfCourse;

    Long idOfLearningGoal;

    Long idOfLectureOne;

    Long idOfTextUnitOfLectureOne;

    Long idOfExerciseUnitTextOfLectureOne;

    Long idOfExerciseUnitModelingOfLectureOne;

    Long idOfLectureTwo;

    Long idOfTextUnitOfLectureTwo;

    Long idOfExerciseUnitTextOfLectureTwo;

    Long idOfExerciseUnitModelingOfLectureTwo;

    Long idOfTextExercise;

    Long idOfTextExerciseParticipation;

    Long idOfTextExerciseSubmission;

    Long idOfTextExerciseResult;

    Long idOfModelingExercise;

    Long idOfModelingExerciseParticipation;

    Long idOfModelingExerciseSubmission;

    Long idOfModelingExerciseResult;

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @BeforeEach
    public void setupTestScenario() throws Exception {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        // creating the users student1-student10, tutor1-tutor10 and instructors1-instructor10
        this.database.addUsers(10, 10, 10);

        // Add users that are not in the course
        userRepository.save(ModelFactory.generateActivatedUser("student42"));
        userRepository.save(ModelFactory.generateActivatedUser("tutor42"));
        userRepository.save(ModelFactory.generateActivatedUser("instructor42"));
        // creating course
        Course course = this.database.createCourse();
        idOfCourse = course.getId();

        // setting up course student view (view is just a table in embedded test scenario and therefore needs to be filled manually!!)
        for (int i = 1; i <= 10; i++) {
            Long studentId = userRepository.findOneByLogin("student" + i).get().getId();
            CourseStudentsView.CourseStudentViewId courseStudentViewId = new CourseStudentsView.CourseStudentViewId(idOfCourse, studentId);
            courseStudentViewRepository.save(new CourseStudentsView(courseStudentViewId));
        }

        createLectureOne(course);
        createLectureTwo(course);
        createTextExercise(pastTimestamp, pastTimestamp, pastTimestamp);
        createModelingExercise(pastTimestamp, pastTimestamp, pastTimestamp);
        creatingLectureUnitsOfLectureOne();
        creatingLectureUnitsOfLectureTwo();
        createLearningGoal();
    }

    private void createLearningGoal() {
        Course course;
        course = courseRepository.findWithEagerLearningGoalsById(idOfCourse).get();
        LearningGoal learningGoal = new LearningGoal();
        learningGoal.setTitle("LearningGoalOne");
        learningGoal.setDescription("This is an example learning goal");
        learningGoal.setCourse(course);
        List<LectureUnit> allLectureUnits = lectureUnitRepository.findAll();
        Set<LectureUnit> connectedLectureUnits = new HashSet<>(allLectureUnits);
        learningGoal.setLectureUnits(connectedLectureUnits);
        learningGoal = learningGoalRepository.save(learningGoal);
        idOfLearningGoal = learningGoal.getId();
    }

    private void creatingLectureUnitsOfLectureOne() {
        // creating lecture units for lecture one
        TextUnit textUnit = new TextUnit();
        textUnit.setName("TextUnitOfLectureOne");
        textUnit = textUnitRepository.save(textUnit);
        idOfTextUnitOfLectureOne = textUnit.getId();

        Exercise textExercise = exerciseRepository.findById(idOfTextExercise).get();
        ExerciseUnit textExerciseUnit = new ExerciseUnit();
        textExerciseUnit.setExercise(textExercise);
        textExerciseUnit = exerciseUnitRepository.save(textExerciseUnit);
        idOfExerciseUnitTextOfLectureOne = textExerciseUnit.getId();

        Exercise modelingExercise = exerciseRepository.findById(idOfModelingExercise).get();
        ExerciseUnit modelingExerciseUnit = new ExerciseUnit();
        modelingExerciseUnit.setExercise(modelingExercise);
        modelingExerciseUnit = exerciseUnitRepository.save(modelingExerciseUnit);
        idOfExerciseUnitModelingOfLectureOne = modelingExerciseUnit.getId();

        List<LectureUnit> lectureUnitsOfLectureOne = List.of(textUnit, textExerciseUnit, modelingExerciseUnit);
        Lecture lectureOne = lectureRepository.findByIdWithStudentQuestionsAndLectureUnitsAndLearningGoals(idOfLectureOne).get();
        for (LectureUnit lectureUnit : lectureUnitsOfLectureOne) {
            lectureOne.addLectureUnit(lectureUnit);
        }
        lectureRepository.save(lectureOne);
    }

    private void creatingLectureUnitsOfLectureTwo() {
        // creating lecture units for lecture one
        TextUnit textUnit = new TextUnit();
        textUnit.setName("TextUnitOfLectureTwo");
        textUnit = textUnitRepository.save(textUnit);
        idOfTextUnitOfLectureTwo = textUnit.getId();

        Exercise textExercise = exerciseRepository.findById(idOfTextExercise).get();
        ExerciseUnit textExerciseUnit = new ExerciseUnit();
        textExerciseUnit.setExercise(textExercise);
        textExerciseUnit = exerciseUnitRepository.save(textExerciseUnit);
        idOfExerciseUnitTextOfLectureTwo = textExerciseUnit.getId();

        Exercise modelingExercise = exerciseRepository.findById(idOfModelingExercise).get();
        ExerciseUnit modelingExerciseUnit = new ExerciseUnit();
        modelingExerciseUnit.setExercise(modelingExercise);
        modelingExerciseUnit = exerciseUnitRepository.save(modelingExerciseUnit);
        idOfExerciseUnitModelingOfLectureTwo = modelingExerciseUnit.getId();

        List<LectureUnit> lectureUnitsOfLectureTwo = List.of(textUnit, textExerciseUnit, modelingExerciseUnit);
        Lecture lectureTwo = lectureRepository.findByIdWithStudentQuestionsAndLectureUnitsAndLearningGoals(idOfLectureTwo).get();
        for (LectureUnit lectureUnit : lectureUnitsOfLectureTwo) {
            lectureTwo.addLectureUnit(lectureUnit);
        }
        lectureRepository.save(lectureTwo);
    }

    private void createLectureOne(Course course) {
        Lecture lectureOne = new Lecture();
        lectureOne.setTitle("LectureOne");
        lectureOne.setCourse(course);
        lectureOne = lectureRepository.save(lectureOne);
        idOfLectureOne = lectureOne.getId();
    }

    private void createLectureTwo(Course course) {
        Lecture lectureTwo = new Lecture();
        lectureTwo.setTitle("LectureTwo");
        lectureTwo.setCourse(course);
        lectureTwo = lectureRepository.save(lectureTwo);
        idOfLectureTwo = lectureTwo.getId();
    }

    private void createTextExercise(ZonedDateTime pastTimestamp, ZonedDateTime futureTimestamp, ZonedDateTime futureFutureTimestamp) {
        Course course;
        // creating text exercise with Result
        course = courseRepository.findWithEagerExercisesById(idOfCourse);
        TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        textExercise.setMaxScore(10.0);
        textExercise.setBonusPoints(0.0);
        textExercise = exerciseRepository.save(textExercise);
        idOfTextExercise = textExercise.getId();
        User user = (userRepository.findOneByLogin("student1")).get();
        // participation
        StudentParticipation textParticipation = ModelFactory.generateStudentParticipation(InitializationState.FINISHED, textExercise, user);
        textParticipation = studentParticipationRepository.save(textParticipation);
        idOfTextExerciseParticipation = textParticipation.getId();
        // submission
        Submission textSubmission = ModelFactory.generateTextSubmission("text", Language.ENGLISH, true);
        textSubmission.setParticipation(textParticipation);
        textSubmission = submissionRepository.save(textSubmission);
        idOfTextExerciseSubmission = textSubmission.getId();

        // result
        Result textResult = ModelFactory.generateResult(true, 50);
        textResult.setParticipation(textParticipation);
        textResult = resultRepository.save(textResult);
        idOfTextExerciseResult = textResult.getId();

        textSubmission.addResult(textResult);
        textResult.setSubmission(textSubmission);
        submissionRepository.save(textSubmission);
    }

    private void createModelingExercise(ZonedDateTime pastTimestamp, ZonedDateTime futureTimestamp, ZonedDateTime futureFutureTimestamp) {
        Course course;
        // creating text exercise with Result
        course = courseRepository.findWithEagerExercisesById(idOfCourse);
        ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram, course);
        modelingExercise.setMaxScore(10.0);
        modelingExercise.setBonusPoints(0.0);
        modelingExercise = exerciseRepository.save(modelingExercise);
        idOfModelingExercise = modelingExercise.getId();
        User user = (userRepository.findOneByLogin("student1")).get();
        // participation
        StudentParticipation modelingParticipation = ModelFactory.generateStudentParticipation(InitializationState.FINISHED, modelingExercise, user);
        modelingParticipation = studentParticipationRepository.save(modelingParticipation);
        idOfModelingExerciseParticipation = modelingParticipation.getId();
        // submission
        Submission modelingSubmission = ModelFactory.generateTextSubmission("text", Language.ENGLISH, true);
        modelingSubmission.setParticipation(modelingParticipation);
        modelingSubmission = submissionRepository.save(modelingSubmission);
        idOfModelingExerciseSubmission = modelingSubmission.getId();
        // result
        Result modelingResult = ModelFactory.generateResult(true, 50);
        modelingResult.setParticipation(modelingParticipation);
        modelingResult = resultRepository.save(modelingResult);
        idOfModelingExerciseResult = modelingResult.getId();

        modelingSubmission.addResult(modelingResult);
        modelingResult.setSubmission(modelingSubmission);
        submissionRepository.save(modelingSubmission);

    }

    private void testAllPreAuthorize() throws Exception {
        request.put("/api/courses/" + idOfCourse + "/goals", new LearningGoal(), HttpStatus.FORBIDDEN);
        request.post("/api/courses/" + idOfCourse + "/goals", new LearningGoal(), HttpStatus.FORBIDDEN);
        request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.FORBIDDEN, LearningGoal.class);
        request.delete("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getLearningGoal_asInstructor_shouldReturnLearningGoal() throws Exception {
        LearningGoal learningGoal = request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.OK, LearningGoal.class);
        assertThat(learningGoal.getId()).isEqualTo(idOfLearningGoal);
    }

    @Test
    @WithMockUser(username = "instructor42", roles = "INSTRUCTOR")
    public void getLearningGoal_asInstructorNotInCourse_shouldReturnForbidden() throws Exception {
        request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.FORBIDDEN, LearningGoal.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getLearningGoalsOfCourse_asStudent1_shouldReturnLearningGoals() throws Exception {
        List<LearningGoal> learningGoalsOfCourse = request.getList("/api/courses/" + idOfCourse + "/goals", HttpStatus.OK, LearningGoal.class);
        assertThat(learningGoalsOfCourse.size()).isEqualTo(1);
        assertThat(learningGoalsOfCourse.get(0).getId()).isEqualTo(idOfLearningGoal);
    }

    @Test
    @WithMockUser(username = "student42", roles = "USER")
    public void getLearningGoalsOfCourse_asStudentNotInCourse_shouldReturnForbidden() throws Exception {
        request.getList("/api/courses/" + idOfCourse + "/goals", HttpStatus.FORBIDDEN, LearningGoal.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteLearningGoal_asInstructor_shouldDeleteLearningGoal() throws Exception {
        request.delete("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.OK);
        request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.NOT_FOUND, LearningGoal.class);
    }

    @Test
    @WithMockUser(username = "instructor42", roles = "INSTRUCTOR")
    public void deleteLearningGoal_asInstructorNotInCourse_shouldReturnForbidden() throws Exception {
        request.delete("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void deleteCourse_asAdmin_shouldAlsoDeleteLearningGoal() throws Exception {
        request.delete("/api/courses/" + idOfCourse, HttpStatus.OK);
        request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.NOT_FOUND, LearningGoal.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteLecture_asInstructor_shouldUpdateLearningGoal() throws Exception {
        request.delete("/api/lectures/" + idOfLectureTwo, HttpStatus.OK);
        LearningGoal learningGoal = request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.OK, LearningGoal.class);
        assertThat(learningGoal.getLectureUnits().stream().map(DomainObject::getId))
                .containsAll(Set.of(idOfTextUnitOfLectureOne, idOfExerciseUnitTextOfLectureOne, idOfExerciseUnitModelingOfLectureOne));
        assertThat(learningGoal.getLectureUnits().stream().map(DomainObject::getId))
                .doesNotContainAnyElementsOf(Set.of(idOfTextUnitOfLectureTwo, idOfExerciseUnitTextOfLectureTwo, idOfExerciseUnitModelingOfLectureTwo));

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteLectureUnit_asInstructor_shouldUpdateLearningGoal() throws Exception {
        request.delete("/api/lectures/" + idOfLectureTwo + "/lecture-units/" + idOfTextUnitOfLectureTwo, HttpStatus.OK);
        LearningGoal learningGoal = request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.OK, LearningGoal.class);
        assertThat(learningGoal.getLectureUnits().stream().map(DomainObject::getId)).containsAll(Set.of(idOfTextUnitOfLectureOne, idOfExerciseUnitTextOfLectureOne,
                idOfExerciseUnitModelingOfLectureOne, idOfExerciseUnitTextOfLectureTwo, idOfExerciseUnitModelingOfLectureTwo));
        assertThat(learningGoal.getLectureUnits().stream().map(DomainObject::getId)).doesNotContainAnyElementsOf(Set.of(idOfTextUnitOfLectureTwo));

    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getLearningGoalProgress_asStudent1_shouldReturnProgressTenOutOfTwenty() throws Exception {
        IndividualLearningGoalProgress individualLearningGoalProgress = request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal + "/individual-progress",
                HttpStatus.OK, IndividualLearningGoalProgress.class);
        assertThat(individualLearningGoalProgress.totalPointsAchievableByStudentsInLearningGoal).isEqualTo(20.0);
        assertThat(individualLearningGoalProgress.pointsAchievedByStudentInLearningGoal).isEqualTo(10.0);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getLearningGoalCourseProgress_asInstructorOne() throws Exception {
        CourseLearningGoalProgress courseLearningGoalProgress = request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal + "/course-progress", HttpStatus.OK,
                CourseLearningGoalProgress.class);
        assertThat(courseLearningGoalProgress.totalPointsAchievableByStudentsInLearningGoal).isEqualTo(20.0);
        assertThat(courseLearningGoalProgress.averagePointsAchievedByStudentInLearningGoal).isEqualTo(10.0);
    }

    @Test
    @WithMockUser(username = "student42", roles = "USER")
    public void getLearningGoalProgress_asStudentNotInCourse_shouldReturnProgressTenOutOfTwenty() throws Exception {
        request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal + "/individual-progress", HttpStatus.FORBIDDEN, IndividualLearningGoalProgress.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateLearningGoal_asInstructor_shouldUpdateLearningGoal() throws Exception {
        LearningGoal existingLearningGoal = learningGoalRepository.findByIdWithLectureUnitsBidirectional(idOfLearningGoal).get();
        LectureUnit textLectureUnit = lectureUnitRepository.findByIdWithLearningGoalsBidirectional(idOfTextUnitOfLectureOne).get();
        existingLearningGoal.setTitle("Updated");
        existingLearningGoal.removeLectureUnit(textLectureUnit);
        existingLearningGoal.setDescription("Updated Description");

        LearningGoal updatedLearningGoal = request.putWithResponseBody("/api/courses/" + idOfCourse + "/goals", existingLearningGoal, LearningGoal.class, HttpStatus.OK);

        assertThat(updatedLearningGoal.getTitle()).isEqualTo("Updated");
        assertThat(updatedLearningGoal.getDescription()).isEqualTo("Updated Description");
        assertThat(updatedLearningGoal.getLectureUnits().stream().map(DomainObject::getId).collect(Collectors.toSet()).contains(textLectureUnit.getId())).isFalse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createLearningGoal_asInstructor_shouldCreateLearningGoal() throws Exception {
        Course course = courseRepository.findWithEagerLearningGoalsById(idOfCourse).get();
        LearningGoal learningGoal = new LearningGoal();
        learningGoal.setTitle("FreshlyCreatedLearningGoal");
        learningGoal.setDescription("This is an example of a freshly created learning goal");
        learningGoal.setCourse(course);
        List<LectureUnit> allLectureUnits = lectureUnitRepository.findAll();
        Set<LectureUnit> connectedLectureUnits = new HashSet<>(allLectureUnits);
        learningGoal.setLectureUnits(connectedLectureUnits);

        var persistedLearningGoal = request.postWithResponseBody("/api/courses/" + idOfCourse + "/goals", learningGoal, LearningGoal.class, HttpStatus.CREATED);
        assertThat(persistedLearningGoal.getId()).isNotNull();
    }

    @Test
    @WithMockUser(username = "instructor42", roles = "INSTRUCTOR")
    public void createLearningGoal_instructorNotInCourse_shouldReturnForbidden() throws Exception {
        LearningGoal learningGoal = new LearningGoal();
        learningGoal.setTitle("Example Title");
        request.postWithResponseBody("/api/courses/" + idOfCourse + "/goals", learningGoal, LearningGoal.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createLearningGoal_goalWithNameAlreadyExists_shouldReturnBadRequest() throws Exception {
        Course course = courseRepository.findWithEagerLearningGoalsById(idOfCourse).get();
        LearningGoal existingLearningGoal = learningGoalRepository.findById(idOfLearningGoal).get();
        LearningGoal learningGoal = new LearningGoal();
        learningGoal.setTitle(existingLearningGoal.getTitle());
        learningGoal.setDescription("This is an example of a freshly created learning goal");
        learningGoal.setCourse(course);
        List<LectureUnit> allLectureUnits = lectureUnitRepository.findAll();
        Set<LectureUnit> connectedLectureUnits = new HashSet<>(allLectureUnits);
        learningGoal.setLectureUnits(connectedLectureUnits);
        request.postWithResponseBody("/api/courses/" + idOfCourse + "/goals", learningGoal, LearningGoal.class, HttpStatus.BAD_REQUEST);
    }

}
