package de.tum.cit.aet.artemis.atlas.competency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import de.tum.cit.aet.artemis.account.repository.PersonalAccessTokenRepository;
import de.tum.cit.aet.artemis.account.util.PersonalAccessTokenTestUtil;
import de.tum.cit.aet.artemis.account.util.PersonalAccessTokenTestUtil.SavedToken;
import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyRelationDTO;
import de.tum.cit.aet.artemis.atlas.dto.CourseCompetencyRequestDTO;
import de.tum.cit.aet.artemis.atlas.dto.UpdateCourseCompetencyRelationDTO;
import de.tum.cit.aet.artemis.core.security.allowedScopes.AgentScope;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * The competency endpoints a personal access token can reach, and the ones it cannot.
 */
class AgentAccessCompetencyIntegrationTest extends AbstractAtlasIntegrationTest {

    private static final String TEST_PREFIX = "agentcomp";

    @Autowired
    private PersonalAccessTokenRepository personalAccessTokenRepository;

    private Course course;

    private Competency courseCompetency;

    @BeforeEach
    void setupTestScenario() {
        // Competency creation notifies AtlasML, which is mocked in the same way as in the other competency tests.
        atlasMLRequestMockProvider.ifPresent(provider -> {
            provider.enableMockingOfRequests();
            provider.mockSaveCompetenciesAny();
        });
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
        course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        courseCompetency = competencyUtilService.createCompetency(course);
        // The setup runs with a mock user; the requests below must be authenticated by their token alone.
        SecurityContextHolder.clearContext();
        TestSecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        personalAccessTokenRepository.deleteAll();
    }

    @Test
    void tokenWithReadScopeReadsCompetenciesAndRelations() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.COMPETENCIES_READ);

        perform(get("/api/atlas/courses/" + course.getId() + "/competencies"), token).andExpect(status().isOk());
        perform(get("/api/atlas/courses/" + course.getId() + "/competencies/" + courseCompetency.getId()), token).andExpect(status().isOk());
        perform(get("/api/atlas/courses/" + course.getId() + "/course-competencies"), token).andExpect(status().isOk());
        perform(get("/api/atlas/courses/" + course.getId() + "/course-competencies/relations"), token).andExpect(status().isOk());
    }

    @Test
    void tokenWithoutReadScopeIsForbidden() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.COURSES_READ);

        perform(get("/api/atlas/courses/" + course.getId() + "/competencies"), token).andExpect(status().isForbidden());
        perform(get("/api/atlas/courses/" + course.getId() + "/course-competencies/relations"), token).andExpect(status().isForbidden());
    }

    @Test
    void editorTokenWithWriteScopeCreatesCompetency() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.COMPETENCIES_WRITE);
        var body = new CourseCompetencyRequestDTO(null, "Competency from an agent", "Created with a token", null, 50, null, false);

        perform(post("/api/atlas/courses/" + course.getId() + "/competencies").contentType(MediaType.APPLICATION_JSON).content(JsonObjectMapper.get().writeValueAsString(body)),
                token).andExpect(status().isCreated());
    }

    @Test
    void writeScopeDoesNotWidenTheRoleOfTheOwner() throws Exception {
        SavedToken studentToken = tokenOf("student1", AgentScope.COMPETENCIES_WRITE);
        var body = new CourseCompetencyRequestDTO(null, "Not allowed", "A student cannot create competencies", null, 50, null, false);

        perform(post("/api/atlas/courses/" + course.getId() + "/competencies").contentType(MediaType.APPLICATION_JSON).content(JsonObjectMapper.get().writeValueAsString(body)),
                studentToken).andExpect(status().isForbidden());
    }

    @Test
    void invalidCompetencyIsRejectedWithBadRequest() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.COMPETENCIES_WRITE);
        var body = new CourseCompetencyRequestDTO(null, "", "A competency needs a title", null, 50, null, false);

        perform(post("/api/atlas/courses/" + course.getId() + "/competencies").contentType(MediaType.APPLICATION_JSON).content(JsonObjectMapper.get().writeValueAsString(body)),
                token).andExpect(status().isBadRequest());
    }

    @Test
    void relationsCanBeCreatedAndChangedButNotDeleted() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.COMPETENCIES_WRITE, AgentScope.COMPETENCIES_READ);
        // The cycle check identifies competencies by title, and the utility gives every competency the same title by default.
        var head = competencyUtilService.createCompetency(course, "head");
        var relation = new CompetencyRelationDTO(null, courseCompetency.getId(), head.getId(), RelationType.EXTENDS);

        perform(post("/api/atlas/courses/" + course.getId() + "/course-competencies/relations").contentType(MediaType.APPLICATION_JSON)
                .content(JsonObjectMapper.get().writeValueAsString(relation)), token).andExpect(status().isOk());

        var created = competencyRelationRepository.findAllWithHeadAndTailByCourseId(course.getId()).iterator().next();
        var change = new UpdateCourseCompetencyRelationDTO(RelationType.MATCHES);
        perform(patch("/api/atlas/courses/" + course.getId() + "/course-competencies/relations/" + created.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(JsonObjectMapper.get().writeValueAsString(change)), token).andExpect(status().isNoContent());
        assertThat(competencyRelationRepository.findById(created.getId()).orElseThrow().getType()).isEqualTo(RelationType.MATCHES);

        // Deleting is deliberately not available to a token.
        perform(delete("/api/atlas/courses/" + course.getId() + "/course-competencies/relations/" + created.getId()), token).andExpect(status().isForbidden());
        perform(delete("/api/atlas/courses/" + course.getId() + "/competencies/" + courseCompetency.getId()), token).andExpect(status().isForbidden());
    }

    private SavedToken tokenOf(String loginWithoutPrefix, AgentScope... scopes) {
        long userId = userUtilService.getUserByLogin(TEST_PREFIX + loginWithoutPrefix).getId();
        return PersonalAccessTokenTestUtil.saveToken(personalAccessTokenRepository, userId, Set.of(scopes), Duration.ofDays(1));
    }

    private ResultActions perform(AbstractMockHttpServletRequestBuilder<?> requestBuilder, SavedToken token) throws Exception {
        return request.performMvcRequest(requestBuilder.header(HttpHeaders.AUTHORIZATION, token.bearer()));
    }
}
