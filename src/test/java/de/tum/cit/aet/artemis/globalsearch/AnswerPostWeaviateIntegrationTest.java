package de.tum.cit.aet.artemis.globalsearch;

import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertAnswerPostExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertAnswerPostNotInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertPostExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertPostNotInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.queryAnswerPostProperties;
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

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.dto.AnswerPostResponseDTO;
import de.tum.cit.aet.artemis.communication.dto.CreateAnswerPostDTO;
import de.tum.cit.aet.artemis.communication.dto.CreatePostConversationDTO;
import de.tum.cit.aet.artemis.communication.dto.CreatePostDTO;
import de.tum.cit.aet.artemis.communication.dto.ParentPostDTO;
import de.tum.cit.aet.artemis.communication.dto.PostResponseDTO;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationFactory;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.AnswerPostSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.PostSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

/**
 * Integration tests for answer post (reply) Weaviate indexing.
 * <p>
 * Verifies that answer posts from public channels are correctly upserted and deleted in Weaviate,
 * and that parent post deletion, channel deletion, and course deletion remove associated answer posts
 * from the index.
 * <p>
 * Tests are skipped when Docker is not available or the Weaviate container failed to start.
 */
@EnabledIf("isWeaviateEnabled")
class AnswerPostWeaviateIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "apweaviateint";

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

    @Autowired
    private ConversationUtilService conversationUtilService;

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

    private Channel createPrivateChannel(String name) {
        Channel channel = new Channel();
        channel.setName(name);
        channel.setIsPublic(false);
        channel.setIsCourseWide(false);
        channel.setIsAnnouncementChannel(false);
        return channelService.createChannel(course, channel, Optional.of(instructor));
    }

    private PostResponseDTO createPostViaApi(Conversation conversation) throws Exception {
        CreatePostDTO postToCreate = new CreatePostDTO("Test message in " + conversation.getClass().getSimpleName(), null, false, new CreatePostConversationDTO(conversation.getId()));
        return request.postWithResponseBody("/api/communication/courses/" + course.getId() + "/messages", postToCreate, PostResponseDTO.class, HttpStatus.CREATED);
    }

    private AnswerPostResponseDTO createAnswerPostViaApi(PostResponseDTO parentPost) throws Exception {
        var dto = new CreateAnswerPostDTO("Reply to " + parentPost.content(), new ParentPostDTO(parentPost.id()));
        return request.postWithResponseBody("/api/communication/courses/" + course.getId() + "/answer-messages", dto, AnswerPostResponseDTO.class, HttpStatus.CREATED);
    }

    @Nested
    class UpsertTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testUpsertAnswerPost_indexesInWeaviate() throws Exception {
            Channel channel = createPublicChannel("ap-upsert-test");
            Post post = createAndSavePost(channel);
            AnswerPost answerPost = createAndSaveAnswerPost(post);

            searchableEntityWeaviateService.upsertAnswerPostAsync(AnswerPostSearchableEntityDTO.fromAnswerPost(answerPost, channel));

            assertAnswerPostExistsInWeaviate(weaviateService, answerPost.getId());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testUpsertAnswerPost_storesCorrectProperties() throws Exception {
            Channel channel = createPublicChannel("ap-props-test");
            Post post = createAndSavePost(channel);
            AnswerPost answerPost = createAndSaveAnswerPost(post);

            searchableEntityWeaviateService.upsertAnswerPostAsync(AnswerPostSearchableEntityDTO.fromAnswerPost(answerPost, channel));

            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                var properties = queryAnswerPostProperties(weaviateService, answerPost.getId());
                assertThat(properties).isNotNull();
                assertThat(properties.get(SearchableEntitySchema.Properties.DESCRIPTION)).isEqualTo(answerPost.getContent());
                assertThat(((Number) properties.get(SearchableEntitySchema.Properties.COURSE_ID)).longValue()).isEqualTo(course.getId());
                assertThat(((Number) properties.get(SearchableEntitySchema.Properties.CHANNEL_ID)).longValue()).isEqualTo(channel.getId());
                assertThat(((Number) properties.get(SearchableEntitySchema.Properties.POST_ID)).longValue()).isEqualTo(post.getId());
            });
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testUpsertAnswerPost_updatesContentInWeaviate() throws Exception {
            Channel channel = createPublicChannel("ap-update-test");
            Post post = createAndSavePost(channel);
            AnswerPost answerPost = createAndSaveAnswerPost(post);

            searchableEntityWeaviateService.upsertAnswerPostAsync(AnswerPostSearchableEntityDTO.fromAnswerPost(answerPost, channel));
            assertAnswerPostExistsInWeaviate(weaviateService, answerPost.getId());

            answerPost.setContent("Updated reply content");
            searchableEntityWeaviateService.upsertAnswerPostAsync(AnswerPostSearchableEntityDTO.fromAnswerPost(answerPost, channel));

            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                var properties = queryAnswerPostProperties(weaviateService, answerPost.getId());
                assertThat(properties).isNotNull();
                assertThat(properties.get(SearchableEntitySchema.Properties.DESCRIPTION)).isEqualTo("Updated reply content");
            });
        }
    }

    @Nested
    class DeleteTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testDeleteAnswerPost_removesFromWeaviate() throws Exception {
            Channel channel = createPublicChannel("ap-delete-test");
            Post post = createAndSavePost(channel);
            AnswerPost answerPost = createAndSaveAnswerPost(post);

            searchableEntityWeaviateService.upsertAnswerPostAsync(AnswerPostSearchableEntityDTO.fromAnswerPost(answerPost, channel));
            assertAnswerPostExistsInWeaviate(weaviateService, answerPost.getId());

            long answerPostId = answerPost.getId();
            searchableEntityWeaviateService.deleteEntityAsync(SearchableEntitySchema.TypeValues.ANSWER_POST, answerPostId);

            assertAnswerPostNotInWeaviate(weaviateService, answerPostId);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testDeleteParentPost_removesAnswerPostsFromWeaviate() throws Exception {
            Channel channel = createPublicChannel("ap-parent-del-test");
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

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testDeleteChannel_removesAnswerPostsFromWeaviate() throws Exception {
            Channel channel = createPublicChannel("ap-ch-del-test");
            Post post = createAndSavePost(channel);
            AnswerPost answerPost = createAndSaveAnswerPost(post);

            searchableEntityWeaviateService.upsertAnswerPostAsync(AnswerPostSearchableEntityDTO.fromAnswerPost(answerPost, channel));
            assertAnswerPostExistsInWeaviate(weaviateService, answerPost.getId());

            long answerPostId = answerPost.getId();
            channelService.deleteChannel(channel);

            assertAnswerPostNotInWeaviate(weaviateService, answerPostId);
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void testDeleteCourse_removesAnswerPostsFromWeaviate() throws Exception {
            Channel channel = createPublicChannel("ap-course-del-test");
            Post post = createAndSavePost(channel);
            AnswerPost answerPost = createAndSaveAnswerPost(post);

            searchableEntityWeaviateService.upsertAnswerPostAsync(AnswerPostSearchableEntityDTO.fromAnswerPost(answerPost, channel));
            assertAnswerPostExistsInWeaviate(weaviateService, answerPost.getId());

            long answerPostId = answerPost.getId();
            request.delete("/api/core/admin/courses/" + course.getId(), HttpStatus.OK);

            assertAnswerPostNotInWeaviate(weaviateService, answerPostId);
        }
    }

    @Nested
    class PrivacyFilterTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testCreateAnswerPostInPrivateChannel_notIndexedInWeaviate() throws Exception {
            // Sentinel: create answer post in public channel to confirm Weaviate pipeline works
            Channel publicChannel = createPublicChannel("ap-sentinel-priv");
            PostResponseDTO publicPost = createPostViaApi(publicChannel);
            AnswerPostResponseDTO sentinelAnswer = createAnswerPostViaApi(publicPost);

            // Create answer post in private channel via API
            Channel privateChannel = createPrivateChannel("ap-filter-private");
            PostResponseDTO privatePost = createPostViaApi(privateChannel);
            AnswerPostResponseDTO privateAnswer = createAnswerPostViaApi(privatePost);

            // Wait for sentinel to appear, proving async Weaviate operations have been processed
            assertAnswerPostExistsInWeaviate(weaviateService, sentinelAnswer.id());

            // Assert the private channel answer post was NOT indexed
            assertThat(queryAnswerPostProperties(weaviateService, privateAnswer.id())).isNull();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testCreateAnswerPostInOneToOneChat_notIndexedInWeaviate() throws Exception {
            // Sentinel: create answer post in public channel to confirm Weaviate pipeline works
            Channel publicChannel = createPublicChannel("ap-sentinel-dm");
            PostResponseDTO publicPost = createPostViaApi(publicChannel);
            AnswerPostResponseDTO sentinelAnswer = createAnswerPostViaApi(publicPost);

            // Create answer post in one-to-one chat via API
            User tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
            Conversation oneToOneChat = conversationUtilService.createOneToOneChat(course, instructor, tutor);
            PostResponseDTO dmPost = createPostViaApi(oneToOneChat);
            AnswerPostResponseDTO dmAnswer = createAnswerPostViaApi(dmPost);

            // Wait for sentinel to appear, proving async Weaviate operations have been processed
            assertAnswerPostExistsInWeaviate(weaviateService, sentinelAnswer.id());

            // Assert the direct message answer post was NOT indexed
            assertThat(queryAnswerPostProperties(weaviateService, dmAnswer.id())).isNull();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testCreateAnswerPostInGroupChat_notIndexedInWeaviate() throws Exception {
            // Sentinel: create answer post in public channel to confirm Weaviate pipeline works
            Channel publicChannel = createPublicChannel("ap-sentinel-gc");
            PostResponseDTO publicPost = createPostViaApi(publicChannel);
            AnswerPostResponseDTO sentinelAnswer = createAnswerPostViaApi(publicPost);

            // Create answer post in group chat via API
            User tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
            Conversation groupChat = conversationUtilService.createGroupChat(course, instructor, tutor);
            PostResponseDTO gcPost = createPostViaApi(groupChat);
            AnswerPostResponseDTO gcAnswer = createAnswerPostViaApi(gcPost);

            // Wait for sentinel to appear, proving async Weaviate operations have been processed
            assertAnswerPostExistsInWeaviate(weaviateService, sentinelAnswer.id());

            // Assert the group chat answer post was NOT indexed
            assertThat(queryAnswerPostProperties(weaviateService, gcAnswer.id())).isNull();
        }
    }
}
