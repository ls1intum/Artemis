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

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.GraphType;
import de.tum.in.www1.artemis.domain.enumeration.SpanType;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.util.ModelFactory;

public class UserStatisticsIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(12, 0, 0);

        var course = database.addCourseWithOneModelingExercise();
        var now = ZonedDateTime.now();
        TextExercise textExercise = ModelFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.plusHours(1), course);
        course.addExercises(textExercise);
        textExerciseRepository.save(textExercise);

        for (int i = 1; i <= 12; i++) {
            TextSubmission textSubmission = new TextSubmission();
            textSubmission.submissionDate(ZonedDateTime.now().minusMonths(i - 1).withDayOfMonth(10));
            database.addSubmission(textExercise, textSubmission, "student" + i);
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
}
