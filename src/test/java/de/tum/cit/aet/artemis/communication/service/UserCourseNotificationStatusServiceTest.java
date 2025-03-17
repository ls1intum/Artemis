package de.tum.cit.aet.artemis.communication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.communication.domain.UserCourseNotificationStatus;
import de.tum.cit.aet.artemis.communication.domain.UserCourseNotificationStatusType;
import de.tum.cit.aet.artemis.communication.test_repository.UserCourseNotificationStatusTestRepository;
import de.tum.cit.aet.artemis.core.domain.User;

@ExtendWith(MockitoExtension.class)
class UserCourseNotificationStatusServiceTest {

    @Mock
    private UserCourseNotificationStatusTestRepository userCourseNotificationStatusRepository;

    @Mock
    private CourseNotificationCacheService courseNotificationCacheService;

    @InjectMocks
    private UserCourseNotificationStatusService userCourseNotificationStatusService;

    @Captor
    private ArgumentCaptor<List<UserCourseNotificationStatus>> statusCaptor;

    @Test
    void shouldCreateStatusEntriesForMultipleUsersWhenBatchCreating() {
        User user1 = createTestUser(1L);
        User user2 = createTestUser(2L);
        Set<User> users = Set.of(user1, user2);
        long courseNotificationId = 10L;
        long courseId = 100L;

        userCourseNotificationStatusService.batchCreateStatusForUsers(users, courseNotificationId, courseId);

        verify(userCourseNotificationStatusRepository).saveAll(statusCaptor.capture());
        List<UserCourseNotificationStatus> savedStatuses = statusCaptor.getValue();

        assertThat(savedStatuses).hasSize(2);
        assertThat(savedStatuses).allMatch(status -> status.getStatus() == UserCourseNotificationStatusType.UNSEEN);
        assertThat(savedStatuses).allMatch(status -> status.getCourseNotification().getId() == courseNotificationId);

        verify(courseNotificationCacheService).invalidateCourseNotificationCacheForUsers(users, courseId);
    }

    @ParameterizedTest
    @EnumSource(UserCourseNotificationStatusType.class)
    void shouldUpdateStatusForMultipleNotificationsWhenUpdatingUserStatus(UserCourseNotificationStatusType statusType) {
        User user = createTestUser(1L);
        List<Long> courseNotificationIds = List.of(10L, 20L, 30L);
        long courseId = 100L;

        userCourseNotificationStatusService.updateUserCourseNotificationStatus(user, courseNotificationIds, statusType, courseId);

        verify(userCourseNotificationStatusRepository).updateUserCourseNotificationStatusForUserIdAndCourseNotificationIds(eq(courseNotificationIds), eq(user.getId()),
                eq(statusType));
        verify(courseNotificationCacheService).invalidateCourseNotificationCacheForUsers(eq(Set.of(user)), eq(courseId));
    }

    @Test
    void shouldUpdateStatusForEmptyNotificationListWhenUpdatingUserStatus() {
        User user = createTestUser(1L);
        List<Long> courseNotificationIds = List.of();
        long courseId = 100L;
        UserCourseNotificationStatusType statusType = UserCourseNotificationStatusType.SEEN;

        userCourseNotificationStatusService.updateUserCourseNotificationStatus(user, courseNotificationIds, statusType, courseId);

        verify(userCourseNotificationStatusRepository).updateUserCourseNotificationStatusForUserIdAndCourseNotificationIds(eq(courseNotificationIds), eq(user.getId()),
                eq(statusType));
        verify(courseNotificationCacheService).invalidateCourseNotificationCacheForUsers(eq(Set.of(user)), eq(courseId));
    }

    @Test
    void shouldArchiveAllUserNotificationsInCourseWhenArchiving() {
        long userId = 42L;
        long courseId = 100L;
        User user = createTestUser(userId);

        userCourseNotificationStatusService.archiveUserCourseNotificationStatus(courseId, userId);

        verify(userCourseNotificationStatusRepository).updateUserCourseNotificationStatusForUserIdCourseId(eq(userId), eq(courseId), eq(UserCourseNotificationStatusType.ARCHIVED));

        ArgumentCaptor<Set<User>> userCaptor = ArgumentCaptor.forClass(Set.class);
        verify(courseNotificationCacheService).invalidateCourseNotificationCacheForUsers(userCaptor.capture(), eq(courseId));

        Set<User> capturedUsers = userCaptor.getValue();
        assertThat(capturedUsers).hasSize(1);
        assertThat(capturedUsers.iterator().next().getId()).isEqualTo(userId);
    }

    private User createTestUser(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
