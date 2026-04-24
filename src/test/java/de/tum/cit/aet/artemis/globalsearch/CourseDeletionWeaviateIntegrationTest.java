package de.tum.cit.aet.artemis.globalsearch;

import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertChannelExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertChannelNotInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertExamExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertExamNotInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertExerciseExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertExerciseNotInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertFaqExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertFaqNotInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertLectureExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertLectureNotInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertLectureUnitExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertLectureUnitNotInWeaviate;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import de.tum.cit.aet.artemis.communication.FaqFactory;
import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.iris.api.PyrisFaqApi;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

/**
 * Integration tests verifying that deleting a course removes all related Weaviate rows
 * (exercises, lectures, lecture units, exams, FAQs, channels).
 * <p>
 * Tests are skipped when Docker is not available or the Weaviate container failed to start.
 */
@EnabledIf("isWeaviateEnabled")
class CourseDeletionWeaviateIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "cdweaviateint";

    @Autowired
    private SearchableEntityWeaviateService searchableEntityWeaviateService;

    @Autowired
    private WeaviateService weaviateService;

    @Autowired
    private FaqRepository faqRepository;

    @Autowired
    private ChannelService channelService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @MockitoSpyBean
    private PyrisFaqApi pyrisFaqApi;

    private Course course;

    private User instructor;

    static boolean isWeaviateEnabled() {
        return weaviateContainer != null && weaviateContainer.isRunning();
    }

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        // Pyris is not running in integration tests — stub the FAQ deletion to prevent PyrisConnectorException
        doNothing().when(pyrisFaqApi).deleteFaq(any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteCourse_removesExercisesFromWeaviate() throws Exception {
        ProgrammingExercise exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        searchableEntityWeaviateService.upsertExerciseAsync(exercise);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertExerciseExistsInWeaviate(weaviateService, exercise));

        long exerciseId = exercise.getId();
        request.delete("/api/core/admin/courses/" + course.getId(), HttpStatus.OK);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertExerciseNotInWeaviate(weaviateService, exerciseId));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteCourse_removesLecturesFromWeaviate() throws Exception {
        Lecture lecture = lectureUtilService.createLecture(course);
        searchableEntityWeaviateService.upsertLectureAsync(lecture);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertLectureExistsInWeaviate(weaviateService, lecture));

        long lectureId = lecture.getId();
        request.delete("/api/core/admin/courses/" + course.getId(), HttpStatus.OK);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertLectureNotInWeaviate(weaviateService, lectureId));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteCourse_removesLectureUnitsFromWeaviate() throws Exception {
        Lecture lecture = lectureUtilService.createLecture(course);
        TextUnit textUnit = lectureUtilService.createTextUnit(lecture);
        searchableEntityWeaviateService.upsertLectureUnitAsync(textUnit);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertLectureUnitExistsInWeaviate(weaviateService, textUnit.getId()));

        long textUnitId = textUnit.getId();
        request.delete("/api/core/admin/courses/" + course.getId(), HttpStatus.OK);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertLectureUnitNotInWeaviate(weaviateService, textUnitId));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteCourse_removesExamsFromWeaviate() throws Exception {
        Exam exam = examUtilService.addExam(course);
        searchableEntityWeaviateService.upsertExamAsync(exam);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertExamExistsInWeaviate(weaviateService, exam.getId()));

        long examId = exam.getId();
        request.delete("/api/core/admin/courses/" + course.getId(), HttpStatus.OK);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertExamNotInWeaviate(weaviateService, examId));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteCourse_removesFaqsFromWeaviate() throws Exception {
        Faq faq1 = FaqFactory.generateFaq(course, FaqState.ACCEPTED, "FAQ Title 1", "FAQ Answer 1");
        Faq savedFaq1 = faqRepository.save(faq1);
        searchableEntityWeaviateService.upsertFaqAsync(savedFaq1);

        Faq faq2 = FaqFactory.generateFaq(course, FaqState.ACCEPTED, "FAQ Title 2", "FAQ Answer 2");
        Faq savedFaq2 = faqRepository.save(faq2);
        searchableEntityWeaviateService.upsertFaqAsync(savedFaq2);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertFaqExistsInWeaviate(weaviateService, savedFaq1.getId());
            assertFaqExistsInWeaviate(weaviateService, savedFaq2.getId());
        });

        long faq1Id = savedFaq1.getId();
        long faq2Id = savedFaq2.getId();

        request.delete("/api/core/admin/courses/" + course.getId(), HttpStatus.OK);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertFaqNotInWeaviate(weaviateService, faq1Id);
            assertFaqNotInWeaviate(weaviateService, faq2Id);
        });
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteCourse_removesChannelsFromWeaviate() throws Exception {
        Channel channel = new Channel();
        channel.setName("test-channel");
        channel.setIsPublic(true);
        channel.setIsCourseWide(true);
        channel.setIsAnnouncementChannel(false);

        Channel createdChannel = channelService.createChannel(course, channel, Optional.of(instructor));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertChannelExistsInWeaviate(weaviateService, createdChannel));

        long channelId = createdChannel.getId();
        request.delete("/api/core/admin/courses/" + course.getId(), HttpStatus.OK);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertChannelNotInWeaviate(weaviateService, channelId));
    }
}
