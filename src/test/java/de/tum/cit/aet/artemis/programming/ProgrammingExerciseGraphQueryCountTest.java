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
 * Pins the query count and course-loading behavior of programming exercise graphs.
 * Creation uses three exercise-rooted queries to initialize managed collection wrappers without replacing them in a joined write transaction.
 * This repeats the exercise columns, but avoids a collection Cartesian product and separate course reads.
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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testTheUpdateGraphReadsTheExerciseAndItsCourseInOneStatement() {
        assertThatDb(() -> programmingExerciseRepository.findForUpdateByIdElseThrow(exerciseId)).hasBeenCalledTimes(1);
        assertReadsTheExerciseTimesAndNoCourseOnItsOwn(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testTheCreationGraphInitializesManagedCollectionsWithoutSeparateCourseReads() {
        // The graph without the criteria and the links, the criteria, and the links.
        assertThatDb(() -> programmingExerciseRepository.findForCreationByIdElseThrow(exerciseId)).hasBeenCalledTimes(3);
        assertReadsTheExerciseTimesAndNoCourseOnItsOwn(3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testTheVersioningGraphReadsTheExerciseAndItsCourseOnce() {
        // The base graph, the test cases, the tasks and the static code analysis categories.
        assertThatDb(() -> programmingExerciseRepository.findForVersioningById(exerciseId)).hasBeenCalledTimes(4);
        assertReadsTheExerciseTimesAndNoCourseOnItsOwn(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testTheNameOfTheChannelIsReadWithoutReadingTheExercise() {
        Channel channel = conversationUtilService.addChannelToExercise(exercise);

        String name = assertThatDb(() -> channelRepository.findChannelNameByExerciseId(exerciseId)).hasBeenCalledTimes(1);

        assertThat(name).isEqualTo(channel.getName());
        // Loading the channel itself would read the exercise and the course it points at as well.
        assertThat(queryInterceptor.getStatements()).noneMatch(statement -> READS_THE_EXERCISE.matcher(statement).find() || READS_A_COURSE.matcher(statement).find());
    }

    private void assertReadsTheExerciseTimesAndNoCourseOnItsOwn(int expectedExerciseReads) {
        List<String> statements = queryInterceptor.getStatements();
        assertThat(statements).filteredOn(statement -> READS_THE_EXERCISE.matcher(statement).find()).hasSize(expectedExerciseReads);
        assertThat(statements).noneMatch(statement -> READS_A_COURSE.matcher(statement).find());
    }
}
