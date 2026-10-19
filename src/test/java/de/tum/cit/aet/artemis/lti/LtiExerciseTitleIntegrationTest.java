package de.tum.cit.aet.artemis.lti;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseTitleDTO;
import de.tum.cit.aet.artemis.lti.domain.LtiResourceLaunch;
import de.tum.cit.aet.artemis.lti.dto.Lti13LaunchRequest;
import de.tum.cit.aet.artemis.lti.repository.Lti13ResourceLaunchRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class LtiExerciseTitleIntegrationTest extends AbstractLtiIntegrationTest {

    private static final String TEST_PREFIX = "ltiexercisetitle";

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private Lti13ResourceLaunchRepository ltiResourceLaunchRepository;

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void studentInOnlineCourseSeesOnlyReleasedExerciseLaunchedByThem() throws Exception {
        Course course = createEnrolledCourse(true);
        ZonedDateTime now = ZonedDateTime.now();
        TextExercise launchedByStudent = createExercise(course, "launched by student", now.minusDays(2));
        TextExercise launchedByOtherStudent = createExercise(course, "launched by other student", now.minusDays(2));
        TextExercise unreleasedWithLaunch = createExercise(course, "unreleased with launch", now.plusDays(2));

        saveLaunch(launchedByStudent, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        saveLaunch(launchedByOtherStudent, userUtilService.getUserByLogin(TEST_PREFIX + "student2"));
        saveLaunch(unreleasedWithLaunch, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));

        Set<ExerciseTitleDTO> titles = getExerciseTitles(course);

        assertThat(titles).as("online-course titles are narrowed by release date and the requesting user's resource launch")
                .containsExactly(new ExerciseTitleDTO(launchedByStudent.getId(), launchedByStudent.getTitle(), ExerciseType.TEXT));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void tutorInOnlineCourseSeesReleasedAndUnreleasedExercisesWithoutLaunches() throws Exception {
        Course course = createEnrolledCourse(true);
        ZonedDateTime now = ZonedDateTime.now();
        TextExercise released = createExercise(course, "released", now.minusDays(2));
        TextExercise unreleased = createExercise(course, "unreleased", now.plusDays(2));

        Set<ExerciseTitleDTO> titles = getExerciseTitles(course);

        assertThat(titles).as("teaching assistants can select every course exercise without an LTI launch").containsExactlyInAnyOrder(
                new ExerciseTitleDTO(released.getId(), released.getTitle(), ExerciseType.TEXT), new ExerciseTitleDTO(unreleased.getId(), unreleased.getTitle(), ExerciseType.TEXT));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void studentInRegularCourseSeesReleasedExerciseWithoutLaunch() throws Exception {
        Course course = createEnrolledCourse(false);
        ZonedDateTime now = ZonedDateTime.now();
        TextExercise released = createExercise(course, "released", now.minusDays(2));
        createExercise(course, "unreleased", now.plusDays(2));

        Set<ExerciseTitleDTO> titles = getExerciseTitles(course);

        assertThat(titles).as("regular courses apply release visibility without requiring an LTI launch")
                .containsExactly(new ExerciseTitleDTO(released.getId(), released.getTitle(), ExerciseType.TEXT));
    }

    private Course createEnrolledCourse(boolean onlineCourse) {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        Course course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        course.setOnlineCourse(onlineCourse);
        return courseRepository.save(course);
    }

    private TextExercise createExercise(Course course, String title, ZonedDateTime releaseDate) {
        TextExercise exercise = textExerciseUtilService.createIndividualTextExercise(course, releaseDate, releaseDate.plusDays(5), releaseDate.plusDays(6));
        exercise.setTitle(title);
        return exerciseRepository.save(exercise);
    }

    private void saveLaunch(TextExercise exercise, User user) {
        Lti13LaunchRequest request = new Lti13LaunchRequest("issuer", user.getLogin(), "deployment", "resource-" + exercise.getId(), "target", null, "client");
        LtiResourceLaunch launch = LtiResourceLaunch.from(request);
        launch.setExercise(exercise);
        launch.setUser(user);
        ltiResourceLaunchRepository.save(launch);
    }

    private Set<ExerciseTitleDTO> getExerciseTitles(Course course) throws Exception {
        return request.getSet("/api/exercise/courses/" + course.getId() + "/exercise-titles", HttpStatus.OK, ExerciseTitleDTO.class);
    }
}
