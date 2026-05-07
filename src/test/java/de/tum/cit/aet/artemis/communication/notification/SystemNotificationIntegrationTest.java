package de.tum.cit.aet.artemis.communication.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.communication.domain.notification.SystemNotification;
import de.tum.cit.aet.artemis.communication.dto.SystemNotificationDTO;
import de.tum.cit.aet.artemis.communication.repository.SystemNotificationRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class SystemNotificationIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private SystemNotificationRepository systemNotificationRepo;

    private SystemNotification systemNotification;

    private SystemNotification systemNotificationActive;

    private SystemNotification systemNotificationFuture;

    @BeforeEach
    void initTestCase() {
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
    @WithMockUser(roles = "USER")
    void testGetActiveSystemNotification() throws Exception {
        // Do the actual request that is tested here.
        getActiveSystemNotification();
    }

    private void getActiveSystemNotification() throws Exception {
        // Do the actual request that is tested here.
        List<SystemNotificationDTO> notification = request.getList("/api/core/public/system-notifications/active", HttpStatus.OK, SystemNotificationDTO.class);

        // The returned notifications must be active or scheduled notifications.
        assertThat(notification).hasSize(2).as("Returned notifications are active or scheduled.");

        SystemNotificationDTO activeNotification = notification.get(0);
        SystemNotificationDTO futureNotification = notification.get(1);

        assertThat(activeNotification.id()).isEqualTo(systemNotificationActive.getId());
        assertThat(activeNotification.notificationDate()).isCloseTo(systemNotificationActive.getNotificationDate(), within(1, ChronoUnit.SECONDS));
        assertThat(activeNotification.expireDate()).isCloseTo(systemNotificationActive.getExpireDate(), within(1, ChronoUnit.SECONDS));

        assertThat(futureNotification.id()).isEqualTo(systemNotificationFuture.getId());
        assertThat(futureNotification.notificationDate()).isCloseTo(systemNotificationFuture.getNotificationDate(), within(1, ChronoUnit.SECONDS));
        assertThat(futureNotification.expireDate()).isCloseTo(systemNotificationFuture.getExpireDate(), within(1, ChronoUnit.SECONDS));

        assertThat(systemNotificationActive.getExpireDate()).as("Returned notification 0 has not expired yet.").isAfterOrEqualTo(ZonedDateTime.now());
        assertThat(systemNotificationActive.getNotificationDate()).as("Returned notification 0 is active.").isBeforeOrEqualTo(ZonedDateTime.now());
        assertThat(systemNotificationFuture.getExpireDate()).as("Returned notification 1 has not expired yet.").isAfterOrEqualTo(ZonedDateTime.now());
        assertThat(systemNotificationFuture.getNotificationDate()).as("Returned notification 1 is not active.").isAfterOrEqualTo(ZonedDateTime.now());
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void testCreateSystemNotification() throws Exception {
        SystemNotificationDTO response = request.postWithResponseBody("/api/communication/admin/system-notifications", systemNotification, SystemNotificationDTO.class);

        assertThat(systemNotificationRepo.findById(response.id())).as("Saved system notification").isNotNull();
        assertThat(systemNotificationRepo.existsById(response.id())).as("Saved notification exists").isTrue();
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void testCreateSystemNotification_BadRequest() throws Exception {
        systemNotification.setId(1L);
        request.post("/api/communication/admin/system-notifications", systemNotification, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCreateSystemNotification_asInstructor_Forbidden() throws Exception {
        request.post("/api/communication/admin/system-notifications", systemNotification, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(roles = "USER")
    void testCreateSystemNotification_asUser_Forbidden() throws Exception {
        systemNotification.setId(1L);
        request.post("/api/communication/admin/system-notifications", systemNotification, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void testUpdateSystemNotification() throws Exception {
        systemNotificationRepo.save(systemNotification);
        String updatedText = "updated text";
        systemNotification.setText(updatedText);

        SystemNotificationDTO response = request.putWithResponseBody("/api/communication/admin/system-notifications", systemNotification, SystemNotificationDTO.class,
                HttpStatus.OK);

        SystemNotificationDTO persistedNotification = SystemNotificationDTO.of(systemNotificationRepo.findById(systemNotification.getId()).orElseThrow());

        assertThat(response.id()).isEqualTo(persistedNotification.id());
        assertThat(response.text()).as("response has updated text").isEqualTo(updatedText);
        assertThat(response.text()).as("repository contains updated notification text").isEqualTo(persistedNotification.text());
        assertThat(response.notificationDate()).isCloseTo(persistedNotification.notificationDate(), within(1, ChronoUnit.SECONDS));
        assertThat(response.expireDate()).isCloseTo(persistedNotification.expireDate(), within(1, ChronoUnit.SECONDS));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testUpdateSystemNotification_asInstructor_Forbidden() throws Exception {
        systemNotificationRepo.save(systemNotification);
        systemNotification.setText("updated text");
        request.put("/api/communication/admin/system-notifications", systemNotification, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void testUpdateSystemNotification_BadRequest() throws Exception {
        SystemNotification systemNotification = generateSystemNotification(ZonedDateTime.now().minusDays(3), ZonedDateTime.now().plusDays(3));
        request.put("/api/communication/admin/system-notifications", systemNotification, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void testGetAllSystemNotifications() throws Exception {
        List<SystemNotificationDTO> response = request.getList("/api/communication/system-notifications", HttpStatus.OK, SystemNotificationDTO.class);
        assertThat(response).as("system notification are present").isNotEmpty();
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void testGetSystemNotification() throws Exception {
        SystemNotificationDTO response = request.postWithResponseBody("/api/communication/admin/system-notifications", systemNotification, SystemNotificationDTO.class);
        assertThat(systemNotificationRepo.findById(response.id())).get().as("system notification is not null").isNotNull();
        request.get("/api/communication/system-notifications/" + response.id(), HttpStatus.OK, SystemNotification.class);
        request.get("/api/communication/system-notifications/" + response.id() + 1, HttpStatus.NOT_FOUND, SystemNotification.class);
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void testDeleteSystemNotification() throws Exception {
        SystemNotificationDTO response = request.postWithResponseBody("/api/communication/admin/system-notifications", systemNotification, SystemNotificationDTO.class);
        assertThat(systemNotificationRepo.findById(response.id())).get().as("system notification is not null").isNotNull();
        request.delete("/api/communication/admin/system-notifications/" + response.id(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testDeleteSystemNotification_asInstructor_Forbidden() throws Exception {
        SystemNotification notification = systemNotificationRepo.save(systemNotification);
        assertThat(systemNotificationRepo.findById(notification.getId())).get().as("system notification is not null").isNotNull();
        request.delete("/api/communication/admin/system-notifications/" + notification.getId(), HttpStatus.FORBIDDEN);
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
}
