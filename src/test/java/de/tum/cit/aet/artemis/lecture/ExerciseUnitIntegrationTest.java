package de.tum.cit.aet.artemis.lecture;

import static de.tum.cit.aet.artemis.core.util.RequestUtilService.deleteProgrammingExerciseParamsFalse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.dto.ExerciseUnitDTO;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.test_repository.ModelingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class ExerciseUnitIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "exerciseunitintegration";

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ModelingExerciseTestRepository modelingExerciseRepository;

    @Autowired
    private QuizExerciseTestRepository quizExerciseRepository;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    private FileUploadExerciseRepository fileUploadExerciseRepository;

    private Course course1;

    private Course course2;

    private Lecture lecture1;

    private TextExercise textExercise;

    private ModelingExercise modelingExercise;

    private ProgrammingExercise programmingExercise;

    private FileUploadExercise fileUploadExercise;

    private QuizExercise quizExercise;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 2, 2, 0, 1);
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, 2);
        this.course1 = this.courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(courses.getFirst().getId());
        this.course2 = this.courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(courses.get(1).getId());
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
        request.post("/api/lecture/lectures/" + lecture1.getId() + "/exercise-units", exerciseUnitDtoFor(textExercise), HttpStatus.FORBIDDEN);
        request.getList("/api/lecture/lectures/" + lecture1.getId() + "/exercise-units", HttpStatus.FORBIDDEN, ExerciseUnitDTO.class);
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
        Set<ExerciseUnitDTO> persistedExerciseUnits = new HashSet<>();

        for (Exercise exerciseOfCourse : exercisesOfCourse) {
            ExerciseUnitDTO persistedExerciseUnit = request.postWithResponseBody("/api/lecture/lectures/" + lecture1.getId() + "/exercise-units",
                    exerciseUnitDtoFor(exerciseOfCourse), ExerciseUnitDTO.class, HttpStatus.CREATED);
            persistedExerciseUnits.add(persistedExerciseUnit);
        }
        assertThat(persistedExerciseUnits).hasSameSizeAs(exercisesOfCourse);
        Map<Long, Exercise> exerciseById = exercisesOfCourse.stream().collect(Collectors.toMap(Exercise::getId, exercise -> exercise));

        for (ExerciseUnitDTO exerciseUnit : persistedExerciseUnits) {
            assertThat(exerciseUnit.id()).isNotNull();
            assertThat(exerciseUnit.type()).isEqualTo("exercise");
            assertThat(exerciseUnit.exercise()).isNotNull();
            Exercise exercise = exerciseById.get(exerciseUnit.exercise().id());
            assertThat(exercise).isNotNull();
            assertThat(exerciseUnit.name()).isEqualTo(exercise.getTitle());
            if (exercise.getReleaseDate() == null) {
                assertThat(exerciseUnit.releaseDate()).isNull();
            }
            else {
                assertThat(exerciseUnit.releaseDate().toInstant()).isEqualTo(exercise.getReleaseDate().toInstant());
            }
            assertThat(exerciseUnit.exercise().type()).isEqualTo(exercise.getExerciseType());
        }

        List<ExerciseUnitDTO> exerciseUnitsOfLecture = request.getList("/api/lecture/lectures/" + lecture1.getId() + "/exercise-units", HttpStatus.OK, ExerciseUnitDTO.class);
        // Each exercise must yield exactly one unit; a superset check (containsAll) would not catch duplicate persistence.
        assertThat(exerciseUnitsOfLecture).containsExactlyInAnyOrderElementsOf(persistedExerciseUnits);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createExerciseUnit_withId_shouldReturnBadRequest() throws Exception {
        Exercise exercise = course1.getExercises().stream().findFirst().orElseThrow();
        request.postWithResponseBody("/api/lecture/lectures/" + lecture1.getId() + "/exercise-units", exerciseUnitDtoWithIdFor(1L, exercise), ExerciseUnitDTO.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createExerciseUnit_exerciseFromDifferentCourse_shouldReturnBadRequest() throws Exception {
        TextExercise exercise = textExerciseUtilService.createSampleTextExercise(course2);
        request.postWithResponseBody("/api/lecture/lectures/" + lecture1.getId() + "/exercise-units", exerciseUnitDtoFor(exercise), ExerciseUnitDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createExerciseUnit_missingExercise_shouldReturnBadRequest() throws Exception {
        ExerciseUnitDTO exerciseUnitDTO = new ExerciseUnitDTO(null, null, null, false, false, null, null, null);
        request.postWithResponseBody("/api/lecture/lectures/" + lecture1.getId() + "/exercise-units", exerciseUnitDTO, ExerciseUnitDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createExerciseUnit_missingExerciseId_shouldReturnBadRequest() throws Exception {
        ExerciseUnitDTO exerciseUnitDTO = new ExerciseUnitDTO(null, null, null, false, false, null, new ExerciseUnitDTO.ExerciseReferenceDTO(null, null, null, null), null);
        request.postWithResponseBody("/api/lecture/lectures/" + lecture1.getId() + "/exercise-units", exerciseUnitDTO, ExerciseUnitDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "INSTRUCTOR")
    void createExerciseUnit_asStudent_shouldForbidden() throws Exception {
        Exercise exercise = course1.getExercises().stream().findFirst().orElseThrow();
        request.postWithResponseBody("/api/lecture/lectures/" + lecture1.getId() + "/exercise-units", exerciseUnitDtoWithIdFor(1L, exercise), ExerciseUnitDTO.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createExerciseUnit_notExistingLectureId_shouldReturnForbidden() throws Exception {
        Exercise exercise = course1.getExercises().stream().findFirst().orElseThrow();
        request.postWithResponseBody("/api/lecture/lectures/" + 0 + "/exercise-units", exerciseUnitDtoFor(exercise), ExerciseUnitDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void createExerciseUnit_notInstructorInCourse_shouldReturnForbidden() throws Exception {
        Exercise exercise = course1.getExercises().stream().findFirst().orElseThrow();
        request.postWithResponseBody("/api/lecture/lectures/" + lecture1.getId() + "/exercise-units", exerciseUnitDtoFor(exercise), ExerciseUnitDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteExercise_exerciseConnectedWithExerciseUnit_shouldDeleteExerciseUnit() throws Exception {
        Set<Exercise> exercisesOfCourse = course1.getExercises();
        Set<ExerciseUnitDTO> persistedExerciseUnits = new HashSet<>();

        for (Exercise exerciseOfCourse : exercisesOfCourse) {
            ExerciseUnitDTO persistedExerciseUnit = request.postWithResponseBody("/api/lecture/lectures/" + lecture1.getId() + "/exercise-units",
                    exerciseUnitDtoFor(exerciseOfCourse), ExerciseUnitDTO.class, HttpStatus.CREATED);
            persistedExerciseUnits.add(persistedExerciseUnit);
        }
        assertThat(persistedExerciseUnits).hasSameSizeAs(exercisesOfCourse);

        request.delete("/api/text/text-exercises/" + textExercise.getId(), HttpStatus.OK);
        request.delete("/api/modeling/modeling-exercises/" + modelingExercise.getId(), HttpStatus.OK);
        request.delete("/api/quiz/quiz-exercises/" + quizExercise.getId(), HttpStatus.OK);
        request.delete("/api/fileupload/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK);
        request.delete("/api/programming/programming-exercises/" + programmingExercise.getId(), HttpStatus.OK, deleteProgrammingExerciseParamsFalse());

        List<ExerciseUnitDTO> exerciseUnitsOfLecture = request.getList("/api/lecture/lectures/" + lecture1.getId() + "/exercise-units", HttpStatus.OK, ExerciseUnitDTO.class);
        assertThat(exerciseUnitsOfLecture).isEmpty();

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteExerciseUnit_exerciseConnectedWithExerciseUnit_shouldNOTDeleteExercise() throws Exception {
        Set<Exercise> exercisesOfCourse = course1.getExercises();
        Set<ExerciseUnitDTO> persistedExerciseUnits = new HashSet<>();

        for (Exercise exerciseOfCourse : exercisesOfCourse) {
            ExerciseUnitDTO persistedExerciseUnit = request.postWithResponseBody("/api/lecture/lectures/" + lecture1.getId() + "/exercise-units",
                    exerciseUnitDtoFor(exerciseOfCourse), ExerciseUnitDTO.class, HttpStatus.CREATED);
            persistedExerciseUnits.add(persistedExerciseUnit);
        }
        assertThat(persistedExerciseUnits).hasSameSizeAs(exercisesOfCourse);

        for (ExerciseUnitDTO exerciseUnit : persistedExerciseUnits) {
            request.delete("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + exerciseUnit.id(), HttpStatus.OK);
        }

        for (Exercise exercise : exercisesOfCourse) {
            request.get("/api/exercise/exercises/" + exercise.getId(), HttpStatus.OK, Exercise.class);
        }

        verify(competencyProgressApi, never()).updateProgressForUpdatedLearningObjectAsync(any(), any());
    }

    private static ExerciseUnitDTO exerciseUnitDtoFor(Exercise exercise) {
        return exerciseUnitDtoWithIdFor(null, exercise);
    }

    private static ExerciseUnitDTO exerciseUnitDtoWithIdFor(Long id, Exercise exercise) {
        return new ExerciseUnitDTO(id, null, null, false, false, null, new ExerciseUnitDTO.ExerciseReferenceDTO(exercise.getId(), null, null, null), null);
    }

}
