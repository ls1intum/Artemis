package de.tum.cit.aet.artemis.globalsearch;

import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertChannelExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertPostExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertPostNotInWeaviate;
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

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationFactory;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.PostSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

/**
 * Integration tests verifying that resetting a course removes all post entries from Weaviate
 * while preserving channel (structure) entries.
 * <p>
 * Tests are skipped when Docker is not available or the Weaviate container failed to start.
 */
@EnabledIf("isWeaviateEnabled")
class CourseResetWeaviateIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "crsweaviateint";

    @Autowired
    private SearchableEntityWeaviateService searchableEntityWeaviateService;

    @Autowired
    private WeaviateService weaviateService;

    @Autowired
    private ChannelService channelService;

    @Autowired
    private PostTestRepository postRepository;

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

    private Post createAndSavePost(Channel channel) {
        Post post = ConversationFactory.createBasicPost(0, instructor);
        post.setConversation(channel);
        return postRepository.save(post);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testResetCourse_removesPostsFromWeaviate() throws Exception {
        Channel channel = createPublicChannel("reset-posts-test");
        Post post1 = createAndSavePost(channel);
        Post post2 = createAndSavePost(channel);

        searchableEntityWeaviateService.upsertPostAsync(PostSearchableEntityDTO.fromPost(post1, channel));
        searchableEntityWeaviateService.upsertPostAsync(PostSearchableEntityDTO.fromPost(post2, channel));
        assertPostExistsInWeaviate(weaviateService, post1.getId());
        assertPostExistsInWeaviate(weaviateService, post2.getId());

        long post1Id = post1.getId();
        long post2Id = post2.getId();
        request.postWithoutResponseBody("/api/core/admin/courses/" + course.getId() + "/reset", null, HttpStatus.OK);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertPostNotInWeaviate(weaviateService, post1Id);
            assertPostNotInWeaviate(weaviateService, post2Id);
        });
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testResetCourse_preservesChannelInWeaviate() throws Exception {
        Channel channel = createPublicChannel("reset-channel-preserved-test");
        Post post = createAndSavePost(channel);

        searchableEntityWeaviateService.upsertPostAsync(PostSearchableEntityDTO.fromPost(post, channel));
        assertPostExistsInWeaviate(weaviateService, post.getId());
        assertChannelExistsInWeaviate(weaviateService, channel);

        long postId = post.getId();
        request.postWithoutResponseBody("/api/core/admin/courses/" + course.getId() + "/reset", null, HttpStatus.OK);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertPostNotInWeaviate(weaviateService, postId);
            assertChannelExistsInWeaviate(weaviateService, channel);
        });
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testResetCourse_onlyRemovesPostsFromResetCourse_notOtherCourses() throws Exception {
        Channel channel = createPublicChannel("reset-isolation-test");
        Post post = createAndSavePost(channel);

        Course otherCourse = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        User otherInstructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        Channel otherChannel = new Channel();
        otherChannel.setName("other-course-channel");
        otherChannel.setIsPublic(true);
        otherChannel.setIsCourseWide(false);
        otherChannel.setIsAnnouncementChannel(false);
        Channel createdOtherChannel = channelService.createChannel(otherCourse, otherChannel, Optional.of(otherInstructor));
        Post otherPost = createAndSavePost(createdOtherChannel);

        searchableEntityWeaviateService.upsertPostAsync(PostSearchableEntityDTO.fromPost(post, channel));
        searchableEntityWeaviateService.upsertPostAsync(PostSearchableEntityDTO.fromPost(otherPost, createdOtherChannel));
        assertPostExistsInWeaviate(weaviateService, post.getId());
        assertPostExistsInWeaviate(weaviateService, otherPost.getId());

        long postId = post.getId();
        long otherPostId = otherPost.getId();
        request.postWithoutResponseBody("/api/core/admin/courses/" + course.getId() + "/reset", null, HttpStatus.OK);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertPostNotInWeaviate(weaviateService, postId);
            assertPostExistsInWeaviate(weaviateService, otherPostId);
        });
    }
}
