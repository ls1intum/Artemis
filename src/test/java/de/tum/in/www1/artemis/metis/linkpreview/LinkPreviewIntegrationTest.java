package de.tum.in.www1.artemis.metis.linkpreview;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.post.ConversationUtilService;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.metis.AnswerPostRepository;
import de.tum.in.www1.artemis.user.UserUtilService;

class LinkPreviewIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "linkpreviewintegrationtest";

    @Autowired
    private AnswerPostRepository answerPostRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ConversationUtilService conversationUtilService;

    private List<Post> existingConversationPostsWithAnswers;

    private List<Post> existingPostsWithAnswersCourseWide;

    private Long courseId;

    @BeforeEach
    void initTestCase() {

        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);

        // initialize test setup and get all existing posts with answers (four posts, one in each context, are initialized with one answer each): 4 answers in total (with author
        // student1)
        List<Post> existingPostsAndConversationPostsWithAnswers = conversationUtilService.createPostsWithAnswerPostsWithinCourse(TEST_PREFIX).stream()
                .filter(coursePost -> (coursePost.getAnswers() != null)).toList();

        List<Post> existingPostsWithAnswers = existingPostsAndConversationPostsWithAnswers.stream().filter(post -> post.getConversation() == null).toList();

        existingConversationPostsWithAnswers = existingPostsAndConversationPostsWithAnswers.stream().filter(post -> post.getConversation() != null).toList();

        // get all existing posts with answers in exercise context
        List<Post> existingPostsWithAnswersInExercise = existingPostsWithAnswers.stream()
                .filter(coursePost -> (coursePost.getAnswers() != null) && coursePost.getExercise() != null).toList();

        // get all existing posts with answers in lecture context
        existingPostsWithAnswersCourseWide = existingPostsWithAnswers.stream().filter(coursePost -> (coursePost.getAnswers() != null) && coursePost.getCourseWideContext() != null)
                .toList();

        Course course = existingPostsWithAnswersInExercise.get(0).getExercise().getCourseViaExerciseGroupOrCourseMember();
        courseUtilService.enableMessagingForCourse(course);
        courseId = course.getId();

        SimpMessageSendingOperations simpMessageSendingOperations = mock(SimpMessageSendingOperations.class);
        doNothing().when(simpMessageSendingOperations).convertAndSendToUser(any(), any(), any());
    }
}
