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
import de.tum.cit.aet.artemis.atlas.dto.metrics.ResourceTimestampDTO;
import de.tum.cit.aet.artemis.atlas.dto.metrics.StudentMetricsDTO;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyMetricsRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseInformationDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseMetricsRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitMetricsRepository;
import de.tum.cit.aet.artemis.team.TeamFactory;

class MetricsIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "metricsintegration";

    @Autowired
    private ExerciseMetricsRepository exerciseMetricsRepository;

    @Autowired
    private CompetencyMetricsRepository competencyMetricsRepository;

    @Autowired
    private LectureUnitMetricsRepository lectureUnitMetricsRepository;

    private Course course;

    private Course courseWithTestRuns;

    private Course courseWithCompetencies;

    private long userID;

    private static final String STUDENT_OF_COURSE = TEST_PREFIX + "student1";

    @BeforeEach
    void setupTestScenario() throws Exception {
        // Prevents the ParticipantScoreScheduleService from scheduling tasks related to prior results
        ReflectionTestUtils.setField(participantScoreScheduleService, "lastScheduledRun", Optional.of(Instant.now()));
        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 100;
        participantScoreScheduleService.activate();

        userUtilService.addUsers(TEST_PREFIX, 3, 1, 1, 1);

        course = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResultsAndTeamsAndScores(TEST_PREFIX, true);
        courseWithTestRuns = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResultsAndTestRunsAndTwoUsers(TEST_PREFIX, true);
        courseWithCompetencies = courseUtilService.createCoursesWithExercisesAndLecturesAndLectureUnitsAndCompetencies(TEST_PREFIX, true, true, 1).getFirst();

        userUtilService.createAndSaveUser(TEST_PREFIX + "user1337");
        userID = userUtilService.getUserByLogin(TEST_PREFIX + "student1").getId();

        // course.getExercises().forEach(exercise -> studentScoreUtilService.createStudentScore(exercise, userUtilService.getUserByLogin(STUDENT_OF_COURSE), 0.5));
        course.getExercises().forEach(
                exercise -> exercise.setTeams(Set.of(TeamFactory.generateTeamForExercise(exercise, "testTeam", "test", 3, userUtilService.getUserByLogin(STUDENT_OF_COURSE)))));
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

        @Test
        @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
        void shouldReturnCategories() throws Exception {
            final var result = request.get("/api/metrics/course/" + course.getId() + "/student", HttpStatus.OK, StudentMetricsDTO.class);
            assertThat(result).isNotNull();
            assertThat(result.exerciseMetrics()).isNotNull();
            final var categories = result.exerciseMetrics().categories();

            final var expectedCategories = learningMetricsService.getStudentExerciseMetrics(userID, course.getId()).categories();

            assertThat(categories).isEqualTo(expectedCategories);
        }

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

            // using learningMetricsService
            final var expectedMapSimple = learningMetricsService.getStudentExerciseMetrics(userID, course.getId()).averageScore();

            assertThat(averageScores).isEqualTo(expectedMap);
        }

        @Test
        @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
        void shouldReturnScore() throws Exception {
            final var result = request.get("/api/metrics/course/" + course.getId() + "/student", HttpStatus.OK, StudentMetricsDTO.class);
            assertThat(result).isNotNull();
            assertThat(result.exerciseMetrics()).isNotNull();
            final var score = result.exerciseMetrics().score();

            final var exercises = exerciseRepository.findAllExercisesByCourseId(course.getId());
            final var expectedScores = exercises.stream().collect(Collectors.toMap(Exercise::getId, exercise -> resultRepository
                    .getRatedResultsOrderedByParticipationIdLegalSubmissionIdResultIdDescForStudent(exercise.getId(), userID).stream().mapToDouble(Result::getScore).sum()));

            assertThat(score).isEqualTo(expectedScores);
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

        @Disabled
        @Test
        @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
        void shouldReturnCompleted() throws Exception {
            final var result = request.get("/api/metrics/course/" + course.getId() + "/student", HttpStatus.OK, StudentMetricsDTO.class);
            assertThat(result).isNotNull();
            assertThat(result.exerciseMetrics()).isNotNull();
            final var completed = result.exerciseMetrics().completed();

            /*
             * final var exercises = exerciseRepository.findAllWithCategoriesByCourseId(course.getId());
             * final var expectedCompleted = exercises.stream().map(Exercise::getId)
             * .filter(id -> resultRepository.findAllByParticipationExerciseId(id).stream().anyMatch(Result::isRated)).collect(Collectors.toSet());
             */
            final var expectedCompleted = learningMetricsService.getStudentExerciseMetrics(userID, course.getId()).completed();

            assertThat(completed).isEqualTo(expectedCompleted);
        }

        @Test
        @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
        void shouldReturnTeamId() throws Exception {
            final var result = request.get("/api/metrics/course/" + course.getId() + "/student", HttpStatus.OK, StudentMetricsDTO.class);
            assertThat(result).isNotNull();
            assertThat(result.exerciseMetrics()).isNotNull();
            // final var teamIDs = result.exerciseMetrics().teamId())

            // assertThat(teamIDs).isEqualTo(expectedTeamIDs);
        }
    }

    @Nested
    class CompetencyMetrics {

        @Test
        @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
        void shouldReturnCompetencyInformation() throws Exception {
            final var result = request.get("/api/metrics/course/" + courseWithCompetencies.getId() + "/student", HttpStatus.OK, StudentMetricsDTO.class);
            assertThat(result).isNotNull();
            assertThat(result.competencyMetrics()).isNotNull();

            final var competencyInformation = result.competencyMetrics().competencyInformation();

            // final var competencies = competencyRepository.findAllForCourse(courseWithCompetencies.getId());
            // final var expectedDTOs = competencies.stream().map().collect(Collectors.toSet());
            //
            // final var expectedDTOs = exercises.stream().map(ExerciseInformationDTO::of).collect(Collectors.toSet());

            // assertThat(exerciseInformation.values()).containsExactlyInAnyOrderElementsOf(expectedDTOs);
            // assertThat(exerciseInformation).allSatisfy((id, dto) -> assertThat(id).isEqualTo(dto.id()));
        }
    }

    @Nested
    class LectureMetrics {

        @Test
        @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
        void shouldReturnLectureUnitInformation() throws Exception {
            final var result = request.get("/api/metrics/course/" + course.getId() + "/student", HttpStatus.OK, StudentMetricsDTO.class);
            assertThat(result).isNotNull();
            assertThat(result.lectureUnitStudentMetricsDTO()).isNotNull();
            final var lectureUnitInformation = result.lectureUnitStudentMetricsDTO().lectureUnitInformation();

            final var expectedLectureUnits = lectureUnitMetricsRepository.findAllLectureUnitInformationByCourseId(courseWithCompetencies.getId());

            assertThat(lectureUnitInformation.values()).containsExactlyInAnyOrderElementsOf(expectedLectureUnits);
            assertThat(lectureUnitInformation).allSatisfy((id, dto) -> assertThat(id).isEqualTo(dto.id()));
        }
    }
}
