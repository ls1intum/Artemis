package de.tum.cit.aet.artemis.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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
import de.tum.cit.aet.artemis.communication.service.AtAllMentionDetector;
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
            "[user]A B(ab)[/user] @all", "@all @all", "`code` @all", "```\ncode\n```\n@all", "~~~\ncode\n~~~\n@all", "> quoted\n\n@all", "see https://host/page and @all",
            "   @all", "text\n    @all", "text\n\t@all", "> quote\n\n@all", "`code\n\n@all`", "a ` b\n@all", "`code` and\n@all", "`` a `\n@all", "`example\n# @all",
            "# Heading @all", "- item\n\n  @all", "- ~~~\n  code\n  ~~~\n\n@all", "**@all**", "_@all_ please read", "[details](https://host/page) @all", "<b>@all</b>",
            "<code>x</code> @all", "<blockquote>q</blockquote>\n\n@all", "<p>@all please read</p>", "<p>@all</p>", "<div>\n<p>\n@all please read\n</p>\n</div>",
            "<ul><li>@all</li></ul>", "<p><code>x</code> @all</p>", "<code>x</code>\n\n@all", "<pre>x</pre>\n\n@all", "<!-- note -->\n<p>@all</p>", "<div title=\"x\">@all</div>",
            "<DIV CLASS='a'>@ALL</DIV>", "<p><b>bold</b> @all</p>", "<p>a &lt; b @all</p>", "<p>1 < 2 @all</p>", "<a href=\"https://host/@all\">link</a> @all",
            "<p title=\">\">@all</p>", "<textarea>@all</textarea>", "<p></code>@all</p>", "<style>p {}</style>\n\n@all", "<script>x</script>\n<p>@all</p>",
            "see www.example.org @all", "www.example.org @all", "WWW.example.org\n@all", "www @all", "www.@all", "see https://host/page @all", "mailto:someone@example.org @all",
            "@all www.example.org", "@all <!-- never closed", "@all <p never closed", "< @all", "<3 @all", "a <b>@all</b>", "https://host/ @all", "http://host/a(b) @all",
            "x // @all" })
    void testContainsAtAllMentionMatches(String content) {
        assertThat(AtAllMentionDetector.containsAtAllMention(content)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "all", "@alle", "@allow", "@all_hands", "@all1", "email@all.com", "name@all", "@@all", "@ all", "@al", "[user]A B(ab)[/user]", "@all\u00e9",
            "\u00e9@all", "> @all meeting at 5", "  > @all meeting at 5", ">> @all", "`@all`", "``@all``", "use `@all` to ping", "```\n@all\n```", "```java\nint a;\n@all\n```",
            "```\n@all", "~~~\n@all\n~~~", "https://host/@all", "[link](https://host/@all)", "[link](https://host/p?x=@all)", "https://host/?a=@all", "a=@all", "    @all",
            "\t@all", "text\n\n    @all", "> quote\n    @all", "```\ncode\n```\n    @all", "`code\n@all`", "a `b\nc @all` d", "`code\n    @all` text", "``a\nb ` @all\nc`` d",
            "> quoted text\n@all please read", "> quote\n> more\n@all", "<code>@all</code>", "text <code>@all</code> more", "<pre>@all</pre>", "<blockquote>@all</blockquote>",
            "<p><code>@all</code></p>", "- ~~~\n  @all\n  ~~~", "1. text\n\n       @all", "[details](https://host/?q=(@all))", "see https://host/?q=(@all)", "<https://host/@all>",
            "![alt @all](https://host/image.png)", "<p><code>x</code></p><pre>x</pre>\n<code>y</code><blockquote>@all</blockquote>", "<div>\n<code>\n@all\n</code>\n</div>",
            "<pre>\nline\n\n@all\n</pre>", "<blockquote>\n\n@all\n\n</blockquote>", "<blockquote>\nquote\n</blockquote>\n<p><code>@all</code></p>", "<p>x <code>@all</code></p>",
            "<style>@all</style>", "<script>@all</script>", "<script>\nvar a = '</code>';\n@all\n</script>", "<p>x</p>\n<script>@all</script>", "<noscript>@all</noscript>",
            "<template><p>@all</p></template>", "<svg><text>@all</text></svg>", "<iframe>@all</iframe>", "<title>@all</title>", "<!-- @all -->", "<!--\n@all\n-->",
            "<p><!-- @all --></p>", "<!--@all-->", "<!---->\n<code>@all</code>", "<!-- never closed\n@all", "<p title=\">@all\">x</p>", "<div title=\">@all\">\nx\n</div>",
            "<p title='@all'>x</p>", "<p data-a=@all>x</p>", "<div title=\"x\n@all\">\ny\n</div>", "<p never closed @all", "<p \"never closed @all", "<p a=\"never closed\n@all",
            "<CODE>@ALL</CODE>", "<Pre>\n@all\n</PRE>", "<code class=\"a\">@all</code>", "<code/>@all", "<p><span><code>x</code></span><code>@all</code></p>", "<?php @all ?>",
            "<!DOCTYPE @all>", "<tel:(@all)>", "<mailto:someone@example.org?subject=(@all)>", "www.example.org/?q=(@all)", "WWW.EXAMPLE.ORG/?q=(@all)",
            "see www.example.org/?q=(@all) now", "(www.example.org/?q=(@all))", "see (www.example.org/?q=(@all)", "text,www.example.org/#(@all)", "www.example.org/a,(@all)",
            "mailto:someone@example.org?subject=(@all)", "MAILTO:someone@example.org?subject=(@all)", "(mailto:someone@example.org?body=(@all))", "//example.org/?q=(@all)",
            "(//example.org/?q=(@all))", "see //example.org/#(@all)", "foo://example.org/?q=(@all)", "(https://example.org/?q=(@all))", "x(https://host/a&(@all))",
            "ftp://host/(@all)", "tel://host/(@all)", "file:///a/(@all)" })
    void testContainsAtAllMentionDoesNotMatch(String content) {
        assertThat(AtAllMentionDetector.containsAtAllMention(content)).isFalse();
    }

    @Test
    void testContainsAtAllMentionNullContent() {
        assertThat(AtAllMentionDetector.containsAtAllMention(null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "a ` b @all", "``code with ` inside`` @all", "`a` `b` @all", "```\ncode\n````\n@all", "```\n@all\n``", "`` `` @all", "```inline``` @all" })
    void testContainsAtAllMentionCountsTokensOutsideOfClosedCode(String content) {
        // an unmatched backtick is plain text, a code span ends with a run of exactly the same length, and a shorter fence line does not close a fenced block
        assertThat(AtAllMentionDetector.containsAtAllMention(content)).isEqualTo(!content.equals("```\n@all\n``"));
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testContainsAtAllMentionStaysFastAndStableForAdversarialInputOfMaximumLength() {
        // inputs of the maximum length of a posting that are built to make parsers slow or deep: nesting, many unmatched code spans of different lengths, unclosed markup
        StringBuilder backtickRunsOfGrowingLength = new StringBuilder();
        for (int length = 1; backtickRunsOfGrowingLength.length() < 4900; length++) {
            backtickRunsOfGrowingLength.append("`".repeat(length)).append(' ');
        }
        List<String> adversarialInputs = List.of("> ".repeat(2480) + "@all", ">".repeat(4990) + "@all", "- ".repeat(2480) + "@all", "1. ".repeat(1650) + "@all",
                "*".repeat(4990) + "@all", "_*".repeat(2490) + "@all", "[".repeat(2490) + "@all", "[](".repeat(1650) + "@all", "<code>".repeat(800) + "@all",
                "<".repeat(4990) + "@all", "`a``b```c````d".repeat(350) + "@all", backtickRunsOfGrowingLength + "@all", "```\n".repeat(1240) + "@all",
                "    code `\n".repeat(440) + "    @all", "<div>\n" + "<".repeat(4900) + "@all", "<div>\n" + "<p ".repeat(1600) + "@all", "<div>\n" + "<p>".repeat(1600) + "@all",
                "<div>\n" + "<code>".repeat(800) + "@all", "<div>\n" + "</code>".repeat(700) + "@all", "<div>\n" + "</".repeat(2400) + "@all",
                "<div>\n" + "<!".repeat(2400) + "@all", "<div>\n" + "<!--".repeat(1200) + "@all", "<div>\n" + "<!-- ".repeat(950) + "@all", "<!-- ".repeat(950) + "@all",
                "<div>\n" + "<!--> ".repeat(800) + "@all", "<div>\n" + "<script>".repeat(600) + "@all", "<script>\n" + "</scrip".repeat(700) + "@all",
                "<div>\n" + "<a b=\"".repeat(800) + "@all", "<div>\n" + "<a b='".repeat(800) + "@all", "<div>\n" + "<a b=".repeat(950) + "@all",
                "<div>\n" + "<a b=\"c\" ".repeat(450) + "@all", "<div>\n" + "<?".repeat(2400) + "@all", "<p ".repeat(1600) + "@all", "<code ".repeat(800) + "@all",
                "</code ".repeat(700) + "@all", "<p>\n".repeat(1200) + "@all", "<pre>\n\n".repeat(650) + "@all", "www.".repeat(1200) + "@all", "(www.a/".repeat(700) + "@all",
                "://".repeat(1600) + "@all", "a".repeat(4900) + "://@all", "mailto:".repeat(700) + "@all", "//a".repeat(1600) + "@all", "<www.".repeat(900) + "@all",
                "<a>".repeat(1600) + "@all", "<blockquote>".repeat(400) + "@all");
        for (String input : adversarialInputs) {
            assertThat(input.length()).isLessThanOrEqualTo(5000);
            // the result is not asserted, it must only be computed without an error in time
            assertThat(AtAllMentionDetector.containsAtAllMention(input)).isNotNull();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "www.example.org/?q=(@all) @all", "see www.example.org @all", "(www.example.org) @all", "https://host/p?x=(@all) and @all",
            "mailto:a@example.org @all", "<p>text</p>\n<pre>@all</pre>\n\n@all", "<code>@all</code> @all", "<div>\n<script>@all</script>\n</div>\n\n@all",
            "<blockquote>q</blockquote><p>@all</p>" })
    void testContainsAtAllMentionCountsAStandaloneTokenNextToUrlsAndHtml(String content) {
        assertThat(AtAllMentionDetector.containsAtAllMention(content)).isTrue();
    }

    @Test
    void testContainsAtAllMentionIgnoresContentThatExceedsTheMaximumLengthOfAPosting() {
        // such content is rejected when the posting is saved, so it is not parsed in the first place
        assertThat(AtAllMentionDetector.containsAtAllMention("@all " + "x".repeat(5000))).isFalse();
        assertThat(AtAllMentionDetector.containsAtAllMention("@all " + "x".repeat(4000))).isTrue();
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
