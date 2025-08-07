package de.tum.cit.aet.artemis.communication;

import static de.tum.cit.aet.artemis.core.config.Constants.VOTE_EMOJI_ID;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.PostSortCriterion;
import de.tum.cit.aet.artemis.communication.domain.Reaction;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.dto.ReactionDTO;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.communication.test_repository.ReactionTestRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.SortingOrder;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ReactionIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "reactionintegration";

    @Autowired
    private ReactionTestRepository reactionRepository;

    @Autowired
    private PostTestRepository postRepository;

    @Autowired
    private ConversationUtilService conversationUtilService;

    private List<Post> existingPostsWithAnswers;

    private List<Post> existingConversationPosts;

    private List<AnswerPost> existingAnswerPosts;

    private List<Long> existingCourseWideChannelIds;

    private List<Long> existingConversationIds;

    private Long courseId;

    private Course course;

    private Validator validator;

    private ValidatorFactory validatorFactory;

    private static final int MAX_POSTS_PER_PAGE = 20;

    @BeforeEach
    void initTestCase() {

        // used to test hibernate validation using custom ReactionConstraintValidator
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();

        userUtilService.addUsers(TEST_PREFIX, 5, 5, 4, 4);

        // initialize test setup and get all existing posts with answers (three posts, one in each context, are initialized with one answer each): 3 answers in total (with author
        // student1)
        existingPostsWithAnswers = conversationUtilService.createPostsWithAnswerPostsWithinCourse(courseUtilService.createCourse(), TEST_PREFIX).stream()
                .filter(coursePost -> coursePost.getAnswers() != null && !coursePost.getAnswers().isEmpty()).toList();

        // filters existing posts with conversation
        existingConversationPosts = existingPostsWithAnswers.stream().filter(post -> post.getConversation() != null).toList();

        // get all answerPosts
        existingAnswerPosts = existingPostsWithAnswers.stream().map(Post::getAnswers).flatMap(Collection::stream).toList();

        // filters course wide channels
        existingCourseWideChannelIds = existingPostsWithAnswers.stream().filter(post -> post.getConversation() instanceof Channel channel && channel.getIsCourseWide())
                .map(post -> post.getConversation().getId()).distinct().toList();

        // filters conversation ids
        existingConversationIds = existingPostsWithAnswers.stream().filter(post -> post.getConversation() != null).map(post -> post.getConversation().getId()).distinct().toList();

        course = existingPostsWithAnswers.stream().filter(post -> post.getPlagiarismCase() != null).findFirst().orElseThrow().getPlagiarismCase().getExercise()
                .getCourseViaExerciseGroupOrCourseMember();
        courseId = course.getId();
    }

    @AfterEach
    void tearDown() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    // CREATE

    @ParameterizedTest
    @MethodSource("courseConfigurationProvider")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateOwnPostReaction(CourseInformationSharingConfiguration courseInformationSharingConfiguration, boolean shouldBeAllowed) throws Exception {
        // student 1 is the author of the post and reacts on this post
        Post postReactedOn = existingPostsWithAnswers.getFirst();
        ReactionDTO reactionToSaveOnPost = createReactionDTOOnPost(postReactedOn);

        course.setCourseInformationSharingConfiguration(courseInformationSharingConfiguration);
        courseRepository.save(course);

        if (!shouldBeAllowed) {
            request.postWithResponseBody("/api/communication/courses/" + courseId + "/postings/reactions", reactionToSaveOnPost, ReactionDTO.class, HttpStatus.BAD_REQUEST);
            return;
        }

        ReactionDTO createdReaction = request.postWithResponseBody("/api/communication/courses/" + courseId + "/postings/reactions", reactionToSaveOnPost, ReactionDTO.class,
                HttpStatus.CREATED);

        checkCreatedReaction(reactionToSaveOnPost, createdReaction);
        assertThat(postReactedOn.getReactions()).hasSize(reactionRepository.findReactionsByPostId(postReactedOn.getId()).size() - 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateVoteReaction() throws Exception {
        // student 1 is the author of the post and reacts on this post
        Post postReactedOn = existingPostsWithAnswers.getFirst();
        ReactionDTO reactionToSaveOnPost = createVoteReactionDTOOnPost(postReactedOn, null);

        ReactionDTO createdReaction = request.postWithResponseBody("/api/communication/courses/" + courseId + "/postings/reactions", reactionToSaveOnPost, ReactionDTO.class,
                HttpStatus.CREATED);
        checkCreatedReaction(reactionToSaveOnPost, createdReaction);
        assertThat(postReactedOn.getReactions()).hasSize(reactionRepository.findReactionsByPostId(postReactedOn.getId()).size() - 1);
    }

    @ParameterizedTest
    @MethodSource("courseConfigurationProvider")
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "USER")
    void testCreateOwnPostReactionOnAnotherUsersConversationMessage(CourseInformationSharingConfiguration courseInformationSharingConfiguration, boolean shouldBeAllowed)
            throws Exception {
        // tutor1 is the author of the message and tutor2 reacts on this post
        Post messageReactedOn = existingConversationPosts.getFirst();
        ReactionDTO reactionToSaveOnMessage = createReactionDTOOnPost(messageReactedOn);

        course.setCourseInformationSharingConfiguration(courseInformationSharingConfiguration);
        courseRepository.save(course);

        if (!shouldBeAllowed) {
            request.postWithResponseBody("/api/communication/courses/" + courseId + "/postings/reactions", reactionToSaveOnMessage, ReactionDTO.class, HttpStatus.BAD_REQUEST);
            return;
        }

        ReactionDTO createdReaction = request.postWithResponseBody("/api/communication/courses/" + courseId + "/postings/reactions", reactionToSaveOnMessage, ReactionDTO.class,
                HttpStatus.CREATED);
        checkCreatedReaction(reactionToSaveOnMessage, createdReaction);
        assertThat(messageReactedOn.getReactions()).hasSize(reactionRepository.findReactionsByPostId(messageReactedOn.getId()).size() - 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void testCreateReactionOnConversationBetweenOtherUsers_forbidden() throws Exception {
        // student 1 is the author of the message between student1 & student2 and student3 not part of the conversation tries to react on it
        Post messageReactedOn = existingConversationPosts.get(2);
        ReactionDTO reactionToSaveOnMessage = createReactionDTOOnPost(messageReactedOn);

        ReactionDTO notCreatedReaction = request.postWithResponseBody("/api/communication/courses/" + courseId + "/postings/reactions", reactionToSaveOnMessage, ReactionDTO.class,
                HttpStatus.FORBIDDEN);
        assertThat(notCreatedReaction).isNull();
        assertThat(messageReactedOn.getReactions()).hasSize(reactionRepository.findReactionsByPostId(messageReactedOn.getId()).size());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateMultipleOwnPostReaction_internalServerError() throws Exception {
        // student 1 is the author of the post and reacts on this post
        Post postReactedOn = existingPostsWithAnswers.getFirst();
        ReactionDTO reactionToSaveOnPost = createReactionDTOOnPost(postReactedOn);
        ReactionDTO createdReaction = request.postWithResponseBody("/api/communication/courses/" + courseId + "/postings/reactions", reactionToSaveOnPost, ReactionDTO.class,
                HttpStatus.CREATED);
        checkCreatedReaction(reactionToSaveOnPost, createdReaction);
        assertThat(postReactedOn.getReactions()).hasSize(reactionRepository.findReactionsByPostId(postReactedOn.getId()).size() - 1);

        // try again: the post "silently" fails with a 200
        var response = request.postWithResponseBody("/api/communication/courses/" + courseId + "/postings/reactions", reactionToSaveOnPost, ReactionDTO.class, HttpStatus.OK);
        assertThat(response).isNull();
    }

    @ParameterizedTest
    @MethodSource("courseConfigurationProvider")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateOwnAnswerPostReaction(CourseInformationSharingConfiguration courseInformationSharingConfiguration, boolean shouldBeAllowed) throws Exception {
        // student 1 is the author of the answer post and reacts on this answer post
        AnswerPost answerPostReactedOn = existingAnswerPosts.getFirst();
        ReactionDTO reactionToSaveOnAnswerPost = createReactionDTOOnAnswerPost(answerPostReactedOn);

        course.setCourseInformationSharingConfiguration(courseInformationSharingConfiguration);
        courseRepository.save(course);

        if (!shouldBeAllowed) {
            request.postWithResponseBody("/api/communication/courses/" + courseId + "/postings/reactions", reactionToSaveOnAnswerPost, ReactionDTO.class, HttpStatus.BAD_REQUEST);
            return;
        }

        ReactionDTO createdReaction = request.postWithResponseBody("/api/communication/courses/" + courseId + "/postings/reactions", reactionToSaveOnAnswerPost, ReactionDTO.class,
                HttpStatus.CREATED);
        checkCreatedReaction(reactionToSaveOnAnswerPost, createdReaction);
        assertThat(answerPostReactedOn.getReactions()).hasSize(reactionRepository.findReactionsByAnswerPostId(answerPostReactedOn.getId()).size() - 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateMultipleOwnAnswerPostReaction_internalServerError() throws Exception {
        // student 1 is the author of the answer post and reacts on this answer post
        AnswerPost answerPostReactedOn = existingAnswerPosts.getFirst();
        ReactionDTO reactionToSaveOnAnswerPost = createReactionDTOOnAnswerPost(answerPostReactedOn);
        // First attempt should create a reaction
        ReactionDTO createdReaction = request.postWithResponseBody("/api/communication/courses/" + courseId + "/postings/reactions", reactionToSaveOnAnswerPost, ReactionDTO.class,
                HttpStatus.CREATED);

        checkCreatedReaction(reactionToSaveOnAnswerPost, createdReaction);
        assertThat(answerPostReactedOn.getReactions()).hasSize(reactionRepository.findReactionsByAnswerPostId(answerPostReactedOn.getId()).size() - 1);
        // Try again: the endpoint should "silently" fail with a 200 OK and no body
        var response = request.postWithResponseBody("/api/communication/courses/" + courseId + "/postings/reactions", reactionToSaveOnAnswerPost, ReactionDTO.class, HttpStatus.OK);
        assertThat(response).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testCreatePostReactions() throws Exception {
        // student 1 is the author of the post and student 2 reacts on this post
        AnswerPost answerPostReactedOn = existingAnswerPosts.getFirst();
        ReactionDTO reactionToSaveOnAnswerPost = createReactionDTOOnAnswerPost(answerPostReactedOn);
        // Create first reaction
        ReactionDTO createdFirstReaction = request.postWithResponseBody("/api/communication/courses/" + courseId + "/postings/reactions", reactionToSaveOnAnswerPost,
                ReactionDTO.class, HttpStatus.CREATED);
        checkCreatedReaction(reactionToSaveOnAnswerPost, createdFirstReaction);
        assertThat(answerPostReactedOn.getReactions()).hasSize(reactionRepository.findReactionsByAnswerPostId(answerPostReactedOn.getId()).size() - 1);

        // student 2 reacts again on this answer post
        // change the emojiId to react differently
        reactionToSaveOnAnswerPost = createCryReactionDTOOnAnswerPost(answerPostReactedOn);

        ReactionDTO createdSecondReaction = request.postWithResponseBody("/api/communication/courses/" + courseId + "/postings/reactions", reactionToSaveOnAnswerPost,
                ReactionDTO.class, HttpStatus.CREATED);
        checkCreatedReaction(reactionToSaveOnAnswerPost, createdSecondReaction);
        assertThat(answerPostReactedOn.getReactions()).hasSize(reactionRepository.findReactionsByAnswerPostId(answerPostReactedOn.getId()).size() - 2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testCreateAnswerPostReactions() throws Exception {
        // student 1 is the author of the answer post and student 2 reacts on this answer post
        AnswerPost answerPostReactedOn = existingAnswerPosts.getFirst();
        long answerPostId = answerPostReactedOn.getId();
        // First reaction
        ReactionDTO reactionToSave = createReactionDTOOnAnswerPost(answerPostReactedOn);
        ReactionDTO createdFirstReaction = request.postWithResponseBody("/api/communication/courses/" + courseId + "/postings/reactions", reactionToSave, ReactionDTO.class,
                HttpStatus.CREATED);
        checkCreatedReaction(reactionToSave, createdFirstReaction);
        assertThat(answerPostReactedOn.getReactions()).hasSize(reactionRepository.findReactionsByAnswerPostId(answerPostReactedOn.getId()).size() - 1);
        // Second reaction with a different emoji
        ReactionDTO secondReactionToSave = createCryReactionDTOOnAnswerPost(answerPostReactedOn);
        ReactionDTO createdSecondReaction = request.postWithResponseBody("/api/communication/courses/" + courseId + "/postings/reactions", secondReactionToSave, ReactionDTO.class,
                HttpStatus.CREATED);
        checkCreatedReaction(secondReactionToSave, createdSecondReaction);
        assertThat(answerPostReactedOn.getReactions()).hasSize(reactionRepository.findReactionsByAnswerPostId(answerPostReactedOn.getId()).size() - 2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateExistingReaction_badRequest() throws Exception {
        // student 1 is the author of the answer post and reacts on this answer post
        AnswerPost answerPostReactedOn = existingAnswerPosts.getFirst();
        // Create the ReactionDTO to send
        ReactionDTO reactionDTO = createReactionDTOOnAnswerPost(answerPostReactedOn);
        // First create (should succeed)
        ReactionDTO createdReaction = request.postWithResponseBody("/api/communication/courses/" + courseId + "/postings/reactions", reactionDTO, ReactionDTO.class,
                HttpStatus.CREATED);
        checkCreatedReaction(reactionDTO, createdReaction);
        ReactionDTO duplicateReaction = request.postWithResponseBody("/api/communication/courses/" + courseId + "/postings/reactions", reactionDTO, ReactionDTO.class,
                HttpStatus.OK);
        assertThat(duplicateReaction).isNull();
        assertThat(answerPostReactedOn.getReactions()).hasSize(reactionRepository.findReactionsByAnswerPostId(answerPostReactedOn.getId()).size() - 1);
    }

    // GET

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetPostsForCourse_OrderByCreationDateDESC() throws Exception {
        PostSortCriterion sortCriterion = PostSortCriterion.CREATION_DATE;
        SortingOrder sortingOrder = SortingOrder.DESCENDING;

        User student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");

        // student 1 is the author of the post and reacts on this post
        Post postReactedOn = existingPostsWithAnswers.getFirst();
        createVoteReactionDTOOnPost(postReactedOn, student1);

        Post postReactedOn2 = existingPostsWithAnswers.get(1);
        createVoteReactionDTOOnPost(postReactedOn2, student1);
        createVoteReactionDTOOnPost(postReactedOn2, student2);

        var params = new LinkedMultiValueMap<String, String>();

        // ordering only available in course discussions page, where paging is enabled
        params.add("pagingEnabled", "true");
        params.add("page", "0");
        params.add("size", String.valueOf(MAX_POSTS_PER_PAGE));

        params.add("postSortCriterion", sortCriterion.toString());
        params.add("sortingOrder", sortingOrder.toString());
        params.add("filterToCourseWide", "true");
        params.add("conversationIds", existingCourseWideChannelIds.stream().map(String::valueOf).collect(Collectors.joining(",")));

        List<Post> returnedPosts = request.getList("/api/communication/courses/" + courseId + "/messages", HttpStatus.OK, Post.class, params);

        Long numberOfMaxVotesSeenOnAnyPost = Long.MAX_VALUE;
        for (Post post : returnedPosts) {
            Long numberOfVotes = post.getReactions().stream().filter(reaction -> reaction.getEmojiId().equals(VOTE_EMOJI_ID)).count();
            assertThat(numberOfVotes).isLessThanOrEqualTo(numberOfMaxVotesSeenOnAnyPost);
            numberOfMaxVotesSeenOnAnyPost = numberOfVotes;
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetPostsForCourse_OrderByCreationDateASC() throws Exception {
        PostSortCriterion sortCriterion = PostSortCriterion.CREATION_DATE;
        SortingOrder sortingOrder = SortingOrder.ASCENDING;

        User student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");

        Post postReactedOn = existingPostsWithAnswers.getFirst();
        createVoteReactionDTOOnPost(postReactedOn, student1);
        createVoteReactionDTOOnPost(postReactedOn, student2);

        Post post2ReactedOn = existingPostsWithAnswers.get(1);
        createVoteReactionDTOOnPost(post2ReactedOn, student2);

        var params = new LinkedMultiValueMap<String, String>();

        // ordering only available in course discussions page, where paging is enabled
        params.add("pagingEnabled", "true");
        params.add("page", "0");
        params.add("size", String.valueOf(MAX_POSTS_PER_PAGE));

        params.add("postSortCriterion", sortCriterion.toString());
        params.add("sortingOrder", sortingOrder.toString());
        params.add("filterToCourseWide", "true");
        params.add("conversationIds", existingCourseWideChannelIds.stream().map(String::valueOf).collect(Collectors.joining(",")));

        List<Post> returnedPosts = request.getList("/api/communication/courses/" + courseId + "/messages", HttpStatus.OK, Post.class, params);

        Long numberOfMaxVotesSeenOnAnyPost = 0L;
        for (Post post : returnedPosts) {
            Long numberOfVotes = post.getReactions().stream().filter(reaction -> reaction.getEmojiId().equals(VOTE_EMOJI_ID)).count();
            assertThat(numberOfVotes).isGreaterThanOrEqualTo(numberOfMaxVotesSeenOnAnyPost);
            numberOfMaxVotesSeenOnAnyPost = numberOfVotes;
        }
    }

    // DELETE

    @ParameterizedTest
    @MethodSource("courseConfigurationProvider")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDeleteOwnPostReaction(CourseInformationSharingConfiguration courseInformationSharingConfiguration, boolean shouldBeAllowed) throws Exception {
        // student 1 is the author of the post and reacts on this post
        Post postReactedOn = existingPostsWithAnswers.getFirst();
        ReactionDTO reactionToSaveOnPost = createReactionDTOOnPost(postReactedOn);

        ReactionDTO reactionToBeDeleted = request.postWithResponseBody("/api/communication/courses/" + courseId + "/postings/reactions", reactionToSaveOnPost, ReactionDTO.class,
                HttpStatus.CREATED);

        course.setCourseInformationSharingConfiguration(courseInformationSharingConfiguration);
        courseRepository.save(course);

        // student 1 deletes their reaction on this post
        if (!shouldBeAllowed) {
            request.delete("/api/communication/courses/" + courseId + "/postings/reactions/" + reactionToBeDeleted.id(), HttpStatus.BAD_REQUEST);
            return;
        }

        request.delete("/api/communication/courses/" + courseId + "/postings/reactions/" + reactionToBeDeleted.id(), HttpStatus.OK);

        assertThat(postReactedOn.getReactions()).hasSameSizeAs(reactionRepository.findReactionsByPostId(postReactedOn.getId()));
        assertThat(reactionRepository.findById(reactionToBeDeleted.id())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDeleteOwnVoteReaction() throws Exception {
        // student 1 is the author of the post and reacts on this post
        Post postReactedOn = existingPostsWithAnswers.getFirst();
        ReactionDTO reactionToSaveOnPost = createVoteReactionDTOOnPost(postReactedOn, null);

        // Use DTO for the response type
        ReactionDTO reactionToBeDeleted = request.postWithResponseBody("/api/communication/courses/" + courseId + "/postings/reactions", reactionToSaveOnPost, ReactionDTO.class,
                HttpStatus.CREATED);

        // student 1 deletes their reaction on this post
        request.delete("/api/communication/courses/" + courseId + "/postings/reactions/" + reactionToBeDeleted.id(), HttpStatus.OK);

        assertThat(postReactedOn.getReactions()).hasSameSizeAs(reactionRepository.findReactionsByPostId(postReactedOn.getId()));
        assertThat(reactionRepository.findById(reactionToBeDeleted.id())).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("courseConfigurationProvider")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDeleteOwnAnswerPostReaction(CourseInformationSharingConfiguration courseInformationSharingConfiguration, boolean shouldBeAllowed) throws Exception {
        // student 1 is the author of the post and reacts on this post
        AnswerPost answerPostReactedOn = existingAnswerPosts.getFirst();
        ReactionDTO reactionToSaveOnAnswerPost = createReactionDTOOnAnswerPost(answerPostReactedOn);

        // Create the reaction (expect DTO in response)
        ReactionDTO reactionToBeDeleted = request.postWithResponseBody("/api/communication/courses/" + courseId + "/postings/reactions", reactionToSaveOnAnswerPost,
                ReactionDTO.class, HttpStatus.CREATED);

        course.setCourseInformationSharingConfiguration(courseInformationSharingConfiguration);
        courseRepository.save(course);

        // student 1 deletes their reaction on this post
        if (!shouldBeAllowed) {
            request.delete("/api/communication/courses/" + courseId + "/postings/reactions/" + reactionToBeDeleted.id(), HttpStatus.BAD_REQUEST);
            return;
        }
        request.delete("/api/communication/courses/" + courseId + "/postings/reactions/" + reactionToBeDeleted.id(), HttpStatus.OK);
        assertThat(answerPostReactedOn.getReactions()).hasSameSizeAs(reactionRepository.findReactionsByPostId(answerPostReactedOn.getId()));
        assertThat(reactionRepository.findById(reactionToBeDeleted.id())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDeletePostReactionOfOthers_forbidden() throws Exception {
        // student 1 is the author of the post and student 2 reacts on this post
        Post postReactedOn = existingPostsWithAnswers.getFirst();
        Reaction reactionSaveOnPost = saveReactionOfOtherUserOnPost(postReactedOn, TEST_PREFIX);

        // student 1 wants to delete the reaction of student 2
        request.delete("/api/communication/courses/" + courseId + "/postings/reactions/" + reactionSaveOnPost.getId(), HttpStatus.FORBIDDEN);
        assertThat(postReactedOn.getReactions()).hasSize(reactionRepository.findReactionsByPostId(postReactedOn.getId()).size() - 1);
    }

    @ParameterizedTest
    @MethodSource("courseConfigurationProvider")
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testDeletePostReactionWithWrongCourseId_badRequest(CourseInformationSharingConfiguration courseInformationSharingConfiguration) throws Exception {
        Course dummyCourse = courseUtilService.createCourse();
        Post postToReactOn = existingPostsWithAnswers.getFirst();
        ReactionDTO reactionToSaveOnPost = createReactionDTOOnPost(postToReactOn);
        ReactionDTO createdReaction = request.postWithResponseBody("/api/communication/courses/" + courseId + "/postings/reactions", reactionToSaveOnPost, ReactionDTO.class,
                HttpStatus.CREATED);
        course.setCourseInformationSharingConfiguration(courseInformationSharingConfiguration);
        courseRepository.save(course);

        checkCreatedReaction(reactionToSaveOnPost, createdReaction);
        int initialCount = postRepository.findById(postToReactOn.getId()).orElseThrow().getReactions().size();
        request.delete("/api/communication/courses/" + dummyCourse.getId() + "/postings/reactions/" + createdReaction.id(), HttpStatus.BAD_REQUEST);
        int countAfterFailedDelete = postRepository.findById(postToReactOn.getId()).orElseThrow().getReactions().size();
        assertThat(countAfterFailedDelete).isEqualTo(initialCount);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testDeletePostReaction() throws Exception {
        // student 1 is the author of the post and student 2 reacts on this post
        Post postReactedOn = existingPostsWithAnswers.getFirst();
        ReactionDTO reactionToSaveOnPost = createReactionDTOOnPost(postReactedOn);

        // Create the reaction via the API (returns DTO with id)
        ReactionDTO createdReaction = request.postWithResponseBody("/api/communication/courses/" + courseId + "/postings/reactions", reactionToSaveOnPost, ReactionDTO.class,
                HttpStatus.CREATED);
        // student 2 deletes their reaction on this post
        request.delete("/api/communication/courses/" + courseId + "/postings/reactions/" + createdReaction.id(), HttpStatus.OK);
        assertThat(postReactedOn.getReactions()).hasSameSizeAs(reactionRepository.findReactionsByPostId(postReactedOn.getId()));
        assertThat(reactionRepository.findById(createdReaction.id())).isEmpty();
    }

    // HELPER METHODS

    private ReactionDTO createReactionDTOOnPost(Post postReactedOn) {
        Reaction reaction = new Reaction();
        reaction.setEmojiId("smiley");
        reaction.setPost(postReactedOn);
        return new ReactionDTO(reaction);
    }

    private ReactionDTO createVoteReactionDTOOnPost(Post postReactedOn, User user) {
        Reaction reaction = new Reaction();
        reaction.setUser(user);
        reaction.setEmojiId(VOTE_EMOJI_ID);
        reaction.setPost(postReactedOn);
        return new ReactionDTO(reaction);
    }

    private Reaction saveReactionOfOtherUserOnPost(Post postReactedOn, String userPrefix) {
        Reaction reaction = new Reaction();
        reaction.setEmojiId("smiley");
        reaction.setPost(postReactedOn);
        Reaction savedReaction = reactionRepository.save(reaction);
        User user = userTestRepository.getUserWithGroupsAndAuthorities(userPrefix + "student2");
        savedReaction.setUser(user);
        reactionRepository.save(savedReaction);
        return savedReaction;
    }

    private ReactionDTO createReactionDTOOnAnswerPost(AnswerPost answerPostReactedOn) {
        Reaction reaction = new Reaction();
        reaction.setEmojiId("smiley");
        reaction.setAnswerPost(answerPostReactedOn);
        return new ReactionDTO(reaction);
    }

    private ReactionDTO createCryReactionDTOOnAnswerPost(AnswerPost answerPostReactedOn) {
        Reaction reaction = new Reaction();
        reaction.setEmojiId("cry");
        reaction.setAnswerPost(answerPostReactedOn);
        return new ReactionDTO(reaction);
    }

    private void checkCreatedReaction(ReactionDTO expectedReaction, ReactionDTO createdReaction) {
        // check if post was created with id
        assertThat(createdReaction).isNotNull();
        assertThat(createdReaction.id()).isNotNull();

        // check if emojiId and creation data are set correctly on creation
        assertThat(createdReaction.emojiId()).isEqualTo(expectedReaction.emojiId());
        assertThat(createdReaction.creationDate()).isNotNull();

        // check if association to post or answer post is correct
        assertThat(createdReaction.relatedPostId()).isEqualTo(expectedReaction.relatedPostId());
    }

    private static List<Arguments> courseConfigurationProvider() {
        return List.of(Arguments.of(CourseInformationSharingConfiguration.DISABLED, false), Arguments.of(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING, true),
                Arguments.of(CourseInformationSharingConfiguration.COMMUNICATION_ONLY, true));
    }
}
