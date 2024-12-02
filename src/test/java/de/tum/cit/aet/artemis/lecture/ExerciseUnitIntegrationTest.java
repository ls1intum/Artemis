package de.tum.cit.aet.artemis.lecture;

import static de.tum.cit.aet.artemis.core.util.RequestUtilService.deleteProgrammingExerciseParamsFalse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;

class ExerciseUnitIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "exerciseunitintegration";

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private ModelingExerciseRepository modelingExerciseRepository;

    @Autowired
    private QuizExerciseTestRepository quizExerciseRepository;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    private FileUploadExerciseRepository fileUploadExerciseRepository;

    private Course course1;

    private Lecture lecture1;

    private TextExercise textExercise;

    private ModelingExercise modelingExercise;

    private ProgrammingExercise programmingExercise;

    private FileUploadExercise fileUploadExercise;

    private QuizExercise quizExercise;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 2, 0, 1);
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, 2);
        this.course1 = this.courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(courses.getFirst().getId());
        this.lecture1 = this.course1.getLectures().stream().findFirst().orElseThrow();

        this.textExercise = textExerciseRepository.findByCourseIdWithCategories(course1.getId()).stream().findFirst().orElseThrow();
        this.fileUploadExercise = fileUploadExerciseRepository.findByCourseIdWithCategories(course1.getId()).stream().findFirst().orElseThrow();
        this.programmingExercise = programmingExerciseRepository.findAllProgrammingExercisesInCourseOrInExamsOfCourse(course1).stream().findFirst().orElseThrow();
        this.quizExercise = quizExerciseRepository.findByCourseIdWithCategories(course1.getId()).stream().findFirst().orElseThrow();
        this.modelingExercise = modelingExerciseRepository.findByCourseIdWithCategories(course1.getId()).stream().findFirst().orElseThrow();

        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");
    }

    private void testAllPreAuthorize() throws Exception {
        ExerciseUnit exerciseUnit = new ExerciseUnit();
        request.post("/api/lectures/" + lecture1.getId() + "/exercise-units", exerciseUnit, HttpStatus.FORBIDDEN);
        request.getList("/api/lectures/" + lecture1.getId() + "/exercise-units", HttpStatus.FORBIDDEN, ExerciseUnit.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createExerciseUnit_asInstructor_shouldCreateExerciseUnit() throws Exception {
        Set<Exercise> exercisesOfCourse = course1.getExercises();
        Set<ExerciseUnit> persistedExerciseUnits = new HashSet<>();

        for (Exercise exerciseOfCourse : exercisesOfCourse) {
            ExerciseUnit exerciseUnit = new ExerciseUnit();
            exerciseUnit.setExercise(exerciseOfCourse);
            ExerciseUnit persistedExerciseUnit = request.postWithResponseBody("/api/lectures/" + lecture1.getId() + "/exercise-units", exerciseUnit, ExerciseUnit.class,
                    HttpStatus.CREATED);
            persistedExerciseUnits.add(persistedExerciseUnit);
        }
        assertThat(persistedExerciseUnits).hasSameSizeAs(exercisesOfCourse);

        for (ExerciseUnit exerciseUnit : persistedExerciseUnits) {
            assertThat(exerciseUnit.getId()).isNotNull();
            assertThat(exerciseUnit.getExercise()).isNotNull();
            assertThat(exercisesOfCourse).contains(exerciseUnit.getExercise());
        }

        List<ExerciseUnit> exerciseUnitsOfLecture = request.getList("/api/lectures/" + lecture1.getId() + "/exercise-units", HttpStatus.OK, ExerciseUnit.class);
        assertThat(exerciseUnitsOfLecture).containsAll(persistedExerciseUnits);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createExerciseUnit_withId_shouldReturnBadRequest() throws Exception {
        Exercise exercise = course1.getExercises().stream().findFirst().orElseThrow();
        ExerciseUnit exerciseUnit = new ExerciseUnit();
        exerciseUnit.setExercise(exercise);
        exerciseUnit.setId(1L);
        request.postWithResponseBody("/api/lectures/" + lecture1.getId() + "/exercise-units", exerciseUnit, ExerciseUnit.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "INSTRUCTOR")
    void createExerciseUnit_asStudent_shouldForbidden() throws Exception {
        Exercise exercise = course1.getExercises().stream().findFirst().orElseThrow();
        ExerciseUnit exerciseUnit = new ExerciseUnit();
        exerciseUnit.setExercise(exercise);
        exerciseUnit.setId(1L);
        request.postWithResponseBody("/api/lectures/" + lecture1.getId() + "/exercise-units", exerciseUnit, ExerciseUnit.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createExerciseUnit_notExistingLectureId_shouldReturnNotFound() throws Exception {
        Exercise exercise = course1.getExercises().stream().findFirst().orElseThrow();
        ExerciseUnit exerciseUnit = new ExerciseUnit();
        exerciseUnit.setExercise(exercise);
        request.postWithResponseBody("/api/lectures/" + 0 + "/exercise-units", exerciseUnit, ExerciseUnit.class, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void createExerciseUnit_notInstructorInCourse_shouldReturnForbidden() throws Exception {
        Exercise exercise = course1.getExercises().stream().findFirst().orElseThrow();
        ExerciseUnit exerciseUnit = new ExerciseUnit();
        exerciseUnit.setExercise(exercise);
        request.postWithResponseBody("/api/lectures/" + lecture1.getId() + "/exercise-units", exerciseUnit, ExerciseUnit.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteExercise_exerciseConnectedWithExerciseUnit_shouldDeleteExerciseUnit() throws Exception {
        Set<Exercise> exercisesOfCourse = course1.getExercises();
        Set<ExerciseUnit> persistedExerciseUnits = new HashSet<>();

        for (Exercise exerciseOfCourse : exercisesOfCourse) {
            ExerciseUnit exerciseUnit = new ExerciseUnit();
            exerciseUnit.setExercise(exerciseOfCourse);
            ExerciseUnit persistedExerciseUnit = request.postWithResponseBody("/api/lectures/" + lecture1.getId() + "/exercise-units", exerciseUnit, ExerciseUnit.class,
                    HttpStatus.CREATED);
            persistedExerciseUnits.add(persistedExerciseUnit);
        }
        assertThat(persistedExerciseUnits).hasSameSizeAs(exercisesOfCourse);

        request.delete("/api/text-exercises/" + textExercise.getId(), HttpStatus.OK);
        request.delete("/api/modeling-exercises/" + modelingExercise.getId(), HttpStatus.OK);
        request.delete("/api/quiz-exercises/" + quizExercise.getId(), HttpStatus.OK);
        request.delete("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK);
        request.delete("/api/programming-exercises/" + programmingExercise.getId(), HttpStatus.OK, deleteProgrammingExerciseParamsFalse());

        List<ExerciseUnit> exerciseUnitsOfLecture = request.getList("/api/lectures/" + lecture1.getId() + "/exercise-units", HttpStatus.OK, ExerciseUnit.class);
        assertThat(exerciseUnitsOfLecture).isEmpty();

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteExerciseUnit_exerciseConnectedWithExerciseUnit_shouldNOTDeleteExercise() throws Exception {
        Set<Exercise> exercisesOfCourse = course1.getExercises();
        Set<ExerciseUnit> persistedExerciseUnits = new HashSet<>();

        for (Exercise exerciseOfCourse : exercisesOfCourse) {
            ExerciseUnit exerciseUnit = new ExerciseUnit();
            exerciseUnit.setExercise(exerciseOfCourse);
            ExerciseUnit persistedExerciseUnit = request.postWithResponseBody("/api/lectures/" + lecture1.getId() + "/exercise-units", exerciseUnit, ExerciseUnit.class,
                    HttpStatus.CREATED);
            persistedExerciseUnits.add(persistedExerciseUnit);
        }
        assertThat(persistedExerciseUnits).hasSameSizeAs(exercisesOfCourse);

        for (ExerciseUnit exerciseUnit : persistedExerciseUnits) {
            request.delete("/api/lectures/" + lecture1.getId() + "/lecture-units/" + exerciseUnit.getId(), HttpStatus.OK);
        }

        for (Exercise exercise : exercisesOfCourse) {
            request.get("/api/exercises/" + exercise.getId(), HttpStatus.OK, Exercise.class);
        }

        verify(competencyProgressApi, never()).updateProgressForUpdatedLearningObjectAsync(any(), any());
    }

}
