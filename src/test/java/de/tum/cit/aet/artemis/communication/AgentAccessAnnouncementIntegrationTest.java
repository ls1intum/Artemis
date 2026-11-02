package de.tum.cit.aet.artemis.communication;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.ResultActions;

import de.tum.cit.aet.artemis.account.repository.PersonalAccessTokenRepository;
import de.tum.cit.aet.artemis.account.util.PersonalAccessTokenTestUtil;
import de.tum.cit.aet.artemis.account.util.PersonalAccessTokenTestUtil.SavedToken;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.dto.CreateAnnouncementDTO;
import de.tum.cit.aet.artemis.communication.dto.CreatePostConversationDTO;
import de.tum.cit.aet.artemis.communication.dto.CreatePostDTO;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.security.allowedScopes.AgentScope;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

/**
 * Posting an announcement with a personal access token.
 */
class AgentAccessAnnouncementIntegrationTest extends AbstractConversationTest {

    private static final String TEST_PREFIX = "agentann";

    @Autowired
    private PersonalAccessTokenRepository personalAccessTokenRepository;

    @Autowired
    private ConversationUtilService conversationUtilService;

    private Channel defaultAnnouncementChannel;

    @BeforeEach
    @Override
    void setupTestScenario() throws Exception {
        super.setupTestScenario();
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
        userUtilService.enrollPrefixedUsersInCourse(exampleCourse, TEST_PREFIX);
        defaultAnnouncementChannel = conversationUtilService.createCourseWideChannel(exampleCourse, "announcement", true);
        SecurityContextHolder.clearContext();
        TestSecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        personalAccessTokenRepository.deleteAll();
    }

    @Override
    String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Test
    void instructorTokenPostsToTheDefaultAnnouncementChannel() throws Exception {
        SavedToken token = tokenOf("instructor1", AgentScope.ANNOUNCEMENTS_POST);

        postAnnouncement(new CreateAnnouncementDTO("The exam room changed.", "Exam room", null), token).andExpect(status().isCreated())
                .andExpect(jsonPath("$.conversation.id").value(defaultAnnouncementChannel.getId()));
    }

    @Test
    void channelIdSelectsAnotherAnnouncementChannel() throws Exception {
        SavedToken token = tokenOf("instructor1", AgentScope.ANNOUNCEMENTS_POST);
        Channel news = conversationUtilService.createCourseWideChannel(exampleCourse, "news", true);

        postAnnouncement(new CreateAnnouncementDTO("News for everyone.", null, news.getId()), token).andExpect(status().isCreated())
                .andExpect(jsonPath("$.conversation.id").value(news.getId()));
    }

    @Test
    void aChannelThatIsNotAnAnnouncementChannelIsRejected() throws Exception {
        SavedToken token = tokenOf("instructor1", AgentScope.ANNOUNCEMENTS_POST);
        Channel random = conversationUtilService.createCourseWideChannel(exampleCourse, "random-talk", false);

        postAnnouncement(new CreateAnnouncementDTO("Not an announcement.", null, random.getId()), token).andExpect(status().isBadRequest());
    }

    @Test
    void blankContentIsRejected() throws Exception {
        SavedToken token = tokenOf("instructor1", AgentScope.ANNOUNCEMENTS_POST);

        postAnnouncement(new CreateAnnouncementDTO(" ", null, null), token).andExpect(status().isBadRequest());
    }

    @Test
    void editorTokenCannotPostAnnouncements() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.ANNOUNCEMENTS_POST);

        postAnnouncement(new CreateAnnouncementDTO("Editors may not announce.", null, null), token).andExpect(status().isForbidden());
    }

    @Test
    void tokenWithoutTheScopeCannotPostAnnouncements() throws Exception {
        SavedToken token = tokenOf("instructor1", AgentScope.COMPETENCIES_WRITE);

        postAnnouncement(new CreateAnnouncementDTO("No scope.", null, null), token).andExpect(status().isForbidden());
    }

    @Test
    void tokenCannotUseTheGeneralMessageEndpoint() throws Exception {
        SavedToken token = tokenOf("instructor1", AgentScope.ANNOUNCEMENTS_POST);
        var message = new CreatePostDTO("A plain message.", null, false, new CreatePostConversationDTO(defaultAnnouncementChannel.getId()));

        request.performMvcRequest(post("/api/communication/courses/" + exampleCourseId + "/messages").contentType(MediaType.APPLICATION_JSON)
                .content(JsonObjectMapper.get().writeValueAsString(message)).header(HttpHeaders.AUTHORIZATION, token.bearer())).andExpect(status().isForbidden());
    }

    private SavedToken tokenOf(String loginWithoutPrefix, AgentScope... scopes) {
        long userId = userUtilService.getUserByLogin(TEST_PREFIX + loginWithoutPrefix).getId();
        return PersonalAccessTokenTestUtil.saveToken(personalAccessTokenRepository, userId, Set.of(scopes), Duration.ofDays(1));
    }

    private ResultActions postAnnouncement(CreateAnnouncementDTO announcement, SavedToken token) throws Exception {
        return request.performMvcRequest(post("/api/communication/courses/" + exampleCourseId + "/announcements").contentType(MediaType.APPLICATION_JSON)
                .content(JsonObjectMapper.get().writeValueAsString(announcement)).header(HttpHeaders.AUTHORIZATION, token.bearer()));
    }
}
