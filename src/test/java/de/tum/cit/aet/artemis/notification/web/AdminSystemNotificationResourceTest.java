package de.tum.cit.aet.artemis.notification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.notification.domain.notification.SystemNotification;
import de.tum.cit.aet.artemis.notification.dto.SystemNotificationUpdateDTO;
import de.tum.cit.aet.artemis.notification.repository.SystemNotificationRepository;
import de.tum.cit.aet.artemis.notification.service.SystemNotificationService;
import de.tum.cit.aet.artemis.notification.web.admin.AdminSystemNotificationResource;

@ExtendWith(MockitoExtension.class)
class AdminSystemNotificationResourceTest {

    @Mock
    private SystemNotificationRepository repository;

    @Mock
    private SystemNotificationService service;

    private AdminSystemNotificationResource resource;

    @BeforeEach
    void setUp() {
        resource = new AdminSystemNotificationResource(repository, service);
        ReflectionTestUtils.setField(resource, "applicationName", "customApp");
    }

    @Test
    void shouldCreateWithoutSendingMailAndKeepConfiguredAlertHeaders() throws Exception {
        var result = savedNotification();
        when(repository.save(any(SystemNotification.class))).thenReturn(result);
        var response = resource.createSystemNotification(input(), false);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getLocation()).hasToString("/api/notifications/42");
        assertThat(response.getHeaders().getFirst("X-customApp-alert")).isEqualTo("customApp.systemNotification.created");
        verify(service, never()).sendMaintenanceEmails(any());
        verify(service).distributeActiveAndFutureNotificationsToClients();
    }

    @Test
    void shouldKeepSuccessfulCreationWhenSelectedMaintenanceMailFails() throws Exception {
        var result = savedNotification();
        when(repository.save(any(SystemNotification.class))).thenReturn(result);
        doThrow(new IllegalStateException("mail unavailable")).when(service).sendMaintenanceEmails(result);
        var response = resource.createSystemNotification(input(), true);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().id()).isEqualTo(42L);
        verify(service).sendMaintenanceEmails(result);
        verify(service).distributeActiveAndFutureNotificationsToClients();
    }

    @Test
    void shouldReturnZeroRecipientsAsJsonNumber() {
        when(service.countMaintenanceEmailRecipients()).thenReturn(0L);
        assertThat(resource.getMaintenanceRecipientsCount().getBody()).isZero();
    }

    private static SystemNotificationUpdateDTO input() {
        var start = ZonedDateTime.parse("2026-10-25T00:30:00Z");
        return new SystemNotificationUpdateDTO(null, "Maintenance", null, start, start.plusHours(1), null);
    }

    private static SystemNotification savedNotification() {
        var notification = input().toEntity();
        notification.setId(42L);
        return notification;
    }
}
