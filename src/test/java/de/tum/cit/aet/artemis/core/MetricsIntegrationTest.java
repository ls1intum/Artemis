package de.tum.cit.aet.artemis.core;

import static de.tum.cit.aet.artemis.core.config.Constants.MIN_SCORE_GREEN;
import static de.tum.cit.aet.artemis.core.util.TimeUtil.toRelativeTime;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.assessment.domain.ParticipantScore;
import de.tum.cit.aet.artemis.assessment.repository.StudentScoreRepository;
import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreScheduleService;
import de.tum.cit.aet.artemis.assessment.util.StudentScoreUtilService;
import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyUtilService;
import de.tum.cit.aet.artemis.atlas.dto.metrics.CompetencyInformationDTO;
import de.tum.cit.aet.artemis.atlas.dto.metrics.LectureUnitInformationDTO;
import de.tum.cit.aet.artemis.atlas.dto.metrics.ResourceTimestampDTO;
import de.tum.cit.aet.artemis.atlas.dto.metrics.StudentMetricsDTO;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseInformationDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseMetricsRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureUnitService;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class MetricsIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "metricsintegration";

    @Autowired
    private ExerciseMetricsRepository exerciseMetricsRepository;

    @Autowired
    private CompetencyRepository competencyRepository;

    @Autowired
    private ExerciseTestRepository exerciseTestRepository;

    @Autowired
    private LectureUnitRepository lectureUnitRepository;

    @Autowired
    private StudentScoreRepository studentScoreRepository;

    @Autowired
    protected StudentScoreUtilService studentScoreUtilService;

    @Autowired
    protected LectureUtilService lectureUtilService;

    @Autowired
    protected CompetencyUtilService competencyUtilService;

    @Autowired
    protected LectureUnitService lectureUnitService;

    private Course course;

    private Course courseWithTestRuns;

    private long userID;

    private static final String STUDENT_OF_COURSE = TEST_PREFIX + "student1";

    @BeforeEach
    void setupTestScenario() throws Exception {
        // Prevents the ParticipantScoreScheduleService from scheduling tasks related to prior results
        ReflectionTestUtils.setField(participantScoreScheduleService, "lastScheduledRun", Optional.of(Instant.now()));
        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 100;
        participantScoreScheduleService.activate();

        userUtilService.addUsers(TEST_PREFIX, 3, 1, 1, 1);

        course = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, true);
        courseWithTestRuns = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResultsAndTestRunsAndTwoUsers(TEST_PREFIX, true);

        userUtilService.createAndSaveUser(TEST_PREFIX + "user1337");
        userID = userUtilService.getUserByLogin(TEST_PREFIX + "student1").getId();
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

            final var expectedCategories = exerciseTestRepository.findAllWithCategoriesByCourseId(course.getId()).stream()
                    .collect(Collectors.toMap(Exercise::getId, Exercise::getCategories));

            assertThat(categories).isEqualTo(expectedCategories);
        }

        @Test
        @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
        void shouldReturnAverageScores() throws Exception {
            final var exercises = exerciseTestRepository.findAllExercisesByCourseIdWithEagerParticipation(course.getId());
            exercises.forEach(exercise -> studentScoreUtilService.createRatedStudentScore(exercise, userUtilService.getUserByLogin(STUDENT_OF_COURSE), 5));
            final var result = request.get("/api/metrics/course/" + course.getId() + "/student", HttpStatus.OK, StudentMetricsDTO.class);
            assertThat(result).isNotNull();
            assertThat(result.exerciseMetrics()).isNotNull();
            final var averageScores = result.exerciseMetrics().averageScore();

            final var expectedAverageScores = exercises.stream().collect(Collectors.toMap(Exercise::getId,
                    exercise -> exercise.getStudentParticipations().stream().flatMap(participation -> participation.getStudents().stream()).mapToDouble(
                            student -> studentScoreRepository.findByExercise_IdAndUser_Id(exercise.getId(), student.getId()).map(ParticipantScore::getLastRatedScore).orElse(0.0))
                            .average().orElse(0.0)));

            assertThat(averageScores).isEqualTo(expectedAverageScores);
        }

        @Test
        @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
        void shouldReturnScore() throws Exception {
            final var exercises = exerciseRepository.findAllExercisesByCourseId(course.getId());

            exercises.forEach(exercise -> studentScoreUtilService.createRatedStudentScore(exercise, userUtilService.getUserByLogin(STUDENT_OF_COURSE), 0.5));

            final var result = request.get("/api/metrics/course/" + course.getId() + "/student", HttpStatus.OK, StudentMetricsDTO.class);
            assertThat(result).isNotNull();
            assertThat(result.exerciseMetrics()).isNotNull();
            final var score = result.exerciseMetrics().score();

            var expectedScores = exercises.stream()
                    .map(exercise -> studentScoreRepository.findByExercise_IdAndUser_Id(exercise.getId(), userID)
                            .map(studentScore -> Map.entry(exercise.getId(), studentScore.getLastRatedScore())))
                    .filter(Optional::isPresent).map(Optional::get).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

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

        @Test
        @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
        void shouldReturnCompleted() throws Exception {
            final var exercises = exerciseRepository.findAllExercisesByCourseId(course.getId());
            exercises.forEach(exercise -> studentScoreUtilService.createRatedStudentScore(exercise, userUtilService.getUserByLogin(STUDENT_OF_COURSE), MIN_SCORE_GREEN));

            final var result = request.get("/api/metrics/course/" + course.getId() + "/student", HttpStatus.OK, StudentMetricsDTO.class);
            assertThat(result).isNotNull();
            assertThat(result.exerciseMetrics()).isNotNull();
            final var completed = result.exerciseMetrics().completed();

            final var expectedCompleted = exercises.stream().map(Exercise::getId).filter(
                    id -> studentScoreRepository.findByExercise_IdAndUser_Id(id, userID).map(studentScore -> studentScore.getLastRatedScore() >= MIN_SCORE_GREEN).orElse(false))
                    .collect(Collectors.toSet());

            assertThat(completed).isEqualTo(expectedCompleted);
        }
    }

    @Nested
    class CompetencyMetrics {

        @Test
        @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
        void shouldReturnCompetencyInformation() throws Exception {
            course.setCompetencies(Set.of(competencyUtilService.createCompetency(course)));

            final var result = request.get("/api/metrics/course/" + course.getId() + "/student", HttpStatus.OK, StudentMetricsDTO.class);
            assertThat(result).isNotNull();
            assertThat(result.competencyMetrics()).isNotNull();

            final var competencyInformation = result.competencyMetrics().competencyInformation();

            final var competencies = competencyRepository.findAllForCourseWithExercisesAndLectureUnitsAndLecturesAndAttachments(course.getId());
            final var expectedDTOs = competencies.stream().map(CompetencyInformationDTO::of).collect(Collectors.toSet());

            assertThat(competencyInformation.values()).containsExactlyInAnyOrderElementsOf(expectedDTOs);
            assertThat(competencyInformation).allSatisfy((id, dto) -> assertThat(id).isEqualTo(dto.id()));
        }
    }

    @Nested
    class LectureMetrics {

        @Test
        @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
        void shouldReturnLectureUnitInformation() throws Exception {

            final var lectureUnit = lectureUtilService.createTextUnit();
            lectureUnitService.linkLectureUnitsToCompetency(competencyUtilService.createCompetency(course), Set.of(lectureUnit), Set.of());

            final var testLecture = lectureUtilService.createLecture(course, null);
            lectureUtilService.addLectureUnitsToLecture(testLecture, List.of(lectureUnit));
            course.addLectures(testLecture);

            final var result = request.get("/api/metrics/course/" + course.getId() + "/student", HttpStatus.OK, StudentMetricsDTO.class);
            assertThat(result).isNotNull();
            assertThat(result.lectureUnitStudentMetricsDTO()).isNotNull();
            final var lectureUnitInformation = result.lectureUnitStudentMetricsDTO().lectureUnitInformation();

            final var lectureUnits = lectureUnitRepository.findAllById(Set.of(lectureUnit.getId()));
            final var expectedDTOs = lectureUnits.stream().map(LectureUnitInformationDTO::of).collect(Collectors.toSet());

            assertThat(lectureUnitInformation.values()).containsExactlyInAnyOrderElementsOf(expectedDTOs);
            assertThat(lectureUnitInformation).allSatisfy((id, dto) -> assertThat(id).isEqualTo(dto.id()));
        }
    }
}
