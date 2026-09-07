package de.tum.cit.aet.artemis.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.admin.dto.DuplicateUserEmailReportDTO;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.service.notifications.MailService;

@ExtendWith(MockitoExtension.class)
class DuplicateUserEmailScheduleServiceTest {

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private MailService mailService;

    @Mock
    private ProfileService profileService;

    private DuplicateUserEmailScheduleService service;

    @BeforeEach
    void setUp() {
        service = new DuplicateUserEmailScheduleService(userRepository, mailService, profileService);
        ReflectionTestUtils.setField(service, "adminEmail", "admin@uni.example");
    }

    @Test
    void schedulesReportWeekly() throws NoSuchMethodException {
        Method method = DuplicateUserEmailScheduleService.class.getMethod("checkForDuplicatedUserEmailsAndNotifyAdmin");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("${artemis.scheduling.duplicate-user-email-report-time:0 0 8 * * MON}");
    }

    @Test
    void doesNotSendReportWithoutDuplicates() {
        when(userRepository.findUserIdsWithDuplicatedEmail()).thenReturn(List.of());

        assertThat(service.sendDuplicateUserEmailReport()).isFalse();
        verify(mailService, never()).sendDuplicateUserEmailReportEmail(any(), any());
    }

    @Test
    void sendsPrivacyMinimizedAndCappedReport() {
        List<Long> affectedIds = LongStream.rangeClosed(1, 105).boxed().toList();
        when(userRepository.findUserIdsWithDuplicatedEmail()).thenReturn(affectedIds);

        assertThat(service.sendDuplicateUserEmailReport()).isTrue();

        ArgumentCaptor<MailRecipientDTO> recipientCaptor = ArgumentCaptor.forClass(MailRecipientDTO.class);
        ArgumentCaptor<DuplicateUserEmailReportDTO> reportCaptor = ArgumentCaptor.forClass(DuplicateUserEmailReportDTO.class);
        verify(mailService).sendDuplicateUserEmailReportEmail(recipientCaptor.capture(), reportCaptor.capture());
        assertThat(recipientCaptor.getValue().email()).isEqualTo("admin@uni.example");
        assertThat(reportCaptor.getValue().affectedAccountCount()).isEqualTo(105);
        assertThat(reportCaptor.getValue().accountIds()).containsExactlyElementsOf(affectedIds.subList(0, 100));
        assertThat(reportCaptor.getValue().omittedAccountCount()).isEqualTo(5);
    }
}
