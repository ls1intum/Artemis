package de.tum.cit.aet.artemis.globalsearch;

import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertChannelExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertChannelNotInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.queryChannelProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

/**
 * Integration tests for channel Weaviate indexing in {@link ChannelService}.
 * <p>
 * Verifies that channels are correctly upserted and deleted in Weaviate
 * when created, updated, or deleted via the ChannelService methods.
 * <p>
 * Tests are skipped when Docker is not available or the Weaviate container failed to start.
 */
@EnabledIf("isWeaviateEnabled")
class ChannelWeaviateIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "chweaviateint";

    @Autowired
    private ChannelService channelService;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private SearchableEntityWeaviateService searchableEntityWeaviateService;

    @Autowired
    private WeaviateService weaviateService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private LectureRepository lectureRepository;

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
    }

    @Nested
    class CreateChannelTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testCreateChannel_indexesInWeaviate() throws Exception {
            Channel channel = new Channel();
            channel.setName("test-channel");
            channel.setIsPublic(true);
            channel.setIsCourseWide(true);
            channel.setIsAnnouncementChannel(false);

            Channel createdChannel = channelService.createChannel(course, channel, Optional.of(instructor));

            assertChannelExistsInWeaviate(weaviateService, createdChannel);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testCreateLectureChannel_indexesInWeaviate() throws Exception {
            Lecture lecture = new Lecture();
            lecture.setTitle("Test Lecture");
            lecture.setCourse(course);
            lecture = lectureRepository.save(lecture);

            channelService.createLectureChannel(lecture, Optional.empty());

            Channel lectureChannel = channelRepository.findChannelByLectureId(lecture.getId());
            assertThat(lectureChannel).isNotNull();
            assertChannelExistsInWeaviate(weaviateService, lectureChannel);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testCreateExerciseChannel_indexesInWeaviate() throws Exception {
            ProgrammingExercise exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);

            Channel createdChannel = channelService.createExerciseChannel(exercise, Optional.empty());

            assertThat(createdChannel).isNotNull();
            assertChannelExistsInWeaviate(weaviateService, createdChannel);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testCreateChannelsForLectures_indexesInWeaviate() throws Exception {
            Lecture lecture1 = new Lecture();
            lecture1.setTitle("Lecture One");
            lecture1.setCourse(course);
            lecture1 = lectureRepository.save(lecture1);

            Lecture lecture2 = new Lecture();
            lecture2.setTitle("Lecture Two");
            lecture2.setCourse(course);
            lecture2 = lectureRepository.save(lecture2);

            channelService.createChannelsForLectures(List.of(lecture1, lecture2), course, instructor);

            Channel channel1 = channelRepository.findChannelByLectureId(lecture1.getId());
            Channel channel2 = channelRepository.findChannelByLectureId(lecture2.getId());
            assertThat(channel1).isNotNull();
            assertThat(channel2).isNotNull();

            assertChannelExistsInWeaviate(weaviateService, channel1);
            assertChannelExistsInWeaviate(weaviateService, channel2);
        }
    }

    @Nested
    class UpdateChannelTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testUpdateLectureChannelName_updatesWeaviate() throws Exception {
            Lecture lecture = new Lecture();
            lecture.setTitle("Original Lecture");
            lecture.setCourse(course);
            lecture = lectureRepository.save(lecture);

            channelService.createLectureChannel(lecture, Optional.empty());

            Channel channel = channelRepository.findChannelByLectureId(lecture.getId());
            assertThat(channel).isNotNull();
            assertChannelExistsInWeaviate(weaviateService, channel);

            String newChannelName = "lecture-renamed";
            channelService.updateLectureChannel(lecture, newChannelName);

            Channel updatedChannel = channelRepository.findChannelByLectureId(lecture.getId());
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                var properties = queryChannelProperties(weaviateService, updatedChannel.getId());
                assertThat(properties).isNotNull();
                assertThat(properties.get(SearchableEntitySchema.Properties.TITLE)).isEqualTo(newChannelName);
            });
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testUpdateExerciseChannelName_updatesWeaviate() throws Exception {
            ProgrammingExercise exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
            Channel createdChannel = channelService.createExerciseChannel(exercise, Optional.empty());
            assertThat(createdChannel).isNotNull();
            assertChannelExistsInWeaviate(weaviateService, createdChannel);

            exercise.setChannelName("exercise-renamed");
            channelService.updateExerciseChannel(exercise, exercise);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                var properties = queryChannelProperties(weaviateService, createdChannel.getId());
                assertThat(properties).isNotNull();
                assertThat(properties.get(SearchableEntitySchema.Properties.TITLE)).isEqualTo("exercise-renamed");
            });
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testArchiveChannel_updatesWeaviate() throws Exception {
            Channel channel = new Channel();
            channel.setName("archive-test");
            channel.setIsPublic(true);
            channel.setIsCourseWide(true);
            channel.setIsAnnouncementChannel(false);

            Channel createdChannel = channelService.createChannel(course, channel, Optional.of(instructor));
            assertChannelExistsInWeaviate(weaviateService, createdChannel);

            channelService.archiveChannel(createdChannel.getId());

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                var properties = queryChannelProperties(weaviateService, createdChannel.getId());
                assertThat(properties).isNotNull();
                assertThat(properties.get(SearchableEntitySchema.Properties.CHANNEL_IS_ARCHIVED)).isEqualTo(true);
            });
        }
    }

    @Nested
    class DeleteChannelTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testDeleteChannel_removesFromWeaviate() throws Exception {
            Channel channel = new Channel();
            channel.setName("delete-test");
            channel.setIsPublic(true);
            channel.setIsCourseWide(true);
            channel.setIsAnnouncementChannel(false);

            Channel createdChannel = channelService.createChannel(course, channel, Optional.of(instructor));
            assertChannelExistsInWeaviate(weaviateService, createdChannel);

            long channelId = createdChannel.getId();
            channelService.deleteChannel(createdChannel);

            assertChannelNotInWeaviate(weaviateService, channelId);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testDeleteChannelForExerciseId_removesFromWeaviate() throws Exception {
            ProgrammingExercise exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
            Channel createdChannel = channelService.createExerciseChannel(exercise, Optional.empty());
            assertThat(createdChannel).isNotNull();
            assertChannelExistsInWeaviate(weaviateService, createdChannel);

            long channelId = createdChannel.getId();
            channelService.deleteChannelForExerciseId(exercise.getId());

            assertChannelNotInWeaviate(weaviateService, channelId);
        }
    }
}
