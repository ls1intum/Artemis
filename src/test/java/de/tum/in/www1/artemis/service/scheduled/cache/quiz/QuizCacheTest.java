package de.tum.in.www1.artemis.service.scheduled.cache.quiz;

import static de.tum.in.www1.artemis.service.scheduled.cache.quiz.QuizCache.HAZELCAST_CACHED_EXERCISE_UPDATE_TOPIC;
import static org.assertj.core.api.Assertions.fail;

import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.hazelcast.core.HazelcastInstance;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.QuizMode;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.exercise.quizexercise.QuizExerciseFactory;
import de.tum.in.www1.artemis.exercise.quizexercise.QuizExerciseUtilService;
import de.tum.in.www1.artemis.repository.QuizExerciseRepository;
import de.tum.in.www1.artemis.service.QuizBatchService;
import de.tum.in.www1.artemis.service.QuizExerciseService;
import de.tum.in.www1.artemis.user.UserUtilService;

class QuizCacheTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "quizcachetest";

    @Autowired
    private HazelcastInstance hazelcastInstance;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private QuizExerciseService quizExerciseService;

    @Autowired
    private QuizBatchService quizBatchService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private QuizExerciseRepository quizExerciseRepository;

    @BeforeEach
    void init() {
        // do not use the schedule service based on a time interval in the tests, because this would result in flaky tests that run much slower
        quizScheduleService.stopSchedule();
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    @EnumSource(QuizMode.class)
    void testQuizSubmitNoDatabaseRequests(QuizMode quizMode) throws Exception {
        CountDownLatch lock = new CountDownLatch(1);

        var topic = hazelcastInstance.getTopic(HAZELCAST_CACHED_EXERCISE_UPDATE_TOPIC);
        topic.addMessageListener(o -> lock.countDown());

        Course course = courseUtilService.createCourse();
        QuizExercise quizExercise = QuizExerciseFactory.createQuiz(course, ZonedDateTime.now().minusHours(5), null, quizMode);
        quizExercise.setDuration(360);
        quizExercise.getQuizBatches().forEach(batch -> batch.setStartTime(ZonedDateTime.now().minusMinutes(5)));
        quizExercise = quizExerciseRepository.save(quizExercise);
        final long exerciseId = quizExercise.getId();

        // create a local cache exists to save the exercise in
        quizScheduleService.getWriteCache(exerciseId);
        quizExercise = quizExerciseService.save(quizExercise); // <- responsible for saving the exercise into the distributed exercise cache

        // Wait until the exercise update got processed
        if (!lock.await(2000, TimeUnit.MILLISECONDS)) {
            fail("Timed out waiting for the quiz exercise cache.");
        }

        if (quizMode != QuizMode.SYNCHRONIZED) {
            var batch = quizBatchService.save(QuizExerciseFactory.generateQuizBatch(quizExercise, ZonedDateTime.now().minusSeconds(5)));
            quizExerciseUtilService.joinQuizBatch(quizExercise, batch, TEST_PREFIX + "student1");
        }

        QuizSubmission quizSubmission = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExercise, 1, false, ZonedDateTime.now());

        assertThatDb(() -> request.postWithResponseBody("/api/exercises/" + exerciseId + "/submissions/live", quizSubmission, Result.class, HttpStatus.OK))
                .hasBeenCalledTimes(quizMode == QuizMode.SYNCHRONIZED ? 0 : 1);
    }
}
