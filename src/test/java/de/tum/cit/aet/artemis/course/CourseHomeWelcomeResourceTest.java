package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeSource;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeGeneratedWelcomeDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeWelcomeRequestDTO;
import de.tum.cit.aet.artemis.course.repository.CourseHomeVisitRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

@WithMockUser(username = CourseHomeWelcomeResourceTest.PREFIX + "student1", roles = "USER")
class CourseHomeWelcomeResourceTest extends AbstractSpringIntegrationIndependentTest {

    static final String PREFIX = "homewelcome";

    @Autowired
    private UserAiPreferenceService preferences;

    @Autowired
    private CourseHomeVisitRepository visits;

    @Autowired
    private MockMvc mvc;

    private Course course;

    @BeforeEach
    void setup() {
        doReturn(true).when(courseHomeAiProperties).isConfigured();
        doReturn("LOCAL").when(courseHomeAiProperties).providerLocation();
        var users = userUtilService.addUsers(PREFIX, 1, 0, 0, 1);
        course = courseUtilService.createEnrolledCourse(PREFIX);
        users.forEach(u -> preferences.recordDecision(u.getId(), AiSelectionDecision.CLOUD_AI, ZonedDateTime.now()));
        when(courseHomeAiClient.generate(any())).thenReturn(CompletableFuture.completedFuture(Optional.of("Try one manageable step, then reflect on what you learn from it.")));
    }

    @Test
    void aConsentedWelcomeUsesTheAuthorizedFingerprintWithoutAcknowledgingAVisit() throws Exception {
        var snapshot = request.get(url(), HttpStatus.OK, CourseHomeDTO.class);
        assertThat(snapshot.welcome().aiEligible()).isTrue();
        var result = generate(snapshot.welcome().contextFingerprint());
        assertThat(result.source()).isEqualTo(CourseHomeWelcomeSource.AI);
        assertThat(result.contextFingerprint()).isEqualTo(snapshot.welcome().contextFingerprint());
        assertThat(visits.findByCourseIdAndUserId(course.getId(), userUtilService.getUserByLogin(PREFIX + "student1").getId())).isEmpty();
    }

    @Test
    void declinedAiProducesFallbackWithoutAModelCall() throws Exception {
        preferences.recordDecision(userUtilService.getUserByLogin(PREFIX + "student1").getId(), AiSelectionDecision.NO_AI, ZonedDateTime.now());
        assertThat(generate("a".repeat(64)).source()).isEqualTo(CourseHomeWelcomeSource.FALLBACK);
        verify(courseHomeAiClient, never()).generate(any());
    }

    @Test
    void staleContextIsNeverSent() throws Exception {
        assertThat(generate("b".repeat(64)).source()).isEqualTo(CourseHomeWelcomeSource.FALLBACK);
        verify(courseHomeAiClient, never()).generate(any());
    }

    @Test
    void localeIsRequired() throws Exception {
        request.postWithoutResponseBody(url() + "/welcome", Map.of("contextFingerprint", "a".repeat(64)), HttpStatus.BAD_REQUEST);
        verify(courseHomeAiClient, never()).generate(any());
    }

    @Test
    @WithAnonymousUser
    void anonymousUsersCannotGenerate() throws Exception {
        request.postWithoutResponseBody(url() + "/welcome", new CourseHomeWelcomeRequestDTO("a".repeat(64), "en"), HttpStatus.UNAUTHORIZED);
        verify(courseHomeAiClient, never()).generate(any());
    }

    @Test
    @WithMockUser(username = PREFIX + "outsider", roles = "USER")
    void nonmembersCannotGenerate() throws Exception {
        userUtilService.createAndSaveUser(PREFIX + "outsider");
        request.postWithoutResponseBody(url() + "/welcome", new CourseHomeWelcomeRequestDTO("a".repeat(64), "en"), HttpStatus.FORBIDDEN);
        verify(courseHomeAiClient, never()).generate(any());
    }

    @Test
    @WithMockUser(username = PREFIX + "instructor1", roles = "USER")
    void staffCanUseTheSameStudentHomeFlow() throws Exception {
        var snapshot = request.get(url(), HttpStatus.OK, CourseHomeDTO.class);
        assertThat(generate(snapshot.welcome().contextFingerprint()).source()).isEqualTo(CourseHomeWelcomeSource.AI);
    }

    private CourseHomeGeneratedWelcomeDTO generate(String fingerprint) throws Exception {
        var body = request.getObjectMapper().writeValueAsString(new CourseHomeWelcomeRequestDTO(fingerprint, "en"));
        var result = request.performMvcRequest(post(url() + "/welcome").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        if (result.getRequest().isAsyncStarted()) {
            result.getAsyncResult(10000);
            result = mvc.perform(asyncDispatch(result)).andReturn();
        }
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        return request.getObjectMapper().readValue(result.getResponse().getContentAsString(), CourseHomeGeneratedWelcomeDTO.class);
    }

    private String url() {
        return "/api/course/courses/" + course.getId() + "/home";
    }
}
