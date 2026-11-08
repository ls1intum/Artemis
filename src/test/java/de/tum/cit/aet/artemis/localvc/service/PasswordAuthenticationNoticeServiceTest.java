package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.programming.dto.ExerciseAndCourseTitlesDTO;
import de.tum.cit.aet.artemis.programming.dto.GitRepositoryAccessDTO;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

@ExtendWith(MockitoExtension.class)
class PasswordAuthenticationNoticeServiceTest {

    @Mock
    private MailSendingService mailSendingService;

    @Mock
    private DistributedDataProvider distributedDataProvider;

    @Mock
    private DistributedMap<Long, Boolean> httpsCloneEmailCache;

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @InjectMocks
    private PasswordAuthenticationNoticeService noticeService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setLogin("testuser");
        testUser.setEmail("testuser@example.com");
        testUser.setLangKey("en");
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        lenient().when(distributedDataProvider.<Long, Boolean>getExpiringMap(anyString(), any())).thenReturn(httpsCloneEmailCache);
    }

    private final GitRepositoryAccessDTO exercise = new GitRepositoryAccessDTO(7L, 3L, ExerciseMode.INDIVIDUAL, null, null, null, null, AssessmentType.AUTOMATIC, null, null, null);

    @BeforeEach
    void stubTitles() {
        lenient().when(programmingExerciseRepository.findExerciseAndCourseTitlesById(7L)).thenReturn(Optional.of(new ExerciseAndCourseTitlesDTO("Sorting", "Algorithms")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sentVariables() {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mailSendingService).buildAndSendAsync(any(MailRecipientDTO.class), eq("email.httpsCloneTip.title"), eq("mail/httpsCloneTipEmail"), captor.capture());
        return captor.getValue();
    }

    @Test
    void emailsTheFirstPasswordAuthenticationAndNamesTheExerciseAndCourse() {
        when(httpsCloneEmailCache.putIfAbsent(eq(testUser.getId()), eq(Boolean.TRUE), any(Duration.class))).thenReturn(null);

        noticeService.sendPasswordAuthenticationEmail(testUser, exercise);

        assertThat(sentVariables()).containsEntry("exerciseTitle", "Sorting").containsEntry("courseTitle", "Algorithms").containsEntry("courseId", 3L)
                .containsEntry("exerciseId", 7L).containsEntry("examExercise", false).doesNotContainKey("endDate");
    }

    @Test
    void doesNotRepeatTheEmailWithinADay() {
        when(httpsCloneEmailCache.putIfAbsent(eq(testUser.getId()), eq(Boolean.TRUE), any(Duration.class))).thenReturn(Boolean.TRUE);

        noticeService.sendPasswordAuthenticationEmail(testUser, exercise);

        verifyNoInteractions(mailSendingService);
    }

    @Test
    void announcesTheConfiguredEndDateInTheLanguageOfTheUser() {
        ReflectionTestUtils.setField(noticeService, "passwordAuthenticationEndDate", "2027-01-15");
        testUser.setLangKey("de");
        when(httpsCloneEmailCache.putIfAbsent(eq(testUser.getId()), eq(Boolean.TRUE), any(Duration.class))).thenReturn(null);

        noticeService.sendPasswordAuthenticationEmail(testUser, exercise);

        assertThat(sentVariables()).containsEntry("endDate", "15. Januar 2027");
    }

    @Test
    void aUserWithoutAnEmailAddressIsNotEmailed() {
        testUser.setEmail(" ");

        noticeService.sendPasswordAuthenticationEmail(testUser, exercise);

        verifyNoInteractions(mailSendingService, httpsCloneEmailCache);
    }

    @Test
    void aFailingMailDoesNotFailTheGitOperation() {
        when(httpsCloneEmailCache.putIfAbsent(eq(testUser.getId()), eq(Boolean.TRUE), any(Duration.class))).thenReturn(null);
        doThrow(new IllegalStateException("mail server away")).when(mailSendingService).buildAndSendAsync(any(), any(), any(), any());

        assertThatCode(() -> noticeService.sendPasswordAuthenticationEmail(testUser, exercise)).doesNotThrowAnyException();
        verify(httpsCloneEmailCache).remove(testUser.getId());
    }

    @Test
    void aFailedClaimDoesNotReleaseTheSlotOfAnotherRequest() {
        when(httpsCloneEmailCache.putIfAbsent(eq(testUser.getId()), eq(Boolean.TRUE), any(Duration.class))).thenThrow(new IllegalStateException("redis timeout"));

        assertThatCode(() -> noticeService.sendPasswordAuthenticationEmail(testUser, exercise)).doesNotThrowAnyException();
        verify(httpsCloneEmailCache, never()).remove(testUser.getId());
        verifyNoInteractions(mailSendingService);
    }

    @Test
    void thePushNoticeNamesTheEndDateOnlyWhenOneIsConfigured() {
        assertThat(noticeService.getPasswordAuthenticationPushNotice()).contains("access token or an SSH key").doesNotContain("plan to stop");

        ReflectionTestUtils.setField(noticeService, "passwordAuthenticationEndDate", "2027-01-15");

        assertThat(noticeService.getPasswordAuthenticationPushNotice()).contains("plan to stop accepting passwords for Git on 2027-01-15");
    }

    @Test
    void aMalformedEndDateStopsTheStart() {
        ReflectionTestUtils.setField(noticeService, "passwordAuthenticationEndDate", "15.01.2027");

        assertThatThrownBy(() -> noticeService.validatePasswordAuthenticationEndDate()).isInstanceOf(IllegalStateException.class).hasMessageContaining("2027-01-15");
    }
}
