package de.tum.in.www1.artemis.lecture;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.ModelFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ExerciseUnitIntegrationTest extends AbstractSpringDevelopmentTest {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserRepository userRepo;

    private Course course1;

    private Lecture lecture1;

    @BeforeEach
    public void initTestCase() throws Exception {
        this.database.addUsers(10, 10, 0, 10);
        List<Course> courses = this.database.createCoursesWithExercisesAndLectures(true);
        this.course1 = this.courseRepository.findByIdWithExercisesAndLecturesElseThrow(courses.get(0).getId());
        this.lecture1 = this.course1.getLectures().stream().findFirst().get();

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
        assertThat(exerciseUnitsOfLecture).containsAll(persistedExerciseUnits);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createExerciseUnit_withId_shouldReturnBadRequest() throws Exception {
        Exercise exercise = course1.getExercises().stream().findFirst().get();
        ExerciseUnit exerciseUnit = new ExerciseUnit();
        exerciseUnit.setExercise(exercise);
        exerciseUnit.setId(1L);
        request.postWithResponseBody("/api/lectures/" + lecture1.getId() + "/exercise-units", exerciseUnit, ExerciseUnit.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "student1", roles = "INSTRUCTOR")
    public void createExerciseUnit_asStudent_shouldForbidden() throws Exception {
        Exercise exercise = course1.getExercises().stream().findFirst().get();
        ExerciseUnit exerciseUnit = new ExerciseUnit();
        exerciseUnit.setExercise(exercise);
        exerciseUnit.setId(1L);
        request.postWithResponseBody("/api/lectures/" + lecture1.getId() + "/exercise-units", exerciseUnit, ExerciseUnit.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createExerciseUnit_notExistingLectureId_shouldReturnBadRequest() throws Exception {
        Exercise exercise = course1.getExercises().stream().findFirst().get();
        ExerciseUnit exerciseUnit = new ExerciseUnit();
        exerciseUnit.setExercise(exercise);
        request.postWithResponseBody("/api/lectures/" + 0 + "/exercise-units", exerciseUnit, ExerciseUnit.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor42", roles = "INSTRUCTOR")
    public void createExerciseUnit_notInstructorInCourse_shouldReturnForbidden() throws Exception {
        Exercise exercise = course1.getExercises().stream().findFirst().get();
        ExerciseUnit exerciseUnit = new ExerciseUnit();
        exerciseUnit.setExercise(exercise);
        request.postWithResponseBody("/api/lectures/" + lecture1.getId() + "/exercise-units", exerciseUnit, ExerciseUnit.class,
                HttpStatus.FORBIDDEN);
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
             request.delete("/api/lectures/" + lecture1.getId() + "/lecture-units/" + exerciseUnit.getId(), HttpStatus.OK);
         }
    }

}
