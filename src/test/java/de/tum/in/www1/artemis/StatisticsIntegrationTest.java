package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.GraphType;
import de.tum.in.www1.artemis.domain.enumeration.SpanType;
import de.tum.in.www1.artemis.domain.enumeration.StatisticsView;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.AnswerPostRepository;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.CourseManagementStatisticsDTO;
import de.tum.in.www1.artemis.web.rest.dto.ExerciseManagementStatisticsDTO;

class StatisticsIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private AnswerPostRepository answerPostRepository;

    private Course course;

    private TextExercise exercise;

    private final List<GraphType> artemisGraphs = Arrays.asList(GraphType.SUBMISSIONS, GraphType.ACTIVE_USERS, GraphType.LOGGED_IN_USERS, GraphType.RELEASED_EXERCISES,
            GraphType.EXERCISES_DUE, GraphType.CONDUCTED_EXAMS, GraphType.EXAM_PARTICIPATIONS, GraphType.EXAM_REGISTRATIONS, GraphType.ACTIVE_TUTORS, GraphType.CREATED_RESULTS,
            GraphType.CREATED_FEEDBACKS);

    private final List<GraphType> courseGraphs = Arrays.asList(GraphType.SUBMISSIONS, GraphType.ACTIVE_USERS, GraphType.RELEASED_EXERCISES, GraphType.EXERCISES_DUE,
            GraphType.CONDUCTED_EXAMS, GraphType.EXAM_PARTICIPATIONS, GraphType.EXAM_REGISTRATIONS, GraphType.ACTIVE_TUTORS, GraphType.CREATED_RESULTS, GraphType.CREATED_FEEDBACKS,
            GraphType.POSTS, GraphType.RESOLVED_POSTS);

    private final List<GraphType> exerciseGraphs = Arrays.asList(GraphType.SUBMISSIONS, GraphType.ACTIVE_USERS, GraphType.ACTIVE_TUTORS, GraphType.CREATED_RESULTS,
            GraphType.CREATED_FEEDBACKS, GraphType.POSTS, GraphType.RESOLVED_POSTS);

    @BeforeEach
    void initTestCase() {
        database.addUsers(12, 10, 0, 10);

        course = database.addCourseWithOneModelingExercise();
        var now = ZonedDateTime.now();
        exercise = ModelFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.plusHours(1), course);
        course.addExercises(exercise);
        textExerciseRepository.save(exercise);
        Post post = new Post();
        post.setExercise(exercise);
        post.setContent("Test Student Question 1");
        post.setVisibleForStudents(true);
        post.setCreationDate(ZonedDateTime.now().minusSeconds(11));
        post.setAuthor(database.getUserByLoginWithoutAuthorities("student1"));
        postRepository.save(post);

        AnswerPost answerPost = new AnswerPost();
        answerPost.setAuthor(database.getUserByLoginWithoutAuthorities("student1"));
        answerPost.setContent("Test Answer");
        answerPost.setCreationDate(ZonedDateTime.now().minusSeconds(10));
        answerPost.setPost(post);
        answerPostRepository.save(answerPost);

        // one submission today
        TextSubmission textSubmission = new TextSubmission();
        textSubmission.submissionDate(ZonedDateTime.now().minusSeconds(1));
        var submission = database.addSubmission(exercise, textSubmission, "student1");
        database.addResultToSubmission(submission, AssessmentType.MANUAL);

        for (int i = 2; i <= 12; i++) {
            textSubmission = new TextSubmission();
            textSubmission.submissionDate(ZonedDateTime.now().minusMonths(i - 1).withDayOfMonth(10));
            submission = database.addSubmission(exercise, textSubmission, "student" + i);
            database.addResultToSubmission(submission, AssessmentType.MANUAL);
        }
    }

    @AfterEach
    void resetDatabase() {
        database.resetDatabase();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(SpanType.class)
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDataRangeEachGraph(SpanType span) throws Exception {
        int expectedResultLength = expectedResultLength(span);

        for (GraphType graph : artemisGraphs) {
            int periodIndex = 0;
            var parameters = buildParameters(span, periodIndex, graph);
            Integer[] result = request.get("/api/management/statistics/data", HttpStatus.OK, Integer[].class, parameters);
            assertThat(result).hasSize(expectedResultLength);
        }
    }

    private int expectedResultLength(SpanType spanType) {
        return switch (spanType) {
            case DAY -> 24;
            case WEEK -> 7;
            case MONTH -> {
                ZonedDateTime now = ZonedDateTime.now();
                yield (int) ChronoUnit.DAYS.between(now.minusMonths(1), now);
            }
            case QUARTER, YEAR -> 12;
        };
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetChartDataForCourse() throws Exception {
        SpanType span = SpanType.WEEK;
        int periodIndex = 0;
        var view = StatisticsView.COURSE;
        var courseId = course.getId();
        for (GraphType graph : courseGraphs) {
            var parameters = buildParameters(span, periodIndex, graph, view, courseId);
            Integer[] result = request.get("/api/management/statistics/data-for-content", HttpStatus.OK, Integer[].class, parameters);
            assertThat(result).hasSize(7);
        }
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetChartDataForExercise() throws Exception {
        SpanType span = SpanType.WEEK;
        int periodIndex = 0;
        var view = StatisticsView.EXERCISE;
        var exerciseId = exercise.getId();
        for (GraphType graph : exerciseGraphs) {
            var parameters = buildParameters(span, periodIndex, graph, view, exerciseId);
            Integer[] result = request.get("/api/management/statistics/data-for-content", HttpStatus.OK, Integer[].class, parameters);
            assertThat(result).hasSize(7);
        }
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetCourseStatistics() throws Exception {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        TextExercise laterTextExercise = database.createIndividualTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        TextExercise earlierTextExercise = database.createIndividualTextExercise(course, pastTimestamp.minusDays(1), pastTimestamp.minusDays(1), pastTimestamp.minusDays(1));

        var laterTextExerciseId = laterTextExercise.getId();
        var earlierTextExerciseId = earlierTextExercise.getId();
        User student1 = userRepository.findOneByLogin("student1").orElseThrow();
        User student2 = userRepository.findOneByLogin("student2").orElseThrow();

        // Creating result for student1 and student2 for the later exercise
        database.createParticipationSubmissionAndResult(laterTextExerciseId, student1, 10.0, 0.0, 50, true);
        database.createParticipationSubmissionAndResult(laterTextExerciseId, student2, 10.0, 0.0, 100, true);

        // Creating result for student1 and student2 for the earlier exercise
        database.createParticipationSubmissionAndResult(earlierTextExerciseId, student1, 10.0, 0.0, 0, true);
        database.createParticipationSubmissionAndResult(earlierTextExerciseId, student2, 10.0, 0.0, 80, true);

        Long courseId = course.getId();
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("courseId", "" + courseId);
        CourseManagementStatisticsDTO result = request.get("/api/management/statistics/course-statistics", HttpStatus.OK, CourseManagementStatisticsDTO.class, parameters);

        assertThat(result.getAverageScoreOfCourse()).isEqualTo(57.5);
        assertThat(result.getAverageScoresOfExercises()).hasSize(2);

        // take the second entry as the results are getting sorted for release dates
        var firstTextExerciseStatistics = result.getAverageScoresOfExercises().get(1);
        assertThat(firstTextExerciseStatistics.getAverageScore()).isEqualTo(75.0);
        assertThat(firstTextExerciseStatistics.getExerciseId()).isEqualTo(laterTextExerciseId);
        assertThat(firstTextExerciseStatistics.getExerciseName()).isEqualTo(laterTextExercise.getTitle());

        // take the first entry as the results are getting sorted for release dates
        var secondTextExerciseStatistics = result.getAverageScoresOfExercises().get(0);
        assertThat(secondTextExerciseStatistics.getAverageScore()).isEqualTo(40.0);
        assertThat(secondTextExerciseStatistics.getExerciseId()).isEqualTo(earlierTextExerciseId);
        assertThat(secondTextExerciseStatistics.getExerciseName()).isEqualTo(earlierTextExercise.getTitle());
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetExerciseStatistics() throws Exception {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        TextExercise textExercise = database.createIndividualTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);

        var firstTextExerciseId = textExercise.getId();
        User student1 = userRepository.findOneByLogin("student1").orElseThrow();
        User student2 = userRepository.findOneByLogin("student2").orElseThrow();

        // Creating result for student1 and student2 for firstExercise
        database.createParticipationSubmissionAndResult(firstTextExerciseId, student1, 10.0, 0.0, 50, true);
        database.createParticipationSubmissionAndResult(firstTextExerciseId, student2, 10.0, 0.0, 100, true);

        Post post = new Post();
        post.setExercise(textExercise);
        post.setContent("Test Student Question 1");
        post.setVisibleForStudents(true);
        post.setCreationDate(ZonedDateTime.now().minusHours(2));
        post.setAuthor(database.getUserByLoginWithoutAuthorities("student1"));
        postRepository.save(post);

        AnswerPost answerPost = new AnswerPost();
        answerPost.setAuthor(database.getUserByLoginWithoutAuthorities("student1"));
        answerPost.setContent("Test Answer");
        answerPost.setCreationDate(ZonedDateTime.now().minusHours(1));
        answerPost.setResolvesPost(true);
        answerPost.setPost(post);
        answerPostRepository.save(answerPost);

        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("exerciseId", "" + firstTextExerciseId);
        ExerciseManagementStatisticsDTO result = request.get("/api/management/statistics/exercise-statistics", HttpStatus.OK, ExerciseManagementStatisticsDTO.class, parameters);

        assertThat(result.getAverageScoreOfExercise()).isEqualTo(75.0);
        assertThat(result.getMaxPointsOfExercise()).isEqualTo(10);
        assertThat(result.getNumberOfExerciseScores()).isEqualTo(2);
        assertThat(result.getNumberOfParticipations()).isEqualTo(2);
        assertThat(result.getNumberOfStudentsOrTeamsInCourse()).isEqualTo(12);
        assertThat(result.getNumberOfPosts()).isEqualTo(1);
        assertThat(result.getNumberOfResolvedPosts()).isEqualTo(1);
        var expectedScoresResult = new int[10];
        Arrays.fill(expectedScoresResult, 0);
        // We have one assessment with 50% and one with 100%
        expectedScoresResult[5] = 1;
        expectedScoresResult[9] = 1;
        assertThat(result.getScoreDistribution()).isEqualTo(expectedScoresResult);
    }

    private MultiValueMap<String, String> buildParameters(SpanType span, Integer periodIndex, GraphType graph) {
        return buildParameters(span, periodIndex, graph, null, null);
    }

    private MultiValueMap<String, String> buildParameters(SpanType span, Integer periodIndex, GraphType graph, StatisticsView view, Long entityId) {
        final MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();

        parameters.add("span", span.toString());
        parameters.add("periodIndex", periodIndex.toString());
        parameters.add("graphType", graph.toString());
        if (view != null) {
            parameters.add("view", view.toString());
        }
        if (entityId != null) {
            parameters.add("entityId", entityId.toString());
        }

        return parameters;
    }
}
