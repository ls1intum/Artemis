package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.ModelFactory;

public class ExerciseUnitIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    ExerciseUnitRepository exerciseUnitRepository;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    LectureRepository lectureRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    UserRepository userRepo;

    @Autowired
    TextExerciseRepository textExerciseRepository;

    @Autowired
    ModelingExerciseRepository modelingExerciseRepository;

    @Autowired
    QuizExerciseRepository quizExerciseRepository;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    FileUploadExerciseRepository fileUploadExerciseRepository;

    Course course1;

    Lecture lecture1;

    TextExercise textExercise;

    ModelingExercise modelingExercise;

    ProgrammingExercise programmingExercise;

    FileUploadExercise fileUploadExercise;

    QuizExercise quizExercise;

    @BeforeEach
    public void initTestCase() throws Exception {
        this.database.addUsers(10, 10, 10);
        List<Course> courses = this.database.createCoursesWithExercisesAndLectures(true);
        this.course1 = this.courseRepository.findWithEagerExercisesAndLecturesById(courses.get(0).getId());
        this.lecture1 = this.course1.getLectures().stream().findFirst().get();

        this.textExercise = textExerciseRepository.findByCourseId(course1.getId()).stream().findFirst().get();
        this.fileUploadExercise = fileUploadExerciseRepository.findByCourseId(course1.getId()).stream().findFirst().get();
        this.programmingExercise = programmingExerciseRepository.findAllByCourse(course1).stream().findFirst().get();
        this.quizExercise = quizExerciseRepository.findByCourseId(course1.getId()).stream().findFirst().get();
        this.modelingExercise = modelingExerciseRepository.findByCourseId(course1.getId()).stream().findFirst().get();

        // Add users that are not in the course
        userRepo.save(ModelFactory.generateActivatedUser("student42"));
        userRepo.save(ModelFactory.generateActivatedUser("tutor42"));
        userRepo.save(ModelFactory.generateActivatedUser("instructor42"));
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    private void testAllPreAuthorize() throws Exception {
        ExerciseUnit exerciseUnit = new ExerciseUnit();
        request.post("/api/lectures/" + lecture1.getId() + "/exercise-units", exerciseUnit, HttpStatus.FORBIDDEN);
        request.getList("/api/lectures/" + lecture1.getId() + "/exercise-units", HttpStatus.FORBIDDEN, ExerciseUnit.class);
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
    public void createExerciseUnit_asInstructor_shouldCreateExerciseUnit() throws Exception {
        Set<Exercise> exercisesOfCourse = course1.getExercises();
        Set<ExerciseUnit> persistedExerciseUnits = new HashSet<>();

        for (Exercise exerciseOfCourse : exercisesOfCourse) {
            ExerciseUnit exerciseUnit = new ExerciseUnit();
            exerciseUnit.setExercise(exerciseOfCourse);
            ExerciseUnit persistedExerciseUnit = request.postWithResponseBody("/api/lectures/" + lecture1.getId() + "/exercise-units", exerciseUnit, ExerciseUnit.class,
                    HttpStatus.CREATED);
            persistedExerciseUnits.add(persistedExerciseUnit);
        }
        assertThat(persistedExerciseUnits.size()).isEqualTo(exercisesOfCourse.size());

        for (ExerciseUnit exerciseUnit : persistedExerciseUnits) {
            assertThat(exerciseUnit.getId()).isNotNull();
            assertThat(exerciseUnit.getExercise()).isNotNull();
            assertThat(exercisesOfCourse).contains(exerciseUnit.getExercise());
        }

        List<ExerciseUnit> exerciseUnitsOfLecture = request.getList("/api/lectures/" + lecture1.getId() + "/exercise-units", HttpStatus.OK, ExerciseUnit.class);
        persistedExerciseUnits.forEach(exerciseUnit -> {
            assertThat(exerciseUnitsOfLecture.contains(persistedExerciseUnits));
        });

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createExerciseUnit_withId_shouldReturnBadRequest() throws Exception {
        Exercise exercise = course1.getExercises().stream().findFirst().get();
        ExerciseUnit exerciseUnit = new ExerciseUnit();
        exerciseUnit.setExercise(exercise);
        exerciseUnit.setId(1L);
        ExerciseUnit persistedExerciseUnit = request.postWithResponseBody("/api/lectures/" + lecture1.getId() + "/exercise-units", exerciseUnit, ExerciseUnit.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "student1", roles = "INSTRUCTOR")
    public void createExerciseUnit_asStudent_shouldForbidden() throws Exception {
        Exercise exercise = course1.getExercises().stream().findFirst().get();
        ExerciseUnit exerciseUnit = new ExerciseUnit();
        exerciseUnit.setExercise(exercise);
        exerciseUnit.setId(1L);
        ExerciseUnit persistedExerciseUnit = request.postWithResponseBody("/api/lectures/" + lecture1.getId() + "/exercise-units", exerciseUnit, ExerciseUnit.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createExerciseUnit_notExistingLectureId_shouldReturnBadRequest() throws Exception {
        Exercise exercise = course1.getExercises().stream().findFirst().get();
        ExerciseUnit exerciseUnit = new ExerciseUnit();
        exerciseUnit.setExercise(exercise);
        ExerciseUnit persistedExerciseUnit = request.postWithResponseBody("/api/lectures/" + 0 + "/exercise-units", exerciseUnit, ExerciseUnit.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor42", roles = "INSTRUCTOR")
    public void createExerciseUnit_notInstructorInCourse_shouldReturnForbidden() throws Exception {
        Exercise exercise = course1.getExercises().stream().findFirst().get();
        ExerciseUnit exerciseUnit = new ExerciseUnit();
        exerciseUnit.setExercise(exercise);
        ExerciseUnit persistedExerciseUnit = request.postWithResponseBody("/api/lectures/" + lecture1.getId() + "/exercise-units", exerciseUnit, ExerciseUnit.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteExercise_exerciseConnectedWithExerciseUnit_shouldDeleteExerciseUnit() throws Exception {
        Set<Exercise> exercisesOfCourse = course1.getExercises();
        Set<ExerciseUnit> persistedExerciseUnits = new HashSet<>();

        for (Exercise exerciseOfCourse : exercisesOfCourse) {
            ExerciseUnit exerciseUnit = new ExerciseUnit();
            exerciseUnit.setExercise(exerciseOfCourse);
            ExerciseUnit persistedExerciseUnit = request.postWithResponseBody("/api/lectures/" + lecture1.getId() + "/exercise-units", exerciseUnit, ExerciseUnit.class,
                    HttpStatus.CREATED);
            persistedExerciseUnits.add(persistedExerciseUnit);
        }
        assertThat(persistedExerciseUnits.size()).isEqualTo(exercisesOfCourse.size());

        request.delete("/api/text-exercises/" + textExercise.getId(), HttpStatus.OK);
        request.delete("/api/modeling-exercises/" + modelingExercise.getId(), HttpStatus.OK);
        request.delete("/api/quiz-exercises/" + quizExercise.getId(), HttpStatus.OK);
        request.delete("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK);
        request.delete("/api/programming-exercises/" + programmingExercise.getId(), HttpStatus.OK);

        List<ExerciseUnit> exerciseUnitsOfLecture = request.getList("/api/lectures/" + lecture1.getId() + "/exercise-units", HttpStatus.OK, ExerciseUnit.class);
        assertThat(exerciseUnitsOfLecture).isEmpty();

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteExerciseUnit_exerciseConnectedWithExerciseUnit_shouldNOTDeleteExercise() throws Exception {
        Set<Exercise> exercisesOfCourse = course1.getExercises();
        Set<ExerciseUnit> persistedExerciseUnits = new HashSet<>();

        for (Exercise exerciseOfCourse : exercisesOfCourse) {
            ExerciseUnit exerciseUnit = new ExerciseUnit();
            exerciseUnit.setExercise(exerciseOfCourse);
            ExerciseUnit persistedExerciseUnit = request.postWithResponseBody("/api/lectures/" + lecture1.getId() + "/exercise-units", exerciseUnit, ExerciseUnit.class,
                    HttpStatus.CREATED);
            persistedExerciseUnits.add(persistedExerciseUnit);
        }
        assertThat(persistedExerciseUnits.size()).isEqualTo(exercisesOfCourse.size());

        for (ExerciseUnit exerciseUnit : persistedExerciseUnits) {
            request.delete("/api/lecture-units/" + exerciseUnit.getId(), HttpStatus.OK);
        }

        for (Exercise exercise : exercisesOfCourse) {
            request.get("/api/exercises/" + exercise.getId(), HttpStatus.OK, Exercise.class);
        }

    }

}
