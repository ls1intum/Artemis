package de.tum.in.www1.artemis.metis;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.metis.ConversationMessagingService;
import de.tum.in.www1.artemis.service.metis.PostingService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

public class PostingServiceUnitTest {

    @InjectMocks
    private ConversationMessagingService postingService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthorizationCheckService authorizationCheckService;

    private Method parseUserMentions;

    @BeforeEach
    void initTestCase() throws NoSuchMethodException {
        MockitoAnnotations.openMocks(this);

        parseUserMentions = PostingService.class.getDeclaredMethod("parseUserMentions", Course.class, String.class);
        parseUserMentions.setAccessible(true);
    }

    @Test
    public void testParseUserMentionsEmptyContent() throws InvocationTargetException, IllegalAccessException {
        Course course = new Course();
        String content = "";

        parseUserMentions.invoke(postingService, course, content);
    }

    @Test
    public void testParseUserMentionsContentNull() throws InvocationTargetException, IllegalAccessException {
        Course course = new Course();

        parseUserMentions.invoke(postingService, course, null);
    }

    @Test
    public void testParseUserMentionsNoUserMentioned() throws InvocationTargetException, IllegalAccessException {
        Course course = new Course();
        String content = "This is a regular content without any user mention.";

        parseUserMentions.invoke(postingService, course, content);
    }

    @Test
    public void testParseUserMentionsWithValidUsers() throws InvocationTargetException, IllegalAccessException {
        Course course = new Course();
        String content = "[user]Test User 1(test_user_1)[/user] [user]Test User 2(test_user_2)[/user]";
        List<User> users = List.of(this.createUser("Test User 1", "test_user_1"), this.createUser("Test User 2", "test_user_2"));

        when(userRepository.findAllByLogins(anySet())).thenReturn(users);
        when(authorizationCheckService.isAtLeastStudentInCourse(eq(course), any(User.class))).thenReturn(true);

        parseUserMentions.invoke(postingService, course, content);

        verify(userRepository).findAllByLogins(anySet());
        verify(authorizationCheckService, times(2)).isAtLeastStudentInCourse(eq(course), any(User.class));
    }

    @Test
    public void testParseUserMentionsWithNonExistentUser() {
        Course course = new Course();
        String content = "[user]Test User 1(test_user_1)[/user] [user]Test User 2(test_user_2)[/user]";
        List<User> users = List.of(this.createUser("Test User 1", "test_user_1")); // Return only one user from database

        when(userRepository.findAllByLogins(anySet())).thenReturn(users);

        assertInvalidUserMention(course, content);
    }

    @Test
    public void testParseUserMentionsWithInvalidName() {
        Course course = new Course();
        String content = "[user]Test User 2(test_user_1)[/user]";
        User user = this.createUser("Test User 1", "test_user_1");  // Different name than mentioned

        when(userRepository.findAllByLogins(anySet())).thenReturn(List.of(user));
        when(authorizationCheckService.isAtLeastStudentInCourse(eq(course), any(User.class))).thenReturn(true);

        assertInvalidUserMention(course, content);
    }

    @Test
    public void testParseUserMentionsWithUserNotInCourse() {
        Course course = new Course();
        String content = "[user]Test User 1(test_user_1)[/user]";
        User user = this.createUser("Test User 1", "test_user_1");

        when(userRepository.findAllByLogins(anySet())).thenReturn(List.of(user));
        when(authorizationCheckService.isAtLeastStudentInCourse(eq(course), any(User.class))).thenReturn(false);

        assertInvalidUserMention(course, content);
    }

    @Test
    public void testParseUserMentionsWithMissingLogin() {
        Course course = new Course();
        String content = "[user]Test User 1[/user]";

        assertInvalidUserMention(course, content);
    }

    @Test
    public void testParseUserMentionsWithExtraSpaces() throws InvocationTargetException, IllegalAccessException {
        Course course = new Course();
        String content = "[user] Test User 2 (test_user_1) [/user]";

        User user = this.createUser("Test User 1", "test_user_1");  // Different name than mentioned

        when(userRepository.findAllByLogins(anySet())).thenAnswer(invocation -> {
            Set<String> logins = invocation.getArgument(0);
            return logins.isEmpty() ? List.of() : List.of(user);
        });

        when(authorizationCheckService.isAtLeastStudentInCourse(eq(course), any(User.class))).thenReturn(true);

        // Should not be recognized as user mention and therefore should throw now exception
        parseUserMentions.invoke(postingService, course, content);
    }

    @Test
    public void testParseUserMentionsMissingOpeningTag() throws InvocationTargetException, IllegalAccessException {
        Course course = new Course();
        String content = "Test User 2(test_user_1)[/user]";

        User user = this.createUser("Test User 1", "test_user_1");  // Different name than mentioned

        when(userRepository.findAllByLogins(anySet())).thenAnswer(invocation -> {
            Set<String> logins = invocation.getArgument(0);
            return logins.isEmpty() ? List.of() : List.of(user);
        });

        // Should not be recognized as user mention and therefore should throw now exception
        parseUserMentions.invoke(postingService, course, content);
    }

    @Test
    public void testParseUserMentionsMissingClosingTag() throws InvocationTargetException, IllegalAccessException {
        Course course = new Course();
        String content = "[user]Test User 2(test_user_1)";

        User user = this.createUser("Test User 1", "test_user_1");  // Different name than mentioned

        when(userRepository.findAllByLogins(anySet())).thenAnswer(invocation -> {
            Set<String> logins = invocation.getArgument(0);
            return logins.isEmpty() ? List.of() : List.of(user);
        });

        // Should not be recognized as user mention and therefore should throw now exception
        parseUserMentions.invoke(postingService, course, content);
    }

    private User createUser(String name, String login) {
        User user = new User();
        user.setFirstName(name);
        user.setLogin(login);
        return user;
    }

    private void assertInvalidUserMention(Course course, String content) {
        assertThrows(BadRequestAlertException.class, () -> {
            try {
                parseUserMentions.invoke(postingService, course, content);
            }
            catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }
}
