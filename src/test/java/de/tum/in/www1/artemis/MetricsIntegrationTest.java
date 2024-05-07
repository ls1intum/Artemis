package de.tum.in.www1.artemis;

import static de.tum.in.www1.artemis.service.util.ZonedDateTimeUtil.toRelativeTime;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.web.rest.dto.metrics.ExerciseInformationDTO;
import de.tum.in.www1.artemis.web.rest.dto.metrics.StudentMetricsDTO;

class MetricsIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "metricsintegration";

    private Course course;

    private static final String STUDENT_OF_COURSE = TEST_PREFIX + "student1";

    private static final String TUTOR_OF_COURSE = TEST_PREFIX + "tutor1";

    private static final String EDITOR_OF_COURSE = TEST_PREFIX + "editor1";

    private static final String INSTRUCTOR_OF_COURSE = TEST_PREFIX + "instructor1";

    @BeforeEach
    void setupTestScenario() {
        userUtilService.addUsers(TEST_PREFIX, 3, 1, 1, 1);

        course = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, true);

        userUtilService.createAndSaveUser(TEST_PREFIX + "user1337");
    }

    @Nested
    class PreAuthorize {

        @Test
        @WithMockUser(username = TEST_PREFIX + "user1337", roles = "INSTRUCTOR")
        void shouldReturnForbiddenForUserNotInCourse() throws Exception {
            request.get("/api/metrics/course/" + course.getId() + "/student", HttpStatus.FORBIDDEN, StudentMetricsDTO.class);
        }
    }

    @Nested
    class ExerciseMetrics {

        @Test
        @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
        void shouldReturnExerciseInformation() throws Exception {
            final var result = request.get("/api/metrics/course/" + course.getId() + "/student", HttpStatus.OK, StudentMetricsDTO.class);
            assertThat(result).isNotNull();
            assertThat(result.exerciseMetrics()).isNotNull();
            final var exerciseInformation = result.exerciseMetrics().exerciseInformation();

            final var exercises = exerciseRepository.findAllExercisesByCourseId(course.getId());
            final var expectedDTOs = exercises.stream().map(ExerciseInformationDTO::of).collect(Collectors.toSet());

            assertThat(exerciseInformation.values()).containsExactlyInAnyOrderElementsOf(expectedDTOs);
            assertThat(exerciseInformation).allSatisfy((id, dto) -> assertThat(id).isEqualTo(dto.id()));
        }

        @Test
        @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
        void shouldReturnAverageScores() throws Exception {
            final var result = request.get("/api/metrics/course/" + course.getId() + "/student", HttpStatus.OK, StudentMetricsDTO.class);
            assertThat(result).isNotNull();
            assertThat(result.exerciseMetrics()).isNotNull();
            final var averageScores = result.exerciseMetrics().averageScore();

            final var exercises = exerciseRepository.findAllExercisesByCourseId(course.getId());

            final var expectedMap = exercises.stream().map(Exercise::getId).collect(
                    Collectors.toMap(Function.identity(), id -> resultRepository.findAllByParticipationExerciseId(id).stream().mapToDouble(Result::getScore).average().orElse(0)));

            assertThat(averageScores).isEqualTo(expectedMap);
        }

        @Test
        @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
        void shouldReturnAverageLatestSubmission() throws Exception {
            final var result = request.get("/api/metrics/course/" + course.getId() + "/student", HttpStatus.OK, StudentMetricsDTO.class);
            assertThat(result).isNotNull();
            assertThat(result.exerciseMetrics()).isNotNull();
            final var averageLatestSubmissions = result.exerciseMetrics().averageLatestSubmission();

            final var exercises = exerciseRepository.findAllExercisesByCourseId(course.getId()).stream()
                    .map(exercise -> exerciseRepository.findWithEagerStudentParticipationsStudentAndSubmissionsById(exercise.getId()).orElseThrow());

            final var expectedMap = exercises.collect(Collectors.toMap(Exercise::getId, exercise -> {
                var latestSubmissions = exercise.getStudentParticipations().stream()
                        .map(participation -> participation.getSubmissions().stream().max(Comparator.comparing(Submission::getSubmissionDate)).orElseThrow());
                return latestSubmissions.mapToDouble(submission -> toRelativeTime(exercise.getReleaseDate(), exercise.getDueDate(), submission.getSubmissionDate())).average()
                        .orElseThrow();
            }));

            assertThat(averageLatestSubmissions).isEqualTo(expectedMap);
        }

        @Test
        @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
        void shouldReturnLatestSubmission() throws Exception {
            final var result = request.get("/api/metrics/course/" + course.getId() + "/student", HttpStatus.OK, StudentMetricsDTO.class);
            assertThat(result).isNotNull();
            assertThat(result.exerciseMetrics()).isNotNull();
            final var averageLatestSubmissions = result.exerciseMetrics().latestSubmission();

            final var exercises = exerciseRepository.findAllExercisesByCourseId(course.getId()).stream()
                    .map(exercise -> exerciseRepository.findWithEagerStudentParticipationsStudentAndSubmissionsById(exercise.getId()).orElseThrow());

            final var expectedMap = exercises.collect(Collectors.toMap(Exercise::getId, exercise -> exercise.getStudentParticipations().stream()
                    .flatMap(participation -> participation.getSubmissions().stream()).map(Submission::getSubmissionDate).max(Comparator.naturalOrder()).orElseThrow()));

            // isEqual is not possible due to the need of calling isEqual on the submission dates
            assertThat(averageLatestSubmissions).hasSameSizeAs(expectedMap);
            assertThat(averageLatestSubmissions).allSatisfy((id, latestSubmission) -> expectedMap.get(id).isEqual(latestSubmission));
        }
    }
}
