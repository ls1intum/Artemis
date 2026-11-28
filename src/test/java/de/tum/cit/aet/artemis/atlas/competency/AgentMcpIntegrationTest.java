package de.tum.cit.aet.artemis.atlas.competency;

import static de.tum.cit.aet.artemis.account.util.PersonalAccessTokenTestUtil.replacingHeader;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.test.context.support.TestExecutionEvent;
import org.springframework.security.test.context.support.WithMockUser;

import tools.jackson.databind.JsonNode;

import de.tum.cit.aet.artemis.account.repository.PersonalAccessTokenRepository;
import de.tum.cit.aet.artemis.account.util.PersonalAccessTokenTestUtil;
import de.tum.cit.aet.artemis.account.util.PersonalAccessTokenTestUtil.SavedToken;
import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.core.security.allowedScopes.AgentScope;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * The Model Context Protocol endpoint: the protocol handshake, which tools a token sees and what a call does.
 */
class AgentMcpIntegrationTest extends AbstractAtlasIntegrationTest {

    private static final String TEST_PREFIX = "agentmcp";

    private static final String MCP = "/api/core/agent/mcp";

    @Autowired
    private PersonalAccessTokenRepository personalAccessTokenRepository;

    private Course course;

    private Competency competency;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
        course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        competency = competencyUtilService.createCompetency(course);
        SecurityContextHolder.clearContext();
        TestSecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        personalAccessTokenRepository.deleteAll();
    }

    @Test
    void discoveryNamesTheVersionTheToolsAndTheServer() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.COMPETENCIES_READ);

        JsonNode answer = rpc(token, "server/discover", Map.of());

        assertThat(answer.at("/result/resultType").asString()).isEqualTo("complete");
        assertThat(answer.at("/result/supportedVersions")).hasSize(1);
        assertThat(answer.at("/result/supportedVersions/0").asString()).isEqualTo(PersonalAccessTokenTestUtil.MCP_PROTOCOL_VERSION);
        assertThat(answer.at("/result/_meta/io.modelcontextprotocol~1serverInfo/name").asString()).isEqualTo("artemis");
        assertThat(answer.at("/result/capabilities/tools").isObject()).isTrue();
        assertThat(answer.at("/result/instructions").asString()).contains("release date in the future").contains("at once");
        assertThat(answer.at("/result/cacheScope").asString()).isEqualTo("public");
        assertThat(answer.at("/result/ttlMs").asLong()).isPositive();
    }

    @Test
    void theHandshakeOfEarlierRevisionsIsAnsweredWithTheSupportedVersion() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.COMPETENCIES_READ);
        var message = JsonObjectMapper.get().writeValueAsString(Map.of("jsonrpc", "2.0", "id", 1, "method", "initialize", "params", Map.of("protocolVersion", "2025-11-25")));

        String body = request.performMvcRequest(post(MCP).contentType(MediaType.APPLICATION_JSON).content(message).header(HttpHeaders.AUTHORIZATION, token.bearer()))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();

        JsonNode answer = JsonObjectMapper.get().readTree(body);
        assertThat(answer.at("/error/code").asInt()).isEqualTo(-32022);
        assertThat(answer.at("/error/data/supported/0").asString()).isEqualTo(PersonalAccessTokenTestUtil.MCP_PROTOCOL_VERSION);
        assertThat(answer.at("/error/data/requested").asString()).isEqualTo("2025-11-25");
    }

    @Test
    void aVersionOfAnEarlierRevisionIsRejectedWithTheSupportedOnes() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.COMPETENCIES_READ);
        var message = JsonObjectMapper.get().writeValueAsString(Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/list", "params",
                Map.of("_meta", Map.of("io.modelcontextprotocol/protocolVersion", "2025-11-25", "io.modelcontextprotocol/clientCapabilities", Map.of()))));
        var builder = PersonalAccessTokenTestUtil.replacingHeader(PersonalAccessTokenTestUtil.mcpRequest("tools/list", Map.of()), "MCP-Protocol-Version", "2025-11-25");

        String body = request.performMvcRequest(builder.content(message).header(HttpHeaders.AUTHORIZATION, token.bearer())).andExpect(status().isBadRequest()).andReturn()
                .getResponse().getContentAsString();

        JsonNode answer = JsonObjectMapper.get().readTree(body);
        assertThat(answer.at("/error/code").asInt()).isEqualTo(-32022);
        assertThat(answer.at("/error/data/requested").asString()).isEqualTo("2025-11-25");
    }

    @Test
    void aRequestWithoutTheVersionAndCapabilitiesOfTheClientIsRejected() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.COMPETENCIES_READ);
        var message = JsonObjectMapper.get().writeValueAsString(Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/list", "params", Map.of()));

        String body = request
                .performMvcRequest(post(MCP).contentType(MediaType.APPLICATION_JSON).content(message).header(HttpHeaders.AUTHORIZATION, token.bearer())
                        .header("MCP-Protocol-Version", PersonalAccessTokenTestUtil.MCP_PROTOCOL_VERSION).header("Mcp-Method", "tools/list"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();

        assertThat(JsonObjectMapper.get().readTree(body).at("/error/code").asInt()).isEqualTo(-32602);
    }

    @Test
    void headersThatDifferFromTheMessageAreRejected() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.COMPETENCIES_READ);

        for (var builder : List.of(replacingHeader(PersonalAccessTokenTestUtil.mcpRequest("tools/list", Map.of()), "Mcp-Method", "tools/call"),
                replacingHeader(PersonalAccessTokenTestUtil.mcpRequest("tools/list", Map.of()), "MCP-Protocol-Version", "2025-11-25"),
                replacingHeader(PersonalAccessTokenTestUtil.mcpRequest("tools/call", Map.of("name", "list_my_courses")), "Mcp-Name", "get_competency"))) {
            String body = request.performMvcRequest(builder.header(HttpHeaders.AUTHORIZATION, token.bearer())).andExpect(status().isBadRequest()).andReturn().getResponse()
                    .getContentAsString();
            assertThat(JsonObjectMapper.get().readTree(body).at("/error/code").asInt()).isEqualTo(-32020);
        }
    }

    @Test
    void aToolCallWithoutTheNameHeaderIsRejected() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.COURSES_READ);
        var message = JsonObjectMapper.get().writeValueAsString(Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/call", "params", Map.of("name", "list_my_courses", "_meta",
                Map.of("io.modelcontextprotocol/protocolVersion", PersonalAccessTokenTestUtil.MCP_PROTOCOL_VERSION, "io.modelcontextprotocol/clientCapabilities", Map.of()))));

        String body = request
                .performMvcRequest(post(MCP).contentType(MediaType.APPLICATION_JSON).content(message).header(HttpHeaders.AUTHORIZATION, token.bearer())
                        .header("MCP-Protocol-Version", PersonalAccessTokenTestUtil.MCP_PROTOCOL_VERSION).header("Mcp-Method", "tools/call"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();

        assertThat(JsonObjectMapper.get().readTree(body).at("/error/code").asInt()).isEqualTo(-32020);
    }

    @Test
    void aBase64EncodedToolNameInTheHeaderIsDecodedBeforeItIsCompared() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.COURSES_READ);
        String encoded = "=?base64?" + java.util.Base64.getEncoder().encodeToString("list_my_courses".getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "?=";
        var builder = replacingHeader(PersonalAccessTokenTestUtil.mcpRequest("tools/call", Map.of("name", "list_my_courses", "arguments", Map.of())), "Mcp-Name", encoded);

        String body = request.performMvcRequest(builder.header(HttpHeaders.AUTHORIZATION, token.bearer())).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();

        assertThat(JsonObjectMapper.get().readTree(body).at("/result/isError").asBoolean(true)).isFalse();
    }

    @Test
    void everyResultNamesTheServerInItsMeta() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.COURSES_READ);

        assertThat(rpc(token, "tools/list", Map.of()).at("/result/_meta/io.modelcontextprotocol~1serverInfo/name").asString()).isEqualTo("artemis");
        assertThat(callTool(token, "list_my_courses", Map.of()).at("/result/_meta/io.modelcontextprotocol~1serverInfo/name").asString()).isEqualTo("artemis");
    }

    @Test
    void aNotificationIsAcceptedWithoutAnAnswer() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.COMPETENCIES_READ);
        var notification = JsonObjectMapper.get().writeValueAsString(Map.of("jsonrpc", "2.0", "method", "notifications/cancelled"));

        request.performMvcRequest(post(MCP).contentType(MediaType.APPLICATION_JSON).content(notification).header(HttpHeaders.AUTHORIZATION, token.bearer()))
                .andExpect(status().isAccepted());
    }

    @Test
    void theListOfToolsIsCachedOnlyPerToken() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.COMPETENCIES_READ);

        JsonNode answer = rpc(token, "tools/list", Map.of());

        assertThat(answer.at("/result/resultType").asString()).isEqualTo("complete");
        assertThat(answer.at("/result/cacheScope").asString()).isEqualTo("private");
        assertThat(answer.at("/result/tools/0/annotations/openWorldHint").asBoolean(true)).isFalse();
    }

    @Test
    void aRequestOfAnotherSiteIsForbidden() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.COMPETENCIES_READ);

        request.performMvcRequest(
                PersonalAccessTokenTestUtil.mcpRequest("tools/list", Map.of()).header(HttpHeaders.ORIGIN, "https://evil.example").header(HttpHeaders.AUTHORIZATION, token.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void aTokenSeesOnlyTheToolsItsScopesAllow() throws Exception {
        SavedToken readOnly = tokenOf("editor1", AgentScope.COMPETENCIES_READ);
        SavedToken everything = tokenOf("editor1", AgentScope.values());

        assertThat(toolNames(rpc(readOnly, "tools/list", Map.of()))).contains("get_competency_graph", "get_competency").doesNotContain("post_announcement", "create_competencies",
                "create_text_exercise");
        assertThat(toolNames(rpc(everything, "tools/list", Map.of()))).contains("list_my_courses", "create_competencies", "post_announcement", "create_text_exercise",
                "set_programming_problem_statement", "get_programming_test_cases", "create_lecture");
    }

    @Test
    void everyToolHasADescriptionAndAnObjectSchema() throws Exception {
        SavedToken everything = tokenOf("editor1", AgentScope.values());

        for (JsonNode tool : rpc(everything, "tools/list", Map.of()).at("/result/tools")) {
            assertThat(tool.path("description").asString()).as(tool.path("name").asString()).isNotBlank();
            assertThat(tool.at("/inputSchema/type").asString()).as(tool.path("name").asString()).isEqualTo("object");
        }
    }

    @Test
    void aToolReadsTheCompetencyGraphThroughTheRestApi() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.COMPETENCIES_READ);

        JsonNode answer = callTool(token, "get_competency_graph", Map.of("courseId", course.getId()));

        assertThat(answer.at("/result/isError").asBoolean()).isFalse();
        assertThat(answer.at("/result/content/0/text").asString()).contains(competency.getTitle());
    }

    @Test
    void aToolCannotDoWhatTheRoleOfTheOwnerForbids() throws Exception {
        SavedToken token = tokenOf("student1", AgentScope.COMPETENCIES_WRITE);

        JsonNode answer = callTool(token, "create_competencies", Map.of("courseId", course.getId(), "competencies", List.of(Map.of("title", "T", "masteryThreshold", 50))));

        assertThat(answer.at("/result/isError").asBoolean()).isTrue();
        assertThat(answer.at("/result/content/0/text").asString()).contains("HTTP 403");
    }

    @Test
    void aToolCreatesCompetenciesForAnEditor() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.COMPETENCIES_WRITE);

        JsonNode answer = callTool(token, "create_competencies",
                Map.of("courseId", course.getId(), "competencies", List.of(Map.of("title", "Created by a tool", "masteryThreshold", 60))));

        assertThat(answer.at("/result/isError").asBoolean()).isFalse();
        assertThat(answer.at("/result/content/0/text").asString()).contains("Created by a tool");
    }

    @Test
    void anAnnouncementIsNotPostedWithoutTheConfirmationOfTheInstructor() throws Exception {
        SavedToken token = tokenOf("instructor1", AgentScope.ANNOUNCEMENTS_POST);

        JsonNode answer = callTool(token, "post_announcement", Map.of("courseId", course.getId(), "content", "Hello students"));

        assertThat(answer.at("/result/isError").asBoolean()).isTrue();
        assertThat(answer.at("/result/content/0/text").asString()).contains("Not posted");
    }

    @Test
    void aLectureIsNotCreatedWithoutTheConfirmationOfTheInstructor() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.LECTURES_WRITE);

        JsonNode answer = callTool(token, "create_lecture", Map.of("courseId", course.getId(), "title", "Week 1"));

        assertThat(answer.at("/result/isError").asBoolean()).isTrue();
        assertThat(answer.at("/result/content/0/text").asString()).contains("Not created");
    }

    @Test
    void aConfirmedLectureIsCreated() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.LECTURES_WRITE);

        JsonNode answer = callTool(token, "create_lecture", Map.of("courseId", course.getId(), "title", "Week 1", "confirm", true));

        assertThat(answer.at("/result/isError").asBoolean()).isFalse();
        assertThat(answer.at("/result/content/0/text").asString()).contains("Week 1");
    }

    @Test
    void anExerciseWithoutAFutureReleaseDateIsRefusedWithAnExplanation() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.EXERCISES_DRAFT);
        var exercise = Map.of("title", "Essay", "problemStatement", "Write.", "maxPoints", 10, "includedInOverallScore", "INCLUDED_COMPLETELY", "courseId", course.getId(), "mode",
                "INDIVIDUAL");

        JsonNode answer = callTool(token, "create_text_exercise", Map.of("exercise", exercise));

        assertThat(answer.at("/result/isError").asBoolean()).isTrue();
        assertThat(answer.at("/result/content/0/text").asString()).contains("HTTP 400").contains("draft");
    }

    @Test
    void invalidArgumentsAreReportedToTheAgentAsAFailure() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.COMPETENCIES_READ);

        JsonNode answer = callTool(token, "get_competency_graph", Map.of("courseId", "1/../../admin"));

        assertThat(answer.at("/result/isError").asBoolean()).isTrue();
        assertThat(answer.at("/result/content/0/text").asString()).contains("Invalid arguments").contains("courseId");
    }

    @Test
    void aToolTheTokenCannotSeeIsUnknown() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.COMPETENCIES_READ);

        JsonNode answer = callTool(token, "post_announcement", Map.of("courseId", course.getId(), "content", "x", "confirm", true));

        assertThat(answer.at("/error/code").asInt()).isEqualTo(-32602);
    }

    @Test
    void anUnknownMethodIsAnError() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.COMPETENCIES_READ);

        String body = request.performMvcRequest(PersonalAccessTokenTestUtil.mcpRequest("resources/list", Map.of()).header(HttpHeaders.AUTHORIZATION, token.bearer()))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        assertThat(JsonObjectMapper.get().readTree(body).at("/error/code").asInt()).isEqualTo(-32601);
    }

    @Test
    // Set up after @BeforeEach, which clears the security context so that the other tests authenticate with their token alone.
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "USER", setupBefore = TestExecutionEvent.TEST_EXECUTION)
    void aRequestWithoutATokenIsForbidden() throws Exception {
        var message = JsonObjectMapper.get().writeValueAsString(Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/list"));

        request.performMvcRequest(post(MCP).contentType(MediaType.APPLICATION_JSON).content(message)).andExpect(status().isForbidden());
    }

    @Test
    void theEndpointDoesNotStream() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.COMPETENCIES_READ);

        request.performMvcRequest(get(MCP).header(HttpHeaders.AUTHORIZATION, token.bearer())).andExpect(status().isMethodNotAllowed());
    }

    private JsonNode callTool(SavedToken token, String name, Map<String, Object> arguments) throws Exception {
        return rpc(token, "tools/call", Map.of("name", name, "arguments", arguments));
    }

    private JsonNode rpc(SavedToken token, String method, Map<String, Object> params) throws Exception {
        String body = request.performMvcRequest(PersonalAccessTokenTestUtil.mcpRequest(method, params).header(HttpHeaders.AUTHORIZATION, token.bearer())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonObjectMapper.get().readTree(body);
    }

    private static Set<String> toolNames(JsonNode answer) {
        return StreamSupport.stream(answer.at("/result/tools").spliterator(), false).map(tool -> tool.path("name").asString()).collect(Collectors.toSet());
    }

    private SavedToken tokenOf(String loginWithoutPrefix, AgentScope... scopes) {
        long userId = userUtilService.getUserByLogin(TEST_PREFIX + loginWithoutPrefix).getId();
        return PersonalAccessTokenTestUtil.saveToken(personalAccessTokenRepository, userId, Set.of(scopes), Duration.ofDays(1));
    }
}
