package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.GraphType;
import de.tum.in.www1.artemis.domain.enumeration.SpanType;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.CourseManagementStatisticsDTO;

public class StatisticsIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    UserRepository userRepository;

    Course course;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(12, 10, 10);

        course = database.addCourseWithOneModelingExercise();
        var now = ZonedDateTime.now();
        TextExercise textExercise = ModelFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.plusHours(1), course);
        course.addExercises(textExercise);
        textExerciseRepository.save(textExercise);

        // one submission today
        TextSubmission textSubmission = new TextSubmission();
        textSubmission.submissionDate(ZonedDateTime.now().minusSeconds(1));
        var submission = database.addSubmission(textExercise, textSubmission, "student1");
        database.addResultToSubmission(submission, AssessmentType.MANUAL);

        for (int i = 2; i <= 12; i++) {
            textSubmission = new TextSubmission();
            textSubmission.submissionDate(ZonedDateTime.now().minusMonths(i - 1).withDayOfMonth(10));
            submission = database.addSubmission(textExercise, textSubmission, "student" + i);
            database.addResultToSubmission(submission, AssessmentType.MANUAL);
        }
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testDataForDayEachGraph() throws Exception {

        SpanType span = SpanType.DAY;
        for (GraphType graph : GraphType.values()) {
            int periodIndex = 0;
            LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
            parameters.add("span", "" + span);
            parameters.add("periodIndex", "" + periodIndex);
            parameters.add("graphType", "" + graph);
            Integer[] result = request.get("/api/management/statistics/data", HttpStatus.OK, Integer[].class, parameters);
            assertThat(result.length).isEqualTo(24);
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testDataForWeekEachGraph() throws Exception {

        SpanType span = SpanType.WEEK;
        for (GraphType graph : GraphType.values()) {
            int periodIndex = 0;
            LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
            parameters.add("span", "" + span);
            parameters.add("periodIndex", "" + periodIndex);
            parameters.add("graphType", "" + graph);
            Integer[] result = request.get("/api/management/statistics/data", HttpStatus.OK, Integer[].class, parameters);
            assertThat(result.length).isEqualTo(7);
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testDataForMonthEachGraph() throws Exception {
        ZonedDateTime now = ZonedDateTime.now();
        SpanType span = SpanType.MONTH;
        for (GraphType graph : GraphType.values()) {
            int periodIndex = 0;
            LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
            parameters.add("span", "" + span);
            parameters.add("periodIndex", "" + periodIndex);
            parameters.add("graphType", "" + graph);
            Integer[] result = request.get("/api/management/statistics/data", HttpStatus.OK, Integer[].class, parameters);
            assertThat(result.length).isEqualTo(YearMonth.of(now.getYear(), now.minusMonths(1 - periodIndex).plusDays(1).getMonth()).lengthOfMonth());
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testDataForQuarterEachGraph() throws Exception {
        SpanType span = SpanType.QUARTER;
        for (GraphType graph : GraphType.values()) {
            int periodIndex = 0;
            LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
            parameters.add("span", "" + span);
            parameters.add("periodIndex", "" + periodIndex);
            parameters.add("graphType", "" + graph);
            Integer[] result = request.get("/api/management/statistics/data", HttpStatus.OK, Integer[].class, parameters);
            assertThat(result.length).isEqualTo(12);
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testDataForYearEachGraph() throws Exception {

        SpanType span = SpanType.YEAR;
        for (GraphType graph : GraphType.values()) {
            int periodIndex = 0;
            LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
            parameters.add("span", "" + span);
            parameters.add("periodIndex", "" + periodIndex);
            parameters.add("graphType", "" + graph);
            Integer[] result = request.get("/api/management/statistics/data", HttpStatus.OK, Integer[].class, parameters);
            assertThat(result.length).isEqualTo(12);
        }
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetChartDataForCourse() throws Exception {
        var courseId = course.getId();
        SpanType span = SpanType.WEEK;
        int periodIndex = 0;
        var graph = GraphType.SUBMISSIONS;
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("span", "" + span);
        parameters.add("periodIndex", "" + periodIndex);
        parameters.add("graphType", "" + graph);
        parameters.add("courseId", "" + courseId);
        Integer[] result = request.get("/api/management/statistics/data-for-course", HttpStatus.OK, Integer[].class, parameters);
        assertThat(result.length).isEqualTo(7);
        // one submission was manually added right before the request
        assertThat(result[6]).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetCourseStatistics() throws Exception {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        TextExercise firstTextExercise = database.createIndividualTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        TextExercise secondTextExercise = database.createIndividualTextExercise(course, pastTimestamp.minusDays(1), pastTimestamp.minusDays(1), pastTimestamp.minusDays(1));

        var firstTextExerciseId = firstTextExercise.getId();
        var secondTextExerciseId = secondTextExercise.getId();
        User student1 = userRepository.findOneByLogin("student1").orElseThrow();
        User student2 = userRepository.findOneByLogin("student2").orElseThrow();

        // Creating result for student1 and student2 for firstExercise
        database.createParticipationSubmissionAndResult(firstTextExerciseId, student1, 10.0, 0.0, 50, true);
        database.createParticipationSubmissionAndResult(firstTextExerciseId, student2, 10.0, 0.0, 100, true);

        // Creating result for student1 and student2 for secondExercise
        database.createParticipationSubmissionAndResult(secondTextExerciseId, student1, 10.0, 0.0, 0, true);
        database.createParticipationSubmissionAndResult(secondTextExerciseId, student2, 10.0, 0.0, 80, true);

        Long courseId = course.getId();
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("courseId", "" + courseId);
        CourseManagementStatisticsDTO result = request.get("/api/management/statistics/course-statistics", HttpStatus.OK, CourseManagementStatisticsDTO.class, parameters);

        assertThat(result.getAverageScoreOfCourse()).isEqualTo(57.5);
        assertThat(result.getAverageScoresOfExercises().size()).isEqualTo(2);

        var firstTextExerciseStatistics = result.getAverageScoresOfExercises().get(0);
        assertThat(firstTextExerciseStatistics.getAverageScore()).isEqualTo(75.0);
        assertThat(firstTextExerciseStatistics.getExerciseId()).isEqualTo(firstTextExerciseId);
        assertThat(firstTextExerciseStatistics.getExerciseName()).isEqualTo(firstTextExercise.getTitle());

        var secondTextExerciseStatistics = result.getAverageScoresOfExercises().get(1);
        assertThat(secondTextExerciseStatistics.getAverageScore()).isEqualTo(40.0);
        assertThat(secondTextExerciseStatistics.getExerciseId()).isEqualTo(secondTextExerciseId);
        assertThat(secondTextExerciseStatistics.getExerciseName()).isEqualTo(secondTextExercise.getTitle());
    }
}
