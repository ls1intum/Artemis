package de.tum.cit.aet.artemis.notification.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.notification.domain.notification.SystemNotification;
import de.tum.cit.aet.artemis.notification.dto.SystemNotificationDTO;
import de.tum.cit.aet.artemis.notification.dto.SystemNotificationUpdateDTO;
import de.tum.cit.aet.artemis.notification.repository.SystemNotificationRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class SystemNotificationIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "systemnotification";

    @Autowired
    private SystemNotificationRepository systemNotificationRepo;

    @Autowired
    private JsonMapper jsonMapper;

    private SystemNotification systemNotification;

    private SystemNotification systemNotificationActive;

    private SystemNotification systemNotificationFuture;

    @BeforeEach
    void initTestCase() {
        // The admin endpoints resolve the authenticated login against the database, so the account the tests
        // authenticate as has to exist there with the admin authority rather than only in the mock security context.
        userUtilService.addAdmin(TEST_PREFIX);

        // Generate a system notification that has expired.
        SystemNotification systemNotificationExpired = generateSystemNotification(ZonedDateTime.now().minusDays(8), ZonedDateTime.now().minusMinutes(25));
        systemNotificationRepo.save(systemNotificationExpired);

        // Generate a system notification whose notification date is in the future.
        systemNotificationFuture = generateSystemNotification(ZonedDateTime.now().plusMinutes(25), ZonedDateTime.now().plusDays(8));
        systemNotificationRepo.save(systemNotificationFuture);

        // Generate an active system notification
        systemNotificationActive = generateSystemNotification(ZonedDateTime.now().minusMinutes(25), ZonedDateTime.now().plusMinutes(25));
        systemNotificationRepo.save(systemNotificationActive);

        systemNotification = generateSystemNotification(ZonedDateTime.now().minusDays(3), ZonedDateTime.now().plusDays(3));
    }

    @AfterEach
    void tearDown() {
        systemNotificationRepo.deleteAll();
    }

    @Test
    @WithAnonymousUser
    void testGetActiveSystemNotificationWithAnonymousUser() throws Exception {
        // Do the actual request that is tested here.
        getActiveSystemNotification();
    }

    @Test
    void testGetActiveSystemNotificationWithoutUser() throws Exception {
        getActiveSystemNotification();
    }

    @Test
    void shouldNotTagTheCanonicalPublicSystemNotificationsPath() throws Exception {
        // This resource lost its legacy alias, so its canonical path must carry no deprecation signal at all.
        // That the interceptor still tags the aliases which remain is covered by LegacyApiPathDeprecationInterceptorTest.
        request.performMvcRequest(get("/api/notification/public/system-notifications/active")).andExpect(status().isOk()).andExpect(header().doesNotExist("Deprecation"))
                .andExpect(header().doesNotExist("Sunset")).andExpect(header().doesNotExist("Link"));
    }

    @Test
    void shouldNoLongerServeTheRemovedLegacyPublicSystemNotificationsPath() throws Exception {
        request.performMvcRequest(get("/api/core/public/system-notifications/active")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void testGetActiveSystemNotification() throws Exception {
        // Do the actual request that is tested here.
        getActiveSystemNotification();
    }

    private void getActiveSystemNotification() throws Exception {
        // Do the actual request that is tested here.
        List<SystemNotificationDTO> notification = request.getList("/api/notification/public/system-notifications/active", HttpStatus.OK, SystemNotificationDTO.class);

        // The returned notification must be an active notification.
        assertThat(notification).hasSize(2).as("Returned notifications are active or scheduled.").extracting(SystemNotificationDTO::id)
                .containsExactly(systemNotificationActive.getId(), systemNotificationFuture.getId());
        assertThat(systemNotificationActive.getExpireDate()).as("Returned notification 0 has not expired yet.").isAfterOrEqualTo(ZonedDateTime.now());
        assertThat(systemNotificationActive.getNotificationDate()).as("Returned notification 0 is active.").isBeforeOrEqualTo(ZonedDateTime.now());
        assertThat(systemNotificationFuture.getExpireDate()).as("Returned notification 1 has not expired yet.").isAfterOrEqualTo(ZonedDateTime.now());
        assertThat(systemNotificationFuture.getNotificationDate()).as("Returned notification 1 is not active.").isAfterOrEqualTo(ZonedDateTime.now());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testCreateSystemNotification() throws Exception {
        SystemNotificationDTO response = request.postWithResponseBody("/api/notification/admin/system-notifications", toUpdateDTO(systemNotification), SystemNotificationDTO.class);

        assertThat(systemNotificationRepo.findById(response.id())).as("Saved system notification").isNotNull();
        assertThat(systemNotificationRepo.existsById(response.id())).as("Saved notification exists").isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testCreateSystemNotification_BadRequest() throws Exception {
        systemNotification.setId(1L);
        request.post("/api/notification/admin/system-notifications", toUpdateDTO(systemNotification), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCreateSystemNotification_asInstructor_Forbidden() throws Exception {
        request.post("/api/notification/admin/system-notifications", toUpdateDTO(systemNotification), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(roles = "USER")
    void testCreateSystemNotification_asUser_Forbidden() throws Exception {
        systemNotification.setId(1L);
        request.post("/api/notification/admin/system-notifications", toUpdateDTO(systemNotification), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testUpdateSystemNotification() throws Exception {
        systemNotificationRepo.save(systemNotification);
        String updatedText = "updated text";
        systemNotification.setText(updatedText);
        SystemNotificationDTO response = request.putWithResponseBody("/api/notification/admin/system-notifications", toUpdateDTO(systemNotification), SystemNotificationDTO.class,
                HttpStatus.OK);
        assertThat(response.text()).as("response has updated text").isEqualTo(updatedText);
        assertThat(systemNotificationRepo.findById(response.id())).get().as("repository contains updated notification").extracting(SystemNotification::getText)
                .isEqualTo(updatedText);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testUpdateSystemNotification_asInstructor_Forbidden() throws Exception {
        systemNotificationRepo.save(systemNotification);
        systemNotification.setText("updated text");
        request.put("/api/notification/admin/system-notifications", toUpdateDTO(systemNotification), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testUpdateSystemNotification_BadRequest() throws Exception {
        SystemNotification systemNotification = generateSystemNotification(ZonedDateTime.now().minusDays(3), ZonedDateTime.now().plusDays(3));
        request.put("/api/notification/admin/system-notifications", toUpdateDTO(systemNotification), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetAllSystemNotifications() throws Exception {
        List<SystemNotificationDTO> response = request.getList("/api/notification/system-notifications", HttpStatus.OK, SystemNotificationDTO.class);
        assertThat(response).as("system notification are present").isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetSystemNotification() throws Exception {
        SystemNotificationDTO response = request.postWithResponseBody("/api/notification/admin/system-notifications", toUpdateDTO(systemNotification), SystemNotificationDTO.class);
        assertThat(systemNotificationRepo.findById(response.id())).get().as("system notification is not null").isNotNull();
        request.get("/api/notification/system-notifications/" + response.id(), HttpStatus.OK, SystemNotificationDTO.class);
        request.get("/api/notification/system-notifications/" + response.id() + 1, HttpStatus.NOT_FOUND, SystemNotificationDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testDeleteSystemNotification() throws Exception {
        SystemNotificationDTO response = request.postWithResponseBody("/api/notification/admin/system-notifications", toUpdateDTO(systemNotification), SystemNotificationDTO.class);
        assertThat(systemNotificationRepo.findById(response.id())).get().as("system notification is not null").isNotNull();
        request.delete("/api/notification/admin/system-notifications/" + response.id(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testDeleteSystemNotification_asInstructor_Forbidden() throws Exception {
        SystemNotification notification = systemNotificationRepo.save(systemNotification);
        assertThat(systemNotificationRepo.findById(notification.getId())).get().as("system notification is not null").isNotNull();
        request.delete("/api/notification/admin/system-notifications/" + notification.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void shouldPreserveCreationUpdateAndDeletionStatusHeadersAndInstants() throws Exception {
        var start = ZonedDateTime.parse("2026-10-25T02:30:00+02:00[Europe/Berlin]");
        var end = ZonedDateTime.parse("2026-10-25T02:30:00+01:00[Europe/Berlin]");
        var input = new SystemNotificationUpdateDTO(null, "DST maintenance", null, start, end, null);
        var created = request
                .performMvcRequest(post("/api/notification/admin/system-notifications").contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(input)))
                .andExpect(status().isCreated()).andExpect(header().string("X-artemisApp-alert", "artemisApp.systemNotification.created")).andReturn().getResponse();
        var dto = jsonMapper.readValue(created.getContentAsString(), SystemNotificationDTO.class);
        assertThat(created.getHeader("Location")).isEqualTo("/api/notifications/" + dto.id());
        assertThat(created.getHeader("X-artemisApp-params")).isEqualTo(dto.id().toString());
        assertThat(dto.notificationDate().toInstant()).isEqualTo(start.toInstant());
        assertThat(dto.expireDate().toInstant()).isEqualTo(end.toInstant());

        var replacement = jsonMapper.createObjectNode().put("id", dto.id()).putNull("title").put("text", "replacement").putNull("type")
                .put("notificationDate", start.toOffsetDateTime().toString()).put("expireDate", end.toOffsetDateTime().toString());
        request.performMvcRequest(put("/api/notification/admin/system-notifications").contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(replacement)))
                .andExpect(status().isOk()).andExpect(header().string("X-artemisApp-alert", "artemisApp.systemNotification.updated"))
                .andExpect(header().string("X-artemisApp-params", dto.id().toString())).andExpect(jsonPath("$.title").doesNotExist())
                .andExpect(jsonPath("$.text").value("replacement"));
        request.performMvcRequest(delete("/api/notification/admin/system-notifications/" + dto.id())).andExpect(status().isOk()).andExpect(content().string(""))
                .andExpect(header().string("X-artemisApp-alert", "artemisApp.systemNotification.deleted")).andExpect(header().string("X-artemisApp-params", dto.id().toString()));
        assertThat(systemNotificationRepo.existsById(dto.id())).isFalse();
    }

    @Test
    @WithMockUser(roles = "TA")
    void shouldAllowTutorPagingWithRepeatedSortButForbidDetailAndAdminOperations() throws Exception {
        request.performMvcRequest(get("/api/notification/system-notifications").param("page", "0").param("size", "1").param("sort", "notificationDate,desc", "id,asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(systemNotificationFuture.getId()))
                .andExpect(header().string("X-Total-Count", "3"))
                .andExpect(header().string("Link", org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("page=1"), org.hamcrest.Matchers.containsString("rel=\"next\""),
                        org.hamcrest.Matchers.containsString("sort=notificationDate,desc"), org.hamcrest.Matchers.containsString("sort=id,asc"))));
        request.get("/api/notification/system-notifications/" + systemNotificationActive.getId(), HttpStatus.FORBIDDEN, SystemNotificationDTO.class);
        request.get("/api/notification/admin/system-notifications/maintenance-recipients-count", HttpStatus.FORBIDDEN, Long.class);
        request.post("/api/notification/admin/system-notifications", toUpdateDTO(systemNotification), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    void shouldAllowEditorDetailButForbidAdminWrites() throws Exception {
        request.get("/api/notification/system-notifications/" + systemNotificationActive.getId(), HttpStatus.OK, SystemNotificationDTO.class);
        request.get("/api/notification/system-notifications/" + Long.MAX_VALUE, HttpStatus.NOT_FOUND, SystemNotificationDTO.class);
        request.post("/api/notification/admin/system-notifications", toUpdateDTO(systemNotification), HttpStatus.FORBIDDEN);
        request.put("/api/notification/admin/system-notifications", toUpdateDTO(systemNotificationActive), HttpStatus.FORBIDDEN);
        request.delete("/api/notification/admin/system-notifications/" + systemNotificationActive.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldForbidStudentAdministrationReads() throws Exception {
        request.getList("/api/notification/system-notifications", HttpStatus.FORBIDDEN, SystemNotificationDTO.class);
        request.get("/api/notification/system-notifications/" + systemNotificationActive.getId(), HttpStatus.FORBIDDEN, SystemNotificationDTO.class);
        request.get("/api/notification/admin/system-notifications/maintenance-recipients-count", HttpStatus.FORBIDDEN, Long.class);
    }

    @Test
    @WithAnonymousUser
    void shouldReturnPublicEmptyAndNonExpiringNotifications() throws Exception {
        systemNotificationRepo.deleteAll();
        request.performMvcRequest(get("/api/notification/public/system-notifications/active")).andExpect(status().isOk()).andExpect(content().json("[]"));
        var withoutExpiry = generateSystemNotification(ZonedDateTime.now().minusDays(1), null);
        systemNotificationRepo.save(withoutExpiry);
        request.performMvcRequest(get("/api/notification/public/system-notifications/active")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(withoutExpiry.getId())).andExpect(jsonPath("$[0].expireDate").doesNotExist());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void shouldKeepDomainValidationErrorsForMissingDatesAndUnknownId() throws Exception {
        request.performMvcRequest(post("/api/notification/admin/system-notifications").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest())
                .andExpect(header().string("X-artemisApp-error", "error.systemNotificationNeedsBothDates"));
        request.performMvcRequest(
                post("/api/notification/admin/system-notifications").contentType(MediaType.APPLICATION_JSON).content("{\"notificationDate\":null,\"expireDate\":null}"))
                .andExpect(status().isBadRequest()).andExpect(header().string("X-artemisApp-error", "error.systemNotificationNeedsBothDates"));
        var reversed = new SystemNotificationUpdateDTO(null, null, null, ZonedDateTime.now().plusDays(1), ZonedDateTime.now(), null);
        request.performMvcRequest(post("/api/notification/admin/system-notifications").contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(reversed)))
                .andExpect(status().isBadRequest()).andExpect(header().string("X-artemisApp-error", "error.systemNotificationNeedsNotificationBeforeExpiration"));
        var unknown = new SystemNotificationUpdateDTO(Long.MAX_VALUE, null, null, null, null, null);
        request.performMvcRequest(put("/api/notification/admin/system-notifications").contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(unknown)))
                .andExpect(status().isBadRequest()).andExpect(header().string("X-artemisApp-error", "error.idNull"));
    }

    /**
     * Generates a SystemNotification with the given arguments.
     *
     * @param notificationDate The notification date of the SystemNotification
     * @param expiryDate       The expiry date of the SystemNotification
     * @return The generated SystemNotification
     */
    public static SystemNotification generateSystemNotification(ZonedDateTime notificationDate, ZonedDateTime expiryDate) {
        SystemNotification systemNotification = new SystemNotification();
        systemNotification.setNotificationDate(notificationDate);
        systemNotification.setExpireDate(expiryDate);
        return systemNotification;
    }

    private static SystemNotificationUpdateDTO toUpdateDTO(SystemNotification notification) {
        return new SystemNotificationUpdateDTO(notification.getId(), notification.getTitle(), notification.getText(), notification.getNotificationDate(),
                notification.getExpireDate(), notification.getType());
    }
}
