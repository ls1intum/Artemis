package de.tum.cit.aet.artemis.globalsearch;

import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertAnswerPostExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertAnswerPostNotInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertPostExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertPostNotInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.queryPostProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationFactory;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.AnswerPostSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.PostSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

/**
 * Integration tests for post (message) Weaviate indexing.
 * <p>
 * Verifies that posts from public channels are correctly upserted and deleted in Weaviate,
 * and that channel deletion/archiving removes associated posts from the index.
 * <p>
 * Tests are skipped when Docker is not available or the Weaviate container failed to start.
 */
@EnabledIf("isWeaviateEnabled")
class PostWeaviateIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "postweaviateint";

    @Autowired
    private SearchableEntityWeaviateService searchableEntityWeaviateService;

    @Autowired
    private WeaviateService weaviateService;

    @Autowired
    private ChannelService channelService;

    @Autowired
    private PostTestRepository postRepository;

    @Autowired
    private AnswerPostRepository answerPostRepository;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

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
        doNothing().when(pyrisFaqApi).deleteFaq(any());
    }

    private Channel createPublicChannel(String name) {
        Channel channel = new Channel();
        channel.setName(name);
        channel.setIsPublic(true);
        channel.setIsCourseWide(false);
        channel.setIsAnnouncementChannel(false);
        return channelService.createChannel(course, channel, Optional.of(instructor));
    }

    private Channel createPrivateChannel(String name) {
        Channel channel = new Channel();
        channel.setName(name);
        channel.setIsPublic(false);
        channel.setIsCourseWide(false);
        channel.setIsAnnouncementChannel(false);
        return channelService.createChannel(course, channel, Optional.of(instructor));
    }

    private Post createAndSavePost(Channel channel) {
        Post post = ConversationFactory.createBasicPost(0, instructor);
        post.setConversation(channel);
        return postRepository.save(post);
    }

    private AnswerPost createAndSaveAnswerPost(Post post) {
        AnswerPost answerPost = new AnswerPost();
        answerPost.setContent("Reply to " + post.getContent());
        answerPost.setAuthor(instructor);
        answerPost.setPost(post);
        answerPost.setCreationDate(ZonedDateTime.now());
        return answerPostRepository.save(answerPost);
    }

    @Nested
    class UpsertTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testUpsertPost_indexesInWeaviate() throws Exception {
            Channel channel = createPublicChannel("upsert-test");
            Post post = createAndSavePost(channel);

            searchableEntityWeaviateService.upsertPostAsync(PostSearchableEntityDTO.fromPost(post, channel));

            assertPostExistsInWeaviate(weaviateService, post.getId());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testUpsertPost_storesCorrectProperties() throws Exception {
            Channel channel = createPublicChannel("props-test");
            Post post = createAndSavePost(channel);

            searchableEntityWeaviateService.upsertPostAsync(PostSearchableEntityDTO.fromPost(post, channel));

            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                var properties = queryPostProperties(weaviateService, post.getId());
                assertThat(properties).isNotNull();
                assertThat(properties.get(SearchableEntitySchema.Properties.DESCRIPTION)).isEqualTo(post.getContent());
                assertThat(((Number) properties.get(SearchableEntitySchema.Properties.COURSE_ID)).longValue()).isEqualTo(course.getId());
                assertThat(((Number) properties.get(SearchableEntitySchema.Properties.CHANNEL_ID)).longValue()).isEqualTo(channel.getId());
            });
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testUpsertPost_updatesContentInWeaviate() throws Exception {
            Channel channel = createPublicChannel("update-test");
            Post post = createAndSavePost(channel);

            searchableEntityWeaviateService.upsertPostAsync(PostSearchableEntityDTO.fromPost(post, channel));
            assertPostExistsInWeaviate(weaviateService, post.getId());

            // Simulate content update
            post.setContent("Updated content");
            searchableEntityWeaviateService.upsertPostAsync(PostSearchableEntityDTO.fromPost(post, channel));

            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                var properties = queryPostProperties(weaviateService, post.getId());
                assertThat(properties).isNotNull();
                assertThat(properties.get(SearchableEntitySchema.Properties.DESCRIPTION)).isEqualTo("Updated content");
            });
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testIsIndexable_returnsFalseForPrivateChannel() {
            Channel channel = createPrivateChannel("private-test");
            assertThat(PostSearchableEntityDTO.isIndexable(channel)).isFalse();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testIsIndexable_returnsTrueForPublicChannel() {
            Channel channel = createPublicChannel("public-test");
            assertThat(PostSearchableEntityDTO.isIndexable(channel)).isTrue();
        }
    }

    @Nested
    class DeleteTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testDeletePost_removesFromWeaviate() throws Exception {
            Channel channel = createPublicChannel("delete-post-test");
            Post post = createAndSavePost(channel);

            searchableEntityWeaviateService.upsertPostAsync(PostSearchableEntityDTO.fromPost(post, channel));
            assertPostExistsInWeaviate(weaviateService, post.getId());

            long postId = post.getId();
            searchableEntityWeaviateService.deleteEntityAsync(SearchableEntitySchema.TypeValues.POST, postId);

            assertPostNotInWeaviate(weaviateService, postId);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testDeleteChannel_removesPostsFromWeaviate() throws Exception {
            Channel channel = createPublicChannel("delete-channel-test");
            Post post1 = createAndSavePost(channel);
            Post post2 = createAndSavePost(channel);

            searchableEntityWeaviateService.upsertPostAsync(PostSearchableEntityDTO.fromPost(post1, channel));
            searchableEntityWeaviateService.upsertPostAsync(PostSearchableEntityDTO.fromPost(post2, channel));
            assertPostExistsInWeaviate(weaviateService, post1.getId());
            assertPostExistsInWeaviate(weaviateService, post2.getId());

            long post1Id = post1.getId();
            long post2Id = post2.getId();
            channelService.deleteChannel(channel);

            assertPostNotInWeaviate(weaviateService, post1Id);
            assertPostNotInWeaviate(weaviateService, post2Id);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testArchiveChannel_removesPostsFromWeaviate() throws Exception {
            Channel channel = createPublicChannel("archive-post-test");
            Post post = createAndSavePost(channel);

            searchableEntityWeaviateService.upsertPostAsync(PostSearchableEntityDTO.fromPost(post, channel));
            assertPostExistsInWeaviate(weaviateService, post.getId());

            long postId = post.getId();
            channelService.archiveChannel(channel.getId());

            assertPostNotInWeaviate(weaviateService, postId);
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void testDeleteCourse_removesPostsFromWeaviate() throws Exception {
            Channel channel = createPublicChannel("course-delete-post-test");
            Post post = createAndSavePost(channel);

            searchableEntityWeaviateService.upsertPostAsync(PostSearchableEntityDTO.fromPost(post, channel));
            assertPostExistsInWeaviate(weaviateService, post.getId());

            long postId = post.getId();
            request.delete("/api/core/admin/courses/" + course.getId(), HttpStatus.OK);

            assertPostNotInWeaviate(weaviateService, postId);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testDeleteAllAnswerPostsForPost_removesAnswerPostsFromWeaviate() throws Exception {
            Channel channel = createPublicChannel("delete-answers-test");
            Post post = createAndSavePost(channel);
            AnswerPost answer1 = createAndSaveAnswerPost(post);
            AnswerPost answer2 = createAndSaveAnswerPost(post);

            searchableEntityWeaviateService.upsertPostAsync(PostSearchableEntityDTO.fromPost(post, channel));
            searchableEntityWeaviateService.upsertAnswerPostAsync(AnswerPostSearchableEntityDTO.fromAnswerPost(answer1, channel));
            searchableEntityWeaviateService.upsertAnswerPostAsync(AnswerPostSearchableEntityDTO.fromAnswerPost(answer2, channel));
            assertPostExistsInWeaviate(weaviateService, post.getId());
            assertAnswerPostExistsInWeaviate(weaviateService, answer1.getId());
            assertAnswerPostExistsInWeaviate(weaviateService, answer2.getId());

            long answer1Id = answer1.getId();
            long answer2Id = answer2.getId();
            searchableEntityWeaviateService.deleteAllAnswerPostsForPostAsync(post.getId());

            assertAnswerPostNotInWeaviate(weaviateService, answer1Id);
            assertAnswerPostNotInWeaviate(weaviateService, answer2Id);
            // The parent post itself should still exist
            assertPostExistsInWeaviate(weaviateService, post.getId());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testDeleteMessage_removesAnswerPostsFromWeaviate() throws Exception {
            Channel channel = createPublicChannel("delete-msg-answers-test");
            Post post = createAndSavePost(channel);
            AnswerPost answer1 = createAndSaveAnswerPost(post);
            AnswerPost answer2 = createAndSaveAnswerPost(post);

            searchableEntityWeaviateService.upsertPostAsync(PostSearchableEntityDTO.fromPost(post, channel));
            searchableEntityWeaviateService.upsertAnswerPostAsync(AnswerPostSearchableEntityDTO.fromAnswerPost(answer1, channel));
            searchableEntityWeaviateService.upsertAnswerPostAsync(AnswerPostSearchableEntityDTO.fromAnswerPost(answer2, channel));
            assertPostExistsInWeaviate(weaviateService, post.getId());
            assertAnswerPostExistsInWeaviate(weaviateService, answer1.getId());
            assertAnswerPostExistsInWeaviate(weaviateService, answer2.getId());

            long postId = post.getId();
            long answer1Id = answer1.getId();
            long answer2Id = answer2.getId();
            request.delete("/api/communication/courses/" + course.getId() + "/messages/" + postId, HttpStatus.OK);

            assertPostNotInWeaviate(weaviateService, postId);
            assertAnswerPostNotInWeaviate(weaviateService, answer1Id);
            assertAnswerPostNotInWeaviate(weaviateService, answer2Id);
        }
    }
}
