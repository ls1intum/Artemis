package de.tum.cit.aet.artemis.globalsearch;

import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertAnswerPostExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertAnswerPostNotInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertChannelExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertChannelNotInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertPostExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertPostNotInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.awaitIndexing;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.queryChannelProperties;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationFactory;
import de.tum.cit.aet.artemis.core.util.RequestUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxEntry;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOrigin;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.AnswerPostSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.PostSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntitySyncStateRepository;
import de.tum.cit.aet.artemis.globalsearch.repository.WeaviateOutboxRepository;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateOutboxDispatcher;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
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
    private WeaviateService weaviateService;

    @Autowired
    private RequestUtilService request;

    @Autowired
    private SearchableEntityWeaviateService searchableEntityWeaviateService;

    @Autowired
    private WeaviateOutboxDispatcher weaviateOutboxDispatcher;

    @Autowired
    private WeaviateOutboxRepository outboxRepository;

    @Autowired
    private SearchableEntitySyncStateRepository syncStateRepository;

    @Autowired
    private PostTestRepository postRepository;

    @Autowired
    private AnswerPostRepository answerPostRepository;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

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
        course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testToggleChannelPrivacy_updatesWeaviate() throws Exception {
        Channel channel = new Channel();
        channel.setName("privacy-test");
        channel.setIsPublic(true);
        channel.setIsCourseWide(true);
        channel.setIsAnnouncementChannel(false);

        Channel createdChannel = channelService.createChannel(course, channel, Optional.of(instructor));
        assertChannelExistsInWeaviate(weaviateService, createdChannel);

        // Toggle privacy via REST (to simulate the actual use case where it's missing)
        request.postWithoutResponseBody("/api/communication/courses/" + course.getId() + "/channels/" + createdChannel.getId() + "/toggle-privacy", HttpStatus.OK,
                new org.springframework.util.LinkedMultiValueMap<>());

        Channel updatedChannel = channelRepository.findByIdElseThrow(createdChannel.getId());
        assertThat(updatedChannel.getIsPublic()).isFalse();

        awaitIndexing(() -> {
            var properties = queryChannelProperties(weaviateService, updatedChannel.getId());
            assertThat(properties).isNotNull();
            assertThat(properties.get(SearchableEntitySchema.Properties.CHANNEL_IS_PUBLIC)).isEqualTo(false);
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testToggleChannelPrivacy_removesPostsFromWeaviate() throws Exception {
        // Create a non-course-wide public channel (toggling to private makes it non-indexable)
        Channel channel = new Channel();
        channel.setName("privacy-posts-test");
        channel.setIsPublic(true);
        channel.setIsCourseWide(false);
        channel.setIsAnnouncementChannel(false);

        Channel createdChannel = channelService.createChannel(course, channel, Optional.of(instructor));
        assertChannelExistsInWeaviate(weaviateService, createdChannel);

        // Create and index a post in the channel
        Post post = ConversationFactory.createBasicPost(0, instructor);
        post.setConversation(createdChannel);
        post = postRepository.save(post);
        searchableEntityWeaviateService.upsertPostAsync(PostSearchableEntityDTO.fromPost(post, createdChannel));
        long postId = post.getId();
        assertPostExistsInWeaviate(weaviateService, postId);

        // Toggle privacy via REST: public -> private
        request.postWithoutResponseBody("/api/communication/courses/" + course.getId() + "/channels/" + createdChannel.getId() + "/toggle-privacy", HttpStatus.OK,
                new org.springframework.util.LinkedMultiValueMap<>());

        Channel updatedChannel = channelRepository.findByIdElseThrow(createdChannel.getId());
        assertThat(updatedChannel.getIsPublic()).isFalse();

        // Both the channel and its posts should be removed from Weaviate. Each helper already polls for up to 30s,
        // so they must not be wrapped in a further await: the first call would consume the whole shared budget and the
        // outer timeout would then replace the real assertion message with a bare "null".
        assertChannelNotInWeaviate(weaviateService, createdChannel.getId());
        assertPostNotInWeaviate(weaviateService, postId);
    }

    @ParameterizedTest
    @EnumSource(value = WeaviateOutboxOrigin.class, names = { "RECONCILE_DRIFT", "RECONCILE_ORPHAN" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReconcileDelete_afterPrivateChannelBecomesPublic_rederivesAndKeepsTheCurrentRow(WeaviateOutboxOrigin origin) throws Exception {
        Channel channel = new Channel();
        channel.setName("reconcile-" + reconcileOriginLabel(origin));
        channel.setIsPublic(false);
        channel.setIsCourseWide(false);
        channel.setIsAnnouncementChannel(false);

        Channel createdChannel = channelService.createChannel(course, channel, Optional.of(instructor));
        assertChannelNotInWeaviate(weaviateService, createdChannel.getId());

        request.postWithoutResponseBody("/api/communication/courses/" + course.getId() + "/channels/" + createdChannel.getId() + "/toggle-privacy", HttpStatus.OK,
                new org.springframework.util.LinkedMultiValueMap<>());
        Channel updatedChannel = channelRepository.findByIdElseThrow(createdChannel.getId());
        assertThat(updatedChannel.getIsPublic()).isTrue();
        assertChannelExistsInWeaviate(weaviateService, updatedChannel);

        assertReconcileDeleteReindexesCurrentChannel(updatedChannel, origin);
    }

    @ParameterizedTest
    @EnumSource(value = WeaviateOutboxOrigin.class, names = { "RECONCILE_DRIFT", "RECONCILE_ORPHAN" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReconcileDelete_afterArchivedChannelIsUnarchived_rederivesAndKeepsTheCurrentRow(WeaviateOutboxOrigin origin) throws Exception {
        Channel channel = new Channel();
        channel.setName("unarchive-" + reconcileOriginLabel(origin));
        channel.setIsPublic(true);
        channel.setIsCourseWide(true);
        channel.setIsAnnouncementChannel(false);

        Channel createdChannel = channelService.createChannel(course, channel, Optional.of(instructor));
        assertChannelExistsInWeaviate(weaviateService, createdChannel);
        channelService.archiveChannel(createdChannel.getId());
        assertChannelNotInWeaviate(weaviateService, createdChannel.getId());
        channelService.unarchiveChannel(createdChannel.getId());
        assertChannelExistsInWeaviate(weaviateService, createdChannel);

        assertReconcileDeleteReindexesCurrentChannel(createdChannel, origin);
    }

    private void assertReconcileDeleteReindexesCurrentChannel(Channel channel, WeaviateOutboxOrigin origin) {
        WeaviateOutboxEntry staleDelete = outboxRepository.save(WeaviateOutboxEntry.forDeleteEntity(SearchableEntitySchema.TypeValues.CHANNEL, channel.getId(), origin));

        weaviateOutboxDispatcher.drain();

        awaitIndexing(() -> {
            var properties = queryChannelProperties(weaviateService, channel.getId());
            assertThat(properties).as("the channel must be re-derived rather than removed by a stale reconcile decision").isNotNull();
            assertThat(((Number) properties.get(SearchableEntitySchema.Properties.SOURCE_SEQ)).longValue()).isEqualTo(staleDelete.getId());
            assertThat(syncStateRepository.findByEntityTypeAndEntityId(SearchableEntitySchema.TypeValues.CHANNEL, channel.getId())).isPresent()
                    .hasValueSatisfying(state -> assertThat(properties.get(SearchableEntitySchema.Properties.CONTENT_HASH)).isEqualTo(state.getContentHash()));
            assertThat(outboxRepository.existsById(staleDelete.getId())).as("the confirmed reconcile row is acknowledged").isFalse();
        });
    }

    private static String reconcileOriginLabel(WeaviateOutboxOrigin origin) {
        return origin == WeaviateOutboxOrigin.RECONCILE_DRIFT ? "drift" : "orphan";
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
            awaitIndexing(() -> {
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

            awaitIndexing(() -> {
                var properties = queryChannelProperties(weaviateService, createdChannel.getId());
                assertThat(properties).isNotNull();
                assertThat(properties.get(SearchableEntitySchema.Properties.TITLE)).isEqualTo("exercise-renamed");
            });
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testArchiveChannel_removesFromWeaviate() throws Exception {
            Channel channel = new Channel();
            channel.setName("archive-test");
            channel.setIsPublic(true);
            channel.setIsCourseWide(true);
            channel.setIsAnnouncementChannel(false);

            Channel createdChannel = channelService.createChannel(course, channel, Optional.of(instructor));
            assertChannelExistsInWeaviate(weaviateService, createdChannel);

            channelService.archiveChannel(createdChannel.getId());

            assertChannelNotInWeaviate(weaviateService, createdChannel.getId());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testUnarchiveChannel_reIndexesInWeaviate() throws Exception {
            Channel channel = new Channel();
            channel.setName("unarchive-test");
            channel.setIsPublic(true);
            channel.setIsCourseWide(true);
            channel.setIsAnnouncementChannel(false);

            Channel createdChannel = channelService.createChannel(course, channel, Optional.of(instructor));
            assertChannelExistsInWeaviate(weaviateService, createdChannel);

            channelService.archiveChannel(createdChannel.getId());
            assertChannelNotInWeaviate(weaviateService, createdChannel.getId());

            channelService.unarchiveChannel(createdChannel.getId());
            createdChannel.setIsArchived(false);
            assertChannelExistsInWeaviate(weaviateService, createdChannel);
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

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testDeleteChannelViaRest_removesChannelAndPostsFromWeaviate() throws Exception {
            // Create and index a public course-wide channel
            Channel channel = new Channel();
            channel.setName("delete-rest-test");
            channel.setIsPublic(true);
            channel.setIsCourseWide(true);
            channel.setIsAnnouncementChannel(false);
            Channel createdChannel = channelService.createChannel(course, channel, Optional.of(instructor));
            assertChannelExistsInWeaviate(weaviateService, createdChannel);

            // Create and index a post in the channel
            Post post = ConversationFactory.createBasicPost(0, instructor);
            post.setConversation(createdChannel);
            post = postRepository.save(post);
            searchableEntityWeaviateService.upsertPostAsync(PostSearchableEntityDTO.fromPost(post, createdChannel));
            long postId = post.getId();
            awaitIndexing(() -> assertPostExistsInWeaviate(weaviateService, postId));

            // Create and index an answer post (reply) in the channel
            AnswerPost answerPost = new AnswerPost();
            answerPost.setContent("Reply");
            answerPost.setAuthor(instructor);
            answerPost.setPost(post);
            answerPost.setCreationDate(ZonedDateTime.now());
            answerPost = answerPostRepository.save(answerPost);
            searchableEntityWeaviateService.upsertAnswerPostAsync(AnswerPostSearchableEntityDTO.fromAnswerPost(answerPost, createdChannel));
            long answerPostId = answerPost.getId();
            awaitIndexing(() -> assertAnswerPostExistsInWeaviate(weaviateService, answerPostId));

            // Delete the channel via the REST endpoint (this is what was broken: it bypassed Weaviate cleanup)
            long channelId = createdChannel.getId();
            request.delete("/api/communication/courses/" + course.getId() + "/channels/" + channelId, HttpStatus.OK);

            // All three entries must be gone from the search index
            awaitIndexing(() -> {
                assertChannelNotInWeaviate(weaviateService, channelId);
                assertPostNotInWeaviate(weaviateService, postId);
                assertAnswerPostNotInWeaviate(weaviateService, answerPostId);
            });
        }
    }
}
