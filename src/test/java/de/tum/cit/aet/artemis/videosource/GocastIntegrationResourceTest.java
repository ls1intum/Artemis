package de.tum.cit.aet.artemis.videosource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus;
import de.tum.cit.aet.artemis.videosource.domain.GocastCourseBinding;
import de.tum.cit.aet.artemis.videosource.dto.GocastBindingWithApprovalDTO;
import de.tum.cit.aet.artemis.videosource.dto.GocastCourseDTO;
import de.tum.cit.aet.artemis.videosource.dto.GocastCreateBindingRequestDTO;
import de.tum.cit.aet.artemis.videosource.dto.GocastPlaybackTokenDTO;
import de.tum.cit.aet.artemis.videosource.dto.GocastStreamDTO;
import de.tum.cit.aet.artemis.videosource.repository.GocastCourseBindingRepository;
import de.tum.cit.aet.artemis.videosource.service.GocastIntegrationException;

/**
 * Spring integration tests for {@link de.tum.cit.aet.artemis.videosource.web.GocastIntegrationResource}.
 * <p>
 * Exercises the authorization matrix (instructor vs. student vs. non-member) and the binding
 * state-machine (PENDING → ACTIVE when EP7 returns true; PENDING stays PENDING when EP7 returns
 * false or throws any exception; REVOKED is only reached from ACTIVE bindings via EP8/EP2 paths).
 * <p>
 * {@link de.tum.cit.aet.artemis.videosource.service.GocastConnectorService} and
 * {@link de.tum.cit.aet.artemis.videosource.service.GocastApprovalLinkService} are provided as
 * {@code @MockitoBean} fields inherited from {@code AbstractSpringIntegrationIndependentTestBase}.
 * The gocast integration properties that activate the {@code GocastEnabled} condition are set in
 * {@code AbstractSpringIntegrationIndependentTest}'s {@code @TestPropertySource}.
 */
class GocastIntegrationResourceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "gocastresource";

    @Autowired
    private GocastCourseBindingRepository bindingRepository;

    private Course course;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        course = courseUtilService.createCourseWithUserPrefix(TEST_PREFIX);
    }

    @AfterEach
    void tearDown() {
        bindingRepository.deleteAll();
    }

    // ── Authorization matrix — instructor-only endpoints ─────────────────────

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void listAdministeredCourses_asStudent_returnsForbidden() throws Exception {
        request.get("/api/videosource/courses/" + course.getId() + "/tumlive-courses", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void listCourseStreams_asStudent_returnsForbidden() throws Exception {
        request.get("/api/videosource/courses/" + course.getId() + "/tumlive-streams", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createBinding_asStudent_returnsForbidden() throws Exception {
        GocastCreateBindingRequestDTO body = new GocastCreateBindingRequestDTO(42L, "eidi");
        request.post("/api/videosource/courses/" + course.getId() + "/binding", body, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getBinding_asStudent_returnsForbidden() throws Exception {
        request.get("/api/videosource/courses/" + course.getId() + "/binding", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteBinding_asStudent_returnsForbidden() throws Exception {
        request.delete("/api/videosource/courses/" + course.getId() + "/binding", HttpStatus.FORBIDDEN);
    }

    // ── listAdministeredCourses (EP1) ─────────────────────────────────────────

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void listAdministeredCourses_asInstructor_returnsOk() throws Exception {
        GocastCourseDTO dto = new GocastCourseDTO(10L, "Eidi", "eidi", 2026, "W", false, "PUBLIC");
        when(gocastConnectorService.listAdministeredCourses(anyString(), anyInt(), anyString())).thenReturn(List.of(dto));

        List<GocastCourseDTO> result = request.getList("/api/videosource/courses/" + course.getId() + "/tumlive-courses", HttpStatus.OK, GocastCourseDTO.class);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(10L);
        verify(gocastConnectorService).listAdministeredCourses(TEST_PREFIX + "instructor1", 0, "");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void listAdministeredCourses_withYearAndTerm_passesParameters() throws Exception {
        when(gocastConnectorService.listAdministeredCourses(anyString(), anyInt(), anyString())).thenReturn(List.of());

        request.get("/api/videosource/courses/" + course.getId() + "/tumlive-courses?year=2026&term=W", HttpStatus.OK, String.class);

        verify(gocastConnectorService).listAdministeredCourses(TEST_PREFIX + "instructor1", 2026, "W");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void listAdministeredCourses_gocastError_propagatesStatus() throws Exception {
        when(gocastConnectorService.listAdministeredCourses(anyString(), anyInt(), anyString()))
                .thenThrow(new GocastIntegrationException("gocast unavailable", org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));

        request.get("/api/videosource/courses/" + course.getId() + "/tumlive-courses", HttpStatus.SERVICE_UNAVAILABLE, String.class);
    }

    // ── listCourseStreams (EP8) ────────────────────────────────────────────────

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void listCourseStreams_noBinding_returnsNotFound() throws Exception {
        request.get("/api/videosource/courses/" + course.getId() + "/tumlive-streams", HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void listCourseStreams_withBinding_returnsStreams() throws Exception {
        persistBinding(course.getId(), 42L, "eidi", GocastBindingStatus.ACTIVE);
        GocastStreamDTO streamDTO = new GocastStreamDTO(1001L, "Lecture 1", false, null, null);
        when(gocastConnectorService.listCourseStreams(42L)).thenReturn(List.of(streamDTO));

        List<GocastStreamDTO> result = request.getList("/api/videosource/courses/" + course.getId() + "/tumlive-streams", HttpStatus.OK, GocastStreamDTO.class);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().streamId()).isEqualTo(1001L);
        verify(gocastConnectorService).listCourseStreams(42L);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void listCourseStreams_pendingBinding_returnsConflict() throws Exception {
        persistBinding(course.getId(), 42L, "eidi", GocastBindingStatus.PENDING);

        request.get("/api/videosource/courses/" + course.getId() + "/tumlive-streams", HttpStatus.CONFLICT, String.class);

        // EP8 must not be called for a non-ACTIVE binding
        verify(gocastConnectorService, never()).listCourseStreams(anyLong());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void listCourseStreams_revokedBinding_returnsConflict() throws Exception {
        persistBinding(course.getId(), 42L, "eidi", GocastBindingStatus.REVOKED);

        request.get("/api/videosource/courses/" + course.getId() + "/tumlive-streams", HttpStatus.CONFLICT, String.class);

        verify(gocastConnectorService, never()).listCourseStreams(anyLong());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void listCourseStreams_gocastReturns403_marksBindingRevokedAndReturnsConflict() throws Exception {
        persistBinding(course.getId(), 42L, "eidi", GocastBindingStatus.ACTIVE);
        when(gocastConnectorService.listCourseStreams(42L)).thenThrow(new GocastIntegrationException("forbidden", org.springframework.http.HttpStatus.FORBIDDEN));

        request.get("/api/videosource/courses/" + course.getId() + "/tumlive-streams", HttpStatus.CONFLICT, String.class);

        // Binding must be marked REVOKED in the database
        GocastCourseBinding persisted = bindingRepository.findByCourseId(course.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(GocastBindingStatus.REVOKED);
    }

    // ── createBinding ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createBinding_asInstructor_returnsPendingBindingWithApprovalUrl() throws Exception {
        // IDOR guard: instructor must administer the gocast course
        GocastCourseDTO administeredCourse = new GocastCourseDTO(42L, "Eidi", "eidi", 2026, "W", false, "PUBLIC");
        when(gocastConnectorService.listAdministeredCourses(anyString(), anyInt(), anyString())).thenReturn(List.of(administeredCourse));
        when(gocastApprovalLinkService.buildApprovalLink(anyLong(), anyString())).thenReturn("https://gocast.test/approve");

        GocastCreateBindingRequestDTO body = new GocastCreateBindingRequestDTO(42L, "eidi");
        GocastBindingWithApprovalDTO response = request.postWithResponseBody("/api/videosource/courses/" + course.getId() + "/binding", body, GocastBindingWithApprovalDTO.class,
                HttpStatus.CREATED);

        assertThat(response).isNotNull();
        assertThat(response.approvalUrl()).isEqualTo("https://gocast.test/approve");
        assertThat(response.binding()).isNotNull();
        assertThat(response.binding().status()).isEqualTo(GocastBindingStatus.PENDING);
        assertThat(response.binding().gocastCourseId()).isEqualTo(42L);
        assertThat(response.binding().gocastCourseSlug()).isEqualTo("eidi");

        // Verify the binding was persisted
        assertThat(bindingRepository.findByCourseId(course.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createBinding_asInstructor_callbackUrlContainsCourseManagementRoute() throws Exception {
        // IDOR guard: instructor administers the gocast course
        GocastCourseDTO administeredCourse = new GocastCourseDTO(42L, "Eidi", "eidi", 2026, "W", false, "PUBLIC");
        when(gocastConnectorService.listAdministeredCourses(anyString(), anyInt(), anyString())).thenReturn(List.of(administeredCourse));
        when(gocastApprovalLinkService.buildApprovalLink(anyLong(), anyString())).thenReturn("https://gocast.test/approve");

        GocastCreateBindingRequestDTO body = new GocastCreateBindingRequestDTO(42L, "eidi");
        request.postWithResponseBody("/api/videosource/courses/" + course.getId() + "/binding", body, GocastBindingWithApprovalDTO.class, HttpStatus.CREATED);

        // Verify the callback URL passed to the approval link service uses the correct route
        verify(gocastApprovalLinkService).buildApprovalLink(anyLong(), contains("/course-management/" + course.getId() + "/gocast-binding"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createBinding_withGocastCourseNotAdministeredByInstructor_returnsForbidden() throws Exception {
        // EP1 returns a list that does NOT include gocastCourseId=42
        GocastCourseDTO otherCourse = new GocastCourseDTO(99L, "Other", "other", 2026, "W", false, "PUBLIC");
        when(gocastConnectorService.listAdministeredCourses(anyString(), anyInt(), anyString())).thenReturn(List.of(otherCourse));

        GocastCreateBindingRequestDTO body = new GocastCreateBindingRequestDTO(42L, "eidi");
        request.post("/api/videosource/courses/" + course.getId() + "/binding", body, HttpStatus.FORBIDDEN);

        // No binding must be created
        assertThat(bindingRepository.findByCourseId(course.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createBinding_withGocastCourseAdministeredByInstructor_returnsPending() throws Exception {
        GocastCourseDTO administeredCourse = new GocastCourseDTO(42L, "Eidi", "eidi", 2026, "W", false, "PUBLIC");
        when(gocastConnectorService.listAdministeredCourses(anyString(), anyInt(), anyString())).thenReturn(List.of(administeredCourse));
        when(gocastApprovalLinkService.buildApprovalLink(anyLong(), anyString())).thenReturn("https://gocast.test/approve");

        GocastCreateBindingRequestDTO body = new GocastCreateBindingRequestDTO(42L, "eidi");
        GocastBindingWithApprovalDTO response = request.postWithResponseBody("/api/videosource/courses/" + course.getId() + "/binding", body, GocastBindingWithApprovalDTO.class,
                HttpStatus.CREATED);

        assertThat(response).isNotNull();
        assertThat(response.binding().status()).isEqualTo(GocastBindingStatus.PENDING);
        assertThat(response.binding().gocastCourseId()).isEqualTo(42L);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createBinding_existingRevokedBinding_resetsToPendingAndReturnsCreated() throws Exception {
        // Pre-create a REVOKED binding
        persistBinding(course.getId(), 42L, "eidi", GocastBindingStatus.REVOKED);

        GocastCourseDTO administeredCourse = new GocastCourseDTO(42L, "Eidi", "eidi", 2026, "W", false, "PUBLIC");
        when(gocastConnectorService.listAdministeredCourses(anyString(), anyInt(), anyString())).thenReturn(List.of(administeredCourse));
        when(gocastApprovalLinkService.buildApprovalLink(anyLong(), anyString())).thenReturn("https://gocast.test/approve");

        GocastCreateBindingRequestDTO body = new GocastCreateBindingRequestDTO(42L, "eidi");
        GocastBindingWithApprovalDTO response = request.postWithResponseBody("/api/videosource/courses/" + course.getId() + "/binding", body, GocastBindingWithApprovalDTO.class,
                HttpStatus.CREATED);

        assertThat(response).isNotNull();
        assertThat(response.binding().status()).isEqualTo(GocastBindingStatus.PENDING);

        // Only one binding row should exist (the revoked one was reset)
        assertThat(bindingRepository.findByCourseId(course.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createBinding_ep1ThrowsGocastIntegrationException_propagatesUpstreamStatus() throws Exception {
        // Fix 4: EP1 fails with GocastIntegrationException (e.g. 503) during IDOR guard → must return 503, not 500.
        when(gocastConnectorService.listAdministeredCourses(anyString(), anyInt(), anyString()))
                .thenThrow(new GocastIntegrationException("gocast unavailable", org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));

        GocastCreateBindingRequestDTO body = new GocastCreateBindingRequestDTO(42L, "eidi");
        request.post("/api/videosource/courses/" + course.getId() + "/binding", body, HttpStatus.SERVICE_UNAVAILABLE);

        // No binding must be created
        assertThat(bindingRepository.findByCourseId(course.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createBinding_existingActiveBinding_returnsConflict() throws Exception {
        persistBinding(course.getId(), 42L, "eidi", GocastBindingStatus.ACTIVE);

        GocastCourseDTO administeredCourse = new GocastCourseDTO(42L, "Eidi", "eidi", 2026, "W", false, "PUBLIC");
        when(gocastConnectorService.listAdministeredCourses(anyString(), anyInt(), anyString())).thenReturn(List.of(administeredCourse));

        GocastCreateBindingRequestDTO body = new GocastCreateBindingRequestDTO(42L, "eidi");
        request.post("/api/videosource/courses/" + course.getId() + "/binding", body, HttpStatus.CONFLICT);
    }

    // ── getBinding (EP7 state-machine) ───────────────────────────────────────

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getBinding_noBinding_returnsNotFound() throws Exception {
        request.get("/api/videosource/courses/" + course.getId() + "/binding", HttpStatus.NOT_FOUND, GocastBindingWithApprovalDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getBinding_pendingAndEp7ReturnsTrue_flipsToActive() throws Exception {
        persistBinding(course.getId(), 42L, "eidi", GocastBindingStatus.PENDING);
        when(gocastConnectorService.getBindingStatus(42L)).thenReturn(true);

        GocastBindingWithApprovalDTO result = request.get("/api/videosource/courses/" + course.getId() + "/binding", HttpStatus.OK, GocastBindingWithApprovalDTO.class);

        assertThat(result.binding().status()).isEqualTo(GocastBindingStatus.ACTIVE);
        // Verify the DB was updated
        GocastCourseBinding persisted = bindingRepository.findByCourseId(course.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(GocastBindingStatus.ACTIVE);
        // ACTIVE binding should not have an approvalUrl
        assertThat(result.approvalUrl()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getBinding_pendingAndEp7ReturnsFalse_remainsPending() throws Exception {
        persistBinding(course.getId(), 42L, "eidi", GocastBindingStatus.PENDING);
        when(gocastConnectorService.getBindingStatus(42L)).thenReturn(false);
        when(gocastApprovalLinkService.buildApprovalLink(anyLong(), anyString())).thenReturn("https://gocast.test/approve");

        GocastBindingWithApprovalDTO result = request.get("/api/videosource/courses/" + course.getId() + "/binding", HttpStatus.OK, GocastBindingWithApprovalDTO.class);

        assertThat(result.binding().status()).isEqualTo(GocastBindingStatus.PENDING);
        GocastCourseBinding persisted = bindingRepository.findByCourseId(course.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(GocastBindingStatus.PENDING);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getBinding_pendingBinding_includesApprovalUrl() throws Exception {
        persistBinding(course.getId(), 42L, "eidi", GocastBindingStatus.PENDING);
        when(gocastConnectorService.getBindingStatus(42L)).thenReturn(false);
        when(gocastApprovalLinkService.buildApprovalLink(anyLong(), anyString())).thenReturn("https://gocast.test/approve");

        GocastBindingWithApprovalDTO result = request.get("/api/videosource/courses/" + course.getId() + "/binding", HttpStatus.OK, GocastBindingWithApprovalDTO.class);

        assertThat(result.binding().status()).isEqualTo(GocastBindingStatus.PENDING);
        assertThat(result.approvalUrl()).isEqualTo("https://gocast.test/approve");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getBinding_pendingAndEp7Returns403_staysPendingWithoutMutation() throws Exception {
        // A thrown 403 from EP7 during PENDING refresh is NOT a definitive "unbound" signal —
        // it can indicate a service-account auth/config/upstream-authorization failure. The binding
        // must stay PENDING (not REVOKED) so the instructor can retry. Only an explicit false return
        // from EP7 or an ACTIVE-binding management-call (EP8/EP2) 403 can trigger REVOKED.
        persistBinding(course.getId(), 42L, "eidi", GocastBindingStatus.PENDING);
        when(gocastConnectorService.getBindingStatus(42L)).thenThrow(new GocastIntegrationException("forbidden", org.springframework.http.HttpStatus.FORBIDDEN));
        when(gocastApprovalLinkService.buildApprovalLink(anyLong(), anyString())).thenReturn("https://gocast.test/approve");

        GocastBindingWithApprovalDTO result = request.get("/api/videosource/courses/" + course.getId() + "/binding", HttpStatus.OK, GocastBindingWithApprovalDTO.class);

        assertThat(result.binding().status()).isEqualTo(GocastBindingStatus.PENDING);
        GocastCourseBinding persisted = bindingRepository.findByCourseId(course.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(GocastBindingStatus.PENDING);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getBinding_pendingAndEp7Returns503_returnsPendingWithoutMutation() throws Exception {
        persistBinding(course.getId(), 42L, "eidi", GocastBindingStatus.PENDING);
        when(gocastConnectorService.getBindingStatus(42L)).thenThrow(new GocastIntegrationException("unavailable", org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
        when(gocastApprovalLinkService.buildApprovalLink(anyLong(), anyString())).thenReturn("https://gocast.test/approve");

        // Response is still 200 — we return the stale binding without erroring out
        GocastBindingWithApprovalDTO result = request.get("/api/videosource/courses/" + course.getId() + "/binding", HttpStatus.OK, GocastBindingWithApprovalDTO.class);

        assertThat(result.binding().status()).isEqualTo(GocastBindingStatus.PENDING);
        GocastCourseBinding persisted = bindingRepository.findByCourseId(course.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(GocastBindingStatus.PENDING);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getBinding_alreadyActive_doesNotCallEp7() throws Exception {
        persistBinding(course.getId(), 42L, "eidi", GocastBindingStatus.ACTIVE);

        GocastBindingWithApprovalDTO result = request.get("/api/videosource/courses/" + course.getId() + "/binding", HttpStatus.OK, GocastBindingWithApprovalDTO.class);

        assertThat(result.binding().status()).isEqualTo(GocastBindingStatus.ACTIVE);
        verify(gocastConnectorService, never()).getBindingStatus(anyLong());
        // ACTIVE binding has no approvalUrl
        assertThat(result.approvalUrl()).isNull();
    }

    // ── deleteBinding ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteBinding_noBinding_returnsNotFound() throws Exception {
        request.delete("/api/videosource/courses/" + course.getId() + "/binding", HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteBinding_withBinding_returnsNoContent() throws Exception {
        persistBinding(course.getId(), 42L, "eidi", GocastBindingStatus.ACTIVE);

        request.delete("/api/videosource/courses/" + course.getId() + "/binding", HttpStatus.NO_CONTENT);

        assertThat(bindingRepository.findByCourseId(course.getId())).isEmpty();
    }

    // ── getPlaybackToken (EP2) — student endpoint ─────────────────────────────

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getPlaybackToken_asStudentWithBinding_returnsSignedUrls() throws Exception {
        persistBinding(course.getId(), 42L, "eidi", GocastBindingStatus.ACTIVE);
        GocastPlaybackTokenDTO tokenDTO = new GocastPlaybackTokenDTO("https://cdn.test/playlist.m3u8", null, null, 7200);
        when(gocastConnectorService.getPlaybackToken(anyLong(), anyLong(), anyInt(), anyString())).thenReturn(tokenDTO);

        GocastPlaybackTokenDTO result = request.postWithResponseBody("/api/videosource/courses/" + course.getId() + "/streams/1001/playback-tokens", null,
                GocastPlaybackTokenDTO.class, HttpStatus.OK);

        assertThat(result).isNotNull();
        assertThat(result.playlistUrl()).isEqualTo("https://cdn.test/playlist.m3u8");
        assertThat(result.expiresIn()).isEqualTo(7200);
        verify(gocastConnectorService).getPlaybackToken(42L, 1001L, 7200, TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = "outsider", roles = "USER")
    void getPlaybackToken_asNonMember_returnsForbidden() throws Exception {
        persistBinding(course.getId(), 42L, "eidi", GocastBindingStatus.ACTIVE);

        request.post("/api/videosource/courses/" + course.getId() + "/streams/1001/playback-tokens", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getPlaybackToken_noBinding_returnsNotFound() throws Exception {
        request.post("/api/videosource/courses/" + course.getId() + "/streams/1001/playback-tokens", null, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getPlaybackToken_gocastReturns403ButEp7StillBound_returns403WithoutRevoking() throws Exception {
        persistBinding(course.getId(), 42L, "eidi", GocastBindingStatus.ACTIVE);
        when(gocastConnectorService.getPlaybackToken(anyLong(), anyLong(), anyInt(), anyString()))
                .thenThrow(new GocastIntegrationException("user not eligible", org.springframework.http.HttpStatus.FORBIDDEN));
        // EP7 confirms still bound — student is ineligible, not a revocation
        when(gocastConnectorService.getBindingStatus(42L)).thenReturn(true);

        request.post("/api/videosource/courses/" + course.getId() + "/streams/1001/playback-tokens", null, HttpStatus.FORBIDDEN);

        // Binding must remain ACTIVE
        GocastCourseBinding persisted = bindingRepository.findByCourseId(course.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(GocastBindingStatus.ACTIVE);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getPlaybackToken_gocastReturns403AndEp7NotBound_marksRevokedAndReturnsConflict() throws Exception {
        persistBinding(course.getId(), 42L, "eidi", GocastBindingStatus.ACTIVE);
        when(gocastConnectorService.getPlaybackToken(anyLong(), anyLong(), anyInt(), anyString()))
                .thenThrow(new GocastIntegrationException("service account unbound", org.springframework.http.HttpStatus.FORBIDDEN));
        // EP7 returns false — definitively unbound (the service account is no longer a course admin)
        when(gocastConnectorService.getBindingStatus(42L)).thenReturn(false);

        request.post("/api/videosource/courses/" + course.getId() + "/streams/1001/playback-tokens", null, HttpStatus.CONFLICT);

        // Binding must be marked REVOKED
        GocastCourseBinding persisted = bindingRepository.findByCourseId(course.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(GocastBindingStatus.REVOKED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getPlaybackToken_gocastReturns403AndEp7Returns403_staysActiveAndReturnsOriginal403() throws Exception {
        // EP2 returns 403 and EP7 itself throws a 403.
        // A thrown EP7 exception (including 403) is NOT a definitive "unbound" signal — only an explicit
        // false return value from EP7 revokes the binding. The binding must stay ACTIVE and the original
        // EP2 403 must be propagated to the caller.
        persistBinding(course.getId(), 42L, "eidi", GocastBindingStatus.ACTIVE);
        when(gocastConnectorService.getPlaybackToken(anyLong(), anyLong(), anyInt(), anyString()))
                .thenThrow(new GocastIntegrationException("service account forbidden", org.springframework.http.HttpStatus.FORBIDDEN));
        // EP7 throws a 403 (auth/service-account error, not a per-course revocation signal)
        when(gocastConnectorService.getBindingStatus(42L)).thenThrow(new GocastIntegrationException("forbidden", org.springframework.http.HttpStatus.FORBIDDEN));

        // The original EP2 403 is propagated unchanged.
        request.post("/api/videosource/courses/" + course.getId() + "/streams/1001/playback-tokens", null, HttpStatus.FORBIDDEN);

        // Binding must remain ACTIVE — a thrown EP7 exception must never cause a false REVOKED transition.
        GocastCourseBinding persisted = bindingRepository.findByCourseId(course.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(GocastBindingStatus.ACTIVE);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getPlaybackToken_gocastReturns403AndEp7Throws503_staysActiveAndReturns403() throws Exception {
        // Fix 1: EP2 returns 403 + EP7 throws a transient 503 → binding must NOT be mutated and the
        // original EP2 403 status is surfaced (a transient EP7 outage must not change the response code,
        // nor revoke the binding).
        persistBinding(course.getId(), 42L, "eidi", GocastBindingStatus.ACTIVE);
        when(gocastConnectorService.getPlaybackToken(anyLong(), anyLong(), anyInt(), anyString()))
                .thenThrow(new GocastIntegrationException("service account forbidden", org.springframework.http.HttpStatus.FORBIDDEN));
        // EP7 itself fails with a transient 503 (not a definitive revocation signal)
        when(gocastConnectorService.getBindingStatus(42L))
                .thenThrow(new GocastIntegrationException("upstream unavailable", org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));

        // The original EP2 403 is propagated unchanged.
        request.post("/api/videosource/courses/" + course.getId() + "/streams/1001/playback-tokens", null, HttpStatus.FORBIDDEN);

        // Binding must remain ACTIVE — transient EP7 failure must never cause a false REVOKED transition.
        GocastCourseBinding persisted = bindingRepository.findByCourseId(course.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(GocastBindingStatus.ACTIVE);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getPlaybackToken_pendingBinding_returnsConflict() throws Exception {
        persistBinding(course.getId(), 42L, "eidi", GocastBindingStatus.PENDING);

        request.post("/api/videosource/courses/" + course.getId() + "/streams/1001/playback-tokens", null, HttpStatus.CONFLICT);

        // EP2 must not be called for a non-ACTIVE binding
        verify(gocastConnectorService, never()).getPlaybackToken(anyLong(), anyLong(), anyInt(), anyString());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getPlaybackToken_revokedBinding_returnsConflict() throws Exception {
        persistBinding(course.getId(), 42L, "eidi", GocastBindingStatus.REVOKED);

        request.post("/api/videosource/courses/" + course.getId() + "/streams/1001/playback-tokens", null, HttpStatus.CONFLICT);

        verify(gocastConnectorService, never()).getPlaybackToken(anyLong(), anyLong(), anyInt(), anyString());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private GocastCourseBinding persistBinding(long courseId, long gocastCourseId, String slug, GocastBindingStatus status) {
        GocastCourseBinding binding = new GocastCourseBinding();
        binding.setCourseId(courseId);
        binding.setGocastCourseId(gocastCourseId);
        binding.setGocastCourseSlug(slug);
        binding.setStatus(status);
        return bindingRepository.save(binding);
    }
}
