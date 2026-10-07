package de.tum.cit.aet.artemis.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.communication.domain.SavedPost;
import de.tum.cit.aet.artemis.communication.service.ConversationMessagingService;
import de.tum.cit.aet.artemis.communication.service.PostingService;
import de.tum.cit.aet.artemis.communication.test_repository.SavedPostTestRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;

class PostingServiceUnitTest {

    @InjectMocks
    private ConversationMessagingService postingService;

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private AuthorizationCheckService authorizationCheckService;

    private Method parseUserMentions;

    private AutoCloseable closeable;

    @Mock
    private SavedPostTestRepository savedPostRepository;

    private User testUser;

    private Post testPost;

    private SavedPost savedPost;

    private SavedPost savedAnswer;

    private SavedPost savedAnswer2;

    @BeforeEach
    void initTestCase() throws NoSuchMethodException {
        closeable = MockitoAnnotations.openMocks(this);

        parseUserMentions = PostingService.class.getDeclaredMethod("parseUserMentions", Course.class, String.class);
        parseUserMentions.setAccessible(true);

        testUser = new User();
        testUser.setId(1L);

        testPost = new Post();
        testPost.setId(2L);

        AnswerPost testAnswer1 = new AnswerPost();
        testAnswer1.setId(3L);

        AnswerPost testAnswer2 = new AnswerPost();
        testAnswer2.setId(4L);

        Set<AnswerPost> answers = new HashSet<>();
        answers.add(testAnswer1);
        answers.add(testAnswer2);
        testPost.setAnswers(answers);

        savedPost = new SavedPost();
        savedPost.setPostId(testPost.getId());
        savedAnswer = new SavedPost();
        savedAnswer.setPostId(testAnswer1.getId());
        savedAnswer2 = new SavedPost();
        savedAnswer2.setPostId(testAnswer2.getId());

        when(userRepository.getUser()).thenReturn(testUser);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    void testParseUserMentionsEmptyContent() throws InvocationTargetException, IllegalAccessException {
        Course course = new Course();
        String content = "";

        parseUserMentions.invoke(postingService, course, content);
    }

    @Test
    void testParseUserMentionsContentNull() throws InvocationTargetException, IllegalAccessException {
        Course course = new Course();

        parseUserMentions.invoke(postingService, course, null);
    }

    @Test
    void testParseUserMentionsNoUserMentioned() throws InvocationTargetException, IllegalAccessException {
        Course course = new Course();
        String content = "This is a regular content without any user mention.";

        parseUserMentions.invoke(postingService, course, content);
    }

    @Test
    void testParseUserMentionsWithValidUsers() throws InvocationTargetException, IllegalAccessException {
        Course course = new Course();
        String content = "[user]Test User 1(test_user_1)[/user] [user]Test User 2(test_user_2)[/user]";
        Set<User> users = Set.of(this.createUser("Test User 1", "test_user_1"), this.createUser("Test User 2", "test_user_2"));

        setupUserRepository(Set.of("test_user_1", "test_user_2"), users);
        when(authorizationCheckService.isAtLeastStudentInCourse(eq(course), any(User.class))).thenReturn(true);

        parseUserMentions.invoke(postingService, course, content);

        verify(userRepository).findAllWithCourseRolesAndAuthoritiesByDeletedIsFalseAndLoginIn(anySet());
        verify(authorizationCheckService, times(2)).isAtLeastStudentInCourse(eq(course), any(User.class));
    }

    @Test
    void testParseUserMentionsWithNonExistentUser() {
        Course course = new Course();
        String content = "[user]Test User 1(test_user_1)[/user] [user]Test User 2(test_user_2)[/user]";
        Set<User> users = Set.of(this.createUser("Test User 1", "test_user_1")); // Return only one user from database

        setupUserRepository(Set.of("test_user_1", "test_user_2"), users);

        actAndAssertInvalidUserMention(course, content);
    }

    @Test
    void testParseUserMentionsWithInvalidName() {
        Course course = new Course();
        String content = "[user]Test User 2(test_user_1)[/user]";
        User user = createUser("Test User 1", "test_user_1");  // Different name than mentioned

        setupUserRepository(Set.of("test_user_1"), Set.of(user));
        when(authorizationCheckService.isAtLeastStudentInCourse(eq(course), any(User.class))).thenReturn(true);

        actAndAssertInvalidUserMention(course, content);
    }

    @Test
    void testParseUserMentionsWithUserNotInCourse() {
        Course course = new Course();
        String content = "[user]Test User 1(test_user_1)[/user]";
        User user = createUser("Test User 1", "test_user_1");

        setupUserRepository(Set.of("test_user_1"), Set.of(user));
        when(authorizationCheckService.isAtLeastStudentInCourse(eq(course), any(User.class))).thenReturn(false);

        actAndAssertInvalidUserMention(course, content);
    }

    @Test
    void testParseUserMentionsWithMissingLogin() {
        Course course = new Course();
        String content = "[user]Test User 1[/user]";

        actAndAssertInvalidUserMention(course, content);
    }

    @Test
    void testParseUserMentionsWithExtraSpaces() throws InvocationTargetException, IllegalAccessException {
        Course course = new Course();
        String content = "[user] Test User 2 (test_user_1) [/user]";

        setupUserRepository(Set.of(), Set.of());

        // Should not be recognized as user mention and therefore should throw now exception
        parseUserMentions.invoke(postingService, course, content);
    }

    @Test
    void testParseUserMentionsMissingOpeningTag() throws InvocationTargetException, IllegalAccessException {
        Course course = new Course();
        String content = "Test User 2(test_user_1)[/user]";

        setupUserRepository(Set.of(), Set.of());

        // Should not be recognized as user mention and therefore should throw now exception
        parseUserMentions.invoke(postingService, course, content);
    }

    @Test
    void testParseUserMentionsMissingClosingTag() throws InvocationTargetException, IllegalAccessException {
        Course course = new Course();
        String content = "[user]Test User 2(test_user_1)";

        setupUserRepository(Set.of(), Set.of());

        // Should not be recognized as user mention and therefore should throw now exception
        parseUserMentions.invoke(postingService, course, content);
    }

    @Test
    void shouldSetCorrectFlagsWhenPostsAreSaved() {
        when(savedPostRepository.findSavedPostIdsByUserIdAndPostType(testUser.getId(), PostingType.POST)).thenReturn(List.of(savedPost.getPostId()));

        when(savedPostRepository.findSavedPostIdsByUserIdAndPostType(testUser.getId(), PostingType.ANSWER)).thenReturn(List.of(savedAnswer.getPostId(), savedAnswer2.getPostId()));

        postingService.preparePostForBroadcast(testPost);

        assertThat(testPost.getIsSaved()).isTrue();
        testPost.getAnswers().forEach(answer -> assertThat(answer.getIsSaved()).isTrue());
    }

    @Test
    void shouldSetCorrectFlagsWhenPostsAreNotSaved() {
        when(savedPostRepository.findSavedPostIdsByUserIdAndPostType(testUser.getId(), PostingType.POST)).thenReturn(List.of());

        when(savedPostRepository.findSavedPostIdsByUserIdAndPostType(testUser.getId(), PostingType.ANSWER)).thenReturn(List.of());

        postingService.preparePostForBroadcast(testPost);

        assertThat(testPost.getIsSaved()).isFalse();
        testPost.getAnswers().forEach(answer -> assertThat(answer.getIsSaved()).isFalse());
    }

    /**
     * Creates a user with the provided name and login
     *
     * @param name  name of the user
     * @param login login of the user
     * @return a user
     */
    private User createUser(String name, String login) {
        User user = new User();
        user.setFirstName(name);
        user.setLogin(login);
        return user;
    }

    /**
     * This helper method sets up the mock for the UserRepository.findAllWithCourseRolesAndAuthoritiesByDeletedIsFalseAndLoginIn method in a way,
     * so that it asserts the correct input
     *
     * @param expectedUserLogins expected set of user logins found in the message content
     * @param usersInDatabase    the mocked return value of UserRepository.findAllWithCourseRolesAndAuthoritiesByDeletedIsFalseAndLoginIn
     */
    private void setupUserRepository(Set<String> expectedUserLogins, Set<User> usersInDatabase) {
        when(userRepository.findAllWithCourseRolesAndAuthoritiesByDeletedIsFalseAndLoginIn(anySet())).thenAnswer(invocation -> {
            Set<String> logins = invocation.getArgument(0);
            assertThat(logins).isEqualTo(expectedUserLogins);
            return usersInDatabase;
        });
    }

    @ParameterizedTest
    @ValueSource(strings = { "@all", "@ALL", "@All", "@all ", "@all, please read", "@all. Please read", "@all!", "Hello @all", "Hello (@all)", "line one\n@all",
            "[user]A B(ab)[/user] @all", "@all @all", "> quoted text\n@all please read", "`code` @all", "```\ncode\n```\n@all", "~~~\ncode\n~~~\n@all", "> quoted\n\n@all",
            "see https://host/page and @all" })
    void testContainsAtAllMentionMatches(String content) {
        assertThat(PostingService.containsAtAllMention(content)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "all", "@alle", "@allow", "@all_hands", "@all1", "email@all.com", "name@all", "@@all", "@ all", "@al", "[user]A B(ab)[/user]", "@all\u00e9",
            "\u00e9@all", "> @all meeting at 5", "  > @all meeting at 5", ">> @all", "`@all`", "``@all``", "use `@all` to ping", "```\n@all\n```", "```java\nint a;\n@all\n```",
            "```\n@all", "~~~\n@all\n~~~", "https://host/@all", "[link](https://host/@all)" })
    void testContainsAtAllMentionDoesNotMatch(String content) {
        assertThat(PostingService.containsAtAllMention(content)).isFalse();
    }

    @Test
    void testContainsAtAllMentionNullContent() {
        assertThat(PostingService.containsAtAllMention(null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "a ` b @all", "``code with ` inside`` @all", "`a` `b` @all", "```\ncode\n````\n@all", "```\n@all\n``", "`` `` @all", "```inline``` @all" })
    void testContainsAtAllMentionCountsTokensOutsideOfClosedCode(String content) {
        // an unmatched backtick is plain text, a code span ends with a run of exactly the same length, and a shorter fence line does not close a fenced block
        assertThat(PostingService.containsAtAllMention(content)).isEqualTo(!content.equals("```\n@all\n``"));
    }

    @Test
    void testContainsAtAllMentionStaysFastForLargeInputWithManyBackticksAndLines() {
        // a single run of backticks that does not start the line has no partner, so it is plain text
        String manyBackticks = "text " + "`".repeat(200_000) + " @all";
        String alternatingRuns = "`a``b```c````d".repeat(20_000);
        // an odd number of fence lines leaves the last block open, so the token at the end is still code
        String manyFenceLikeLines = "```\n".repeat(50_001) + "@all";
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertThat(PostingService.containsAtAllMention(manyBackticks)).isTrue();
            assertThat(PostingService.containsAtAllMention(alternatingRuns)).isFalse();
            assertThat(PostingService.containsAtAllMention(manyFenceLikeLines)).isFalse();
        });
    }

    /**
     * Invokes the parseUserMentions method with the given course and content.
     * Asserts that an BadRequestAlertException exception is thrown.
     *
     * @param course  the course
     * @param content the content
     */
    private void actAndAssertInvalidUserMention(Course course, String content) {
        assertThatThrownBy(() -> parseUserMentions.invoke(postingService, course, content)).hasCauseInstanceOf(BadRequestAlertException.class);
    }
}
