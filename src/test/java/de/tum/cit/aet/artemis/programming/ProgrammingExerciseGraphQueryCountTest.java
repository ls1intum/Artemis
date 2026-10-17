package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Pins how often the graph loads of a programming exercise read the exercise and its course.
 * <p>
 * An exercise brings its course along, and a graph that is assembled from several queries used to read both again for every query: the course as a select of its
 * own after each of them, and the exercise itself with all its columns, the problem statement among them. The course is now fetched in the statement that reads the
 * exercise, and the queries of one assembly run in one session and select the parts by the id of the exercise, so that the exercise and its course are read once.
 */
class ProgrammingExerciseGraphQueryCountTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "graphquerycount";

    /**
     * Statements that read the exercise table itself, not a table that points at it. A join of the course does not matter, it is part of the same statement.
     */
    private static final Pattern READS_THE_EXERCISE = Pattern.compile("\\bfrom exercise\\b");

    /**
     * Statements that read a course of their own.
     */
    private static final Pattern READS_A_COURSE = Pattern.compile("\\bfrom course\\b");

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Autowired
    private ChannelRepository channelRepository;

    private ProgrammingExercise exercise;

    private long exerciseId;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        var course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExerciseAndTestCases(TEST_PREFIX);
        exercise = (ProgrammingExercise) course.getExercises().stream().filter(ProgrammingExercise.class::isInstance).findFirst().orElseThrow();
        exerciseId = exercise.getId();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testTheUpdateGraphReadsTheExerciseAndItsCourseInOneStatement() {
        assertThatDb(() -> programmingExerciseRepository.findForUpdateByIdElseThrow(exerciseId)).hasBeenCalledTimes(1);
        assertReadsTheExerciseOnceAndNoCourseOnItsOwn();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testTheCreationGraphReadsTheExerciseAndItsCourseOnce() {
        // The graph without the criteria and the links, the criteria, and the links.
        assertThatDb(() -> programmingExerciseRepository.findForCreationByIdElseThrow(exerciseId)).hasBeenCalledTimes(3);
        assertReadsTheExerciseOnceAndNoCourseOnItsOwn();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testTheVersioningGraphReadsTheExerciseAndItsCourseOnce() {
        // The base graph, the test cases, the tasks and the static code analysis categories.
        assertThatDb(() -> programmingExerciseRepository.findForVersioningById(exerciseId)).hasBeenCalledTimes(4);
        assertReadsTheExerciseOnceAndNoCourseOnItsOwn();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testTheNameOfTheChannelIsReadWithoutReadingTheExercise() {
        Channel channel = conversationUtilService.addChannelToExercise(exercise);

        String name = assertThatDb(() -> channelRepository.findChannelNameByExerciseId(exerciseId)).hasBeenCalledTimes(1);

        assertThat(name).isEqualTo(channel.getName());
        // Loading the channel itself would read the exercise and the course it points at as well.
        assertThat(queryInterceptor.getStatements()).noneMatch(statement -> READS_THE_EXERCISE.matcher(statement).find() || READS_A_COURSE.matcher(statement).find());
    }

    private void assertReadsTheExerciseOnceAndNoCourseOnItsOwn() {
        List<String> statements = queryInterceptor.getStatements();
        assertThat(statements).filteredOn(statement -> READS_THE_EXERCISE.matcher(statement).find()).hasSize(1);
        assertThat(statements).noneMatch(statement -> READS_A_COURSE.matcher(statement).find());
    }
}
