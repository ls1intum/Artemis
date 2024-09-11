package de.tum.cit.aet.artemis;

import static de.tum.cit.aet.artemis.core.util.TimeUtil.toRelativeTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreScheduleService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseMetricsRepository;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseInformationDTO;
import de.tum.cit.aet.artemis.atlas.dto.metrics.ResourceTimestampDTO;
import de.tum.cit.aet.artemis.atlas.dto.metrics.StudentMetricsDTO;

class MetricsIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "metricsintegration";

    @Autowired
    private ExerciseMetricsRepository exerciseMetricsRepository;

    private Course course;

    private Course courseWithTestRuns;

    private static final String STUDENT_OF_COURSE = TEST_PREFIX + "student1";

    @BeforeEach
    void setupTestScenario() {
        // Prevents the ParticipantScoreScheduleService from scheduling tasks related to prior results
        ReflectionTestUtils.setField(participantScoreScheduleService, "lastScheduledRun", Optional.of(Instant.now()));
        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 100;
        participantScoreScheduleService.activate();

        userUtilService.addUsers(TEST_PREFIX, 3, 1, 1, 1);

        course = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, true);
        courseWithTestRuns = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResultsAndTestRunsAndTwoUsers(TEST_PREFIX, true);

        userUtilService.createAndSaveUser(TEST_PREFIX + "user1337");
    }

    @AfterEach
    void cleanup() {
        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 500;
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

        @Disabled // TODO: reduce jacoco missing by one after enabled
        @Test
        @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
        void shouldReturnAverageScores() throws Exception {
            // Wait for the scheduler to execute its task
            participantScoreScheduleService.executeScheduledTasks();
            await().until(() -> participantScoreScheduleService.isIdle());

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
            final var latestSubmissions = result.exerciseMetrics().latestSubmission();

            final var exercises = exerciseRepository.findAllExercisesByCourseId(course.getId()).stream()
                    .map(exercise -> exerciseRepository.findWithEagerStudentParticipationsStudentAndSubmissionsById(exercise.getId()).orElseThrow());

            final var expectedMap = exercises.collect(Collectors.toMap(Exercise::getId, exercise -> {
                final var absoluteTime = exercise.getStudentParticipations().stream().flatMap(participation -> participation.getSubmissions().stream())
                        .map(Submission::getSubmissionDate).max(Comparator.naturalOrder()).orElseThrow();
                return toRelativeTime(exercise.getReleaseDate(), exercise.getDueDate(), absoluteTime);
            }));

            assertThat(latestSubmissions).isEqualTo(expectedMap);
        }

        @Test
        @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
        void shouldFindLatestSubmissionDates() throws Exception {
            Set<Long> exerciseIds = new HashSet<Long>();
            final var exercises = exerciseRepository.findAllExercisesByCourseId(courseWithTestRuns.getId()).stream()
                    .map(exercise -> exerciseRepository.findWithEagerStudentParticipationsStudentAndSubmissionsById(exercise.getId()).orElseThrow());

            Set<ResourceTimestampDTO> expectedSet = exercises.map(exercise -> {
                exerciseIds.add(exercise.getId());

                final var latestSubmissionDate = exercise.getStudentParticipations().stream().filter(participation -> !participation.isTestRun())
                        .flatMap(participation -> participation.getSubmissions().stream()).map(Submission::getSubmissionDate).max(Comparator.naturalOrder());

                return latestSubmissionDate.map(date -> new ResourceTimestampDTO(exercise.getId(), date));
            }).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toSet());

            Set<ResourceTimestampDTO> result = exerciseMetricsRepository.findLatestSubmissionDates(exerciseIds);
            assertThat(result).isEqualTo(expectedSet);
        }

        @Test
        @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
        void shouldFindLatestSubmissionDatesByUser() throws Exception {
            Set<Long> exerciseIds = new HashSet<Long>();
            final var exercises = exerciseRepository.findAllExercisesByCourseId(courseWithTestRuns.getId()).stream()
                    .map(exercise -> exerciseRepository.findWithEagerStudentParticipationsStudentAndSubmissionsById(exercise.getId()).orElseThrow());
            final var userID = userUtilService.getUserByLogin(TEST_PREFIX + "student1").getId();

            Set<ResourceTimestampDTO> expectedSet = exercises.flatMap(exercise -> {
                exerciseIds.add(exercise.getId());
                final var latestSubmissionDate = exercise.getStudentParticipations().stream().filter(participation -> !participation.isTestRun())
                        .filter(participation -> participation.getStudent().map(student -> student.getId().equals(userID)).orElse(false))
                        .flatMap(participation -> participation.getSubmissions().stream()).map(Submission::getSubmissionDate).max(Comparator.naturalOrder());

                return latestSubmissionDate.map(date -> new ResourceTimestampDTO(exercise.getId(), date)).stream();
            }).collect(Collectors.toSet());

            Set<ResourceTimestampDTO> result = exerciseMetricsRepository.findLatestSubmissionDatesForUser(exerciseIds, userID);
            assertThat(result).isEqualTo(expectedSet);
        }
    }
}
