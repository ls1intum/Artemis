package de.tum.cit.aet.artemis.communication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.Reaction;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.dto.ReactionDTO;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ConversationService;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.communication.test_repository.ReactionTestRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.plagiarism.api.PlagiarismPostApi;

/**
 * Covers how {@link ReactionService} decides which posting a reaction belongs to.
 *
 * <p>
 * Posts and answer posts are numbered by separate identity columns, so the same id regularly denotes one of each. Constructing that collision
 * inside one course through the REST fixtures is not possible — the two sequences cannot be steered independently — so the ambiguous case is
 * pinned down here instead.
 */
@ExtendWith(MockitoExtension.class)
class ReactionServiceUnitTest {

    private static final long COURSE_ID = 7L;

    private static final long SHARED_ID = 3L;

    private ReactionService reactionService;

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private CourseTestRepository courseRepository;

    @Mock
    private ReactionTestRepository reactionRepository;

    @Mock
    private ConversationService conversationService;

    @Mock
    private PostTestRepository postRepository;

    @Mock
    private AnswerPostRepository answerPostRepository;

    @Mock
    private PlagiarismPostApi plagiarismPostApi;

    private Course course;

    @BeforeEach
    void setUp() {
        reactionService = new ReactionService(userRepository, courseRepository, reactionRepository, Optional.of(plagiarismPostApi), conversationService, postRepository,
                answerPostRepository);

        course = new Course();
        course.setId(COURSE_ID);

        when(courseRepository.findByIdElseThrow(COURSE_ID)).thenReturn(course);
        when(userRepository.getUserWithAuthorities()).thenReturn(new User());
    }

    @Test
    void anIdThatDenotesBothAPostAndAnAnswerPostInTheCourseIsRejectedWithoutAType() {
        when(postRepository.findById(SHARED_ID)).thenReturn(Optional.of(postInCourse()));
        when(answerPostRepository.findById(SHARED_ID)).thenReturn(Optional.of(answerPostInCourse()));

        ReactionDTO withoutType = new ReactionDTO(null, null, null, "smiley", SHARED_ID, null);

        assertThatThrownBy(() -> reactionService.createReaction(COURSE_ID, withoutType)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("postingType is required");
    }

    @Test
    void anIdThatDenotesAnAnswerPostInAnotherCourseStillResolvesThePostInThisOne() {
        // The failure this guards against: the answer post was looked up first and its course compared against the request, so reacting to a post
        // whose id happened to exist in answer_post failed with "Reaction does not belong to the given course".
        Post post = postInCourse();

        when(postRepository.findById(SHARED_ID)).thenReturn(Optional.of(post));
        when(answerPostRepository.findById(SHARED_ID)).thenReturn(Optional.of(answerPostInCourse(otherCourse())));
        when(plagiarismPostApi.findPostOrMessagePostById(SHARED_ID)).thenReturn(post);
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reactionRepository.save(any(Reaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReactionDTO withoutType = new ReactionDTO(null, null, null, "smiley", SHARED_ID, null);

        Reaction created = reactionService.createReaction(COURSE_ID, withoutType);

        assertThat(created.getPost()).isSameAs(post);
        assertThat(created.getAnswerPost()).isNull();
    }

    @Test
    void anIdThatDenotesNoPostingAtAllIsNotFound() {
        when(postRepository.findById(SHARED_ID)).thenReturn(Optional.empty());
        when(answerPostRepository.findById(SHARED_ID)).thenReturn(Optional.empty());

        ReactionDTO withoutType = new ReactionDTO(null, null, null, "smiley", SHARED_ID, null);

        assertThatThrownBy(() -> reactionService.createReaction(COURSE_ID, withoutType)).isInstanceOf(EntityNotFoundException.class).hasMessageContaining("Posting")
                .hasMessageContaining(String.valueOf(SHARED_ID));
    }

    @Test
    void anIdThatDenotesAPostingOnlyInAnotherCourseIsRejectedAsWrongCourse() {
        when(postRepository.findById(SHARED_ID)).thenReturn(Optional.empty());
        when(answerPostRepository.findById(SHARED_ID)).thenReturn(Optional.of(answerPostInCourse(otherCourse())));

        ReactionDTO withoutType = new ReactionDTO(null, null, null, "smiley", SHARED_ID, null);

        assertThatThrownBy(() -> reactionService.createReaction(COURSE_ID, withoutType)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("does not belong to the given course");
    }

    private Post postInCourse() {
        Post post = new Post();
        post.setId(SHARED_ID);
        Channel channel = new Channel();
        channel.setCourse(course);
        post.setConversation(channel);
        return post;
    }

    private AnswerPost answerPostInCourse() {
        return answerPostInCourse(course);
    }

    private AnswerPost answerPostInCourse(Course owningCourse) {
        AnswerPost answerPost = new AnswerPost();
        answerPost.setId(SHARED_ID);
        Post parent = new Post();
        Channel channel = new Channel();
        channel.setCourse(owningCourse);
        parent.setConversation(channel);
        answerPost.setPost(parent);
        return answerPost;
    }

    private Course otherCourse() {
        Course other = new Course();
        other.setId(COURSE_ID + 1);
        return other;
    }
}
