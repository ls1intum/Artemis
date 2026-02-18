package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.domain.CampusOnlineConfiguration;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.CampusOnlineSyncResultDTO;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.CampusOnlineApiException;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.CampusOnlineClient;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.CampusOnlineEnrollmentSyncService;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineAttendance;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineContactData;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineExtension;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlinePerson;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlinePersonName;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineStudentListResponse;
import de.tum.cit.aet.artemis.core.service.user.UserService;

/**
 * Unit tests for {@link CampusOnlineEnrollmentSyncService}.
 * Tests enrollment sync logic, batch processing, error handling, and edge cases.
 */
class CampusOnlineEnrollmentSyncServiceTest {

    private CampusOnlineClient campusOnlineClient;

    private CourseRepository courseRepository;

    private UserService userService;

    private ProfileService profileService;

    private CampusOnlineEnrollmentSyncService service;

    @BeforeEach
    void setUp() {
        campusOnlineClient = mock(CampusOnlineClient.class);
        courseRepository = mock(CourseRepository.class);
        userService = mock(UserService.class);
        profileService = mock(ProfileService.class);
        service = new CampusOnlineEnrollmentSyncService(campusOnlineClient, courseRepository, userService, profileService);
    }

    // ==================== performEnrollmentSync (batch) ====================

    @Test
    void performEnrollmentSync_shouldSyncMultipleCourses() {
        Course course1 = createCourseWithConfig(1L, "CO-101", "Course 1", "students-1");
        Course course2 = createCourseWithConfig(2L, "CO-202", "Course 2", "students-2");
        when(courseRepository.findAllWithCampusOnlineConfiguration()).thenReturn(Set.of(course1, course2));

        var student = createConfirmedStudent("ab12cde", "student@example.com");
        when(campusOnlineClient.fetchStudents("CO-101")).thenReturn(new CampusOnlineStudentListResponse(List.of(student)));
        when(campusOnlineClient.fetchStudents("CO-202")).thenReturn(new CampusOnlineStudentListResponse(List.of(student)));
        when(userService.findUserAndAddToCourse(any(), isNull(), any(), any())).thenReturn(Optional.of(new User()));

        CampusOnlineSyncResultDTO result = service.performEnrollmentSync();

        assertThat(result.coursesSynced()).isEqualTo(2);
        assertThat(result.coursesFailed()).isZero();
        assertThat(result.usersAdded()).isEqualTo(2);
        assertThat(result.usersNotFound()).isZero();
    }

    @Test
    void performEnrollmentSync_shouldCountFailedCourses() {
        Course course1 = createCourseWithConfig(1L, "CO-101", "Good Course", "students-1");
        Course course2 = createCourseWithConfig(2L, "CO-FAIL", "Bad Course", "students-2");
        when(courseRepository.findAllWithCampusOnlineConfiguration()).thenReturn(Set.of(course1, course2));

        when(campusOnlineClient.fetchStudents("CO-101")).thenReturn(new CampusOnlineStudentListResponse(List.of()));
        when(campusOnlineClient.fetchStudents("CO-FAIL")).thenThrow(new CampusOnlineApiException("API error"));

        CampusOnlineSyncResultDTO result = service.performEnrollmentSync();

        assertThat(result.coursesSynced()).isEqualTo(1);
        assertThat(result.coursesFailed()).isEqualTo(1);
    }

    @Test
    void performEnrollmentSync_shouldReturnZeros_whenNoCourses() {
        when(courseRepository.findAllWithCampusOnlineConfiguration()).thenReturn(Set.of());

        CampusOnlineSyncResultDTO result = service.performEnrollmentSync();

        assertThat(result.coursesSynced()).isZero();
        assertThat(result.coursesFailed()).isZero();
        assertThat(result.usersAdded()).isZero();
        assertThat(result.usersNotFound()).isZero();
    }

    // ==================== performSingleCourseSync ====================

    @Test
    void performSingleCourseSync_shouldThrow_whenCourseNotFound() {
        when(courseRepository.findWithEagerCampusOnlineConfigurationById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.performSingleCourseSync(999L)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void performSingleCourseSync_shouldThrow_whenNoConfig() {
        Course course = new Course();
        course.setId(1L);
        when(courseRepository.findWithEagerCampusOnlineConfigurationById(1L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> service.performSingleCourseSync(1L)).isInstanceOf(CampusOnlineApiException.class).hasMessageContaining("has no CAMPUSOnline configuration");
    }

    @Test
    void performSingleCourseSync_shouldPropagateApiException() {
        Course course = createCourseWithConfig(1L, "CO-101", "Test Course", "students");
        when(courseRepository.findWithEagerCampusOnlineConfigurationById(1L)).thenReturn(Optional.of(course));
        when(campusOnlineClient.fetchStudents("CO-101")).thenThrow(new CampusOnlineApiException("API down"));

        assertThatThrownBy(() -> service.performSingleCourseSync(1L)).isInstanceOf(CampusOnlineApiException.class).hasMessageContaining("API down");
    }

    // ==================== syncCourseEnrollment edge cases ====================

    @Test
    void performSingleCourseSync_shouldHandleNullPersonsList() {
        Course course = createCourseWithConfig(1L, "CO-101", "Test Course", "students");
        when(courseRepository.findWithEagerCampusOnlineConfigurationById(1L)).thenReturn(Optional.of(course));
        when(campusOnlineClient.fetchStudents("CO-101")).thenReturn(new CampusOnlineStudentListResponse(null));

        CampusOnlineSyncResultDTO result = service.performSingleCourseSync(1L);

        assertThat(result.coursesSynced()).isEqualTo(1);
        assertThat(result.usersAdded()).isZero();
        assertThat(result.usersNotFound()).isZero();
    }

    @Test
    void performSingleCourseSync_shouldOnlySyncConfirmedStudents() {
        Course course = createCourseWithConfig(1L, "CO-101", "Test Course", "students");
        when(courseRepository.findWithEagerCampusOnlineConfigurationById(1L)).thenReturn(Optional.of(course));

        var confirmed = createConfirmedStudent("ab12cde", "confirmed@example.com");
        var unconfirmed = new CampusOnlinePerson("2", new CampusOnlinePersonName("Not", "Confirmed"), new CampusOnlineContactData("nc@example.com"),
                new CampusOnlineExtension("xx99xxx"), new CampusOnlineAttendance("N"));
        var nullAttendance = new CampusOnlinePerson("3", new CampusOnlinePersonName("No", "Attendance"), new CampusOnlineContactData("na@example.com"),
                new CampusOnlineExtension("yy88yyy"), null);

        when(campusOnlineClient.fetchStudents("CO-101")).thenReturn(new CampusOnlineStudentListResponse(List.of(confirmed, unconfirmed, nullAttendance)));
        when(userService.findUserAndAddToCourse(eq("ab12cde"), isNull(), eq("confirmed@example.com"), eq("students"))).thenReturn(Optional.of(new User()));

        CampusOnlineSyncResultDTO result = service.performSingleCourseSync(1L);

        assertThat(result.usersAdded() + result.usersNotFound()).isEqualTo(1);
        // Verify only the confirmed student was processed
        verify(userService, never()).findUserAndAddToCourse(eq("xx99xxx"), any(), any(), any());
        verify(userService, never()).findUserAndAddToCourse(eq("yy88yyy"), any(), any(), any());
    }

    @Test
    void performSingleCourseSync_shouldHandleNullExtensionAndContactData() {
        Course course = createCourseWithConfig(1L, "CO-101", "Test Course", "students");
        when(courseRepository.findWithEagerCampusOnlineConfigurationById(1L)).thenReturn(Optional.of(course));

        // Student with null extension and null contactData
        var student = new CampusOnlinePerson("1", new CampusOnlinePersonName("No", "Data"), null, null, new CampusOnlineAttendance("J"));
        when(campusOnlineClient.fetchStudents("CO-101")).thenReturn(new CampusOnlineStudentListResponse(List.of(student)));
        when(userService.findUserAndAddToCourse(isNull(), isNull(), isNull(), eq("students"))).thenReturn(Optional.empty());

        CampusOnlineSyncResultDTO result = service.performSingleCourseSync(1L);

        assertThat(result.usersNotFound()).isEqualTo(1);
        assertThat(result.usersAdded()).isZero();
        // Verify null values were passed correctly
        verify(userService).findUserAndAddToCourse(isNull(), isNull(), isNull(), eq("students"));
    }

    @Test
    void performSingleCourseSync_shouldCountUsersCorrectly() {
        Course course = createCourseWithConfig(1L, "CO-101", "Test Course", "students");
        when(courseRepository.findWithEagerCampusOnlineConfigurationById(1L)).thenReturn(Optional.of(course));

        var found = createConfirmedStudent("found001", "found@example.com");
        var notFound = createConfirmedStudent("notfound", "notfound@example.com");

        when(campusOnlineClient.fetchStudents("CO-101")).thenReturn(new CampusOnlineStudentListResponse(List.of(found, notFound)));
        when(userService.findUserAndAddToCourse(eq("found001"), isNull(), eq("found@example.com"), eq("students"))).thenReturn(Optional.of(new User()));
        when(userService.findUserAndAddToCourse(eq("notfound"), isNull(), eq("notfound@example.com"), eq("students"))).thenReturn(Optional.empty());

        CampusOnlineSyncResultDTO result = service.performSingleCourseSync(1L);

        assertThat(result.coursesSynced()).isEqualTo(1);
        assertThat(result.coursesFailed()).isZero();
        assertThat(result.usersAdded()).isEqualTo(1);
        assertThat(result.usersNotFound()).isEqualTo(1);
    }

    // ==================== syncEnrollments (scheduled) ====================

    @Test
    void syncEnrollments_shouldSkip_whenSchedulingNotActive() {
        when(profileService.isSchedulingActive()).thenReturn(false);

        service.syncEnrollments();

        verify(campusOnlineClient, never()).fetchStudents(any());
        verify(courseRepository, never()).findAllWithCampusOnlineConfiguration();
    }

    @Test
    void syncEnrollments_shouldSkip_whenDevActive() {
        when(profileService.isSchedulingActive()).thenReturn(true);
        when(profileService.isDevActive()).thenReturn(true);

        service.syncEnrollments();

        verify(campusOnlineClient, never()).fetchStudents(any());
        verify(courseRepository, never()).findAllWithCampusOnlineConfiguration();
    }

    @Test
    void syncEnrollments_shouldExecute_whenSchedulingActiveAndNotDev() {
        when(profileService.isSchedulingActive()).thenReturn(true);
        when(profileService.isDevActive()).thenReturn(false);
        when(courseRepository.findAllWithCampusOnlineConfiguration()).thenReturn(Set.of());

        service.syncEnrollments();

        verify(courseRepository).findAllWithCampusOnlineConfiguration();
    }

    // ==================== Helper methods ====================

    private Course createCourseWithConfig(long id, String campusOnlineCourseId, String title, String studentGroup) {
        Course course = new Course();
        course.setId(id);
        course.setTitle(title);
        course.setStudentGroupName(studentGroup);
        CampusOnlineConfiguration config = new CampusOnlineConfiguration();
        config.setCampusOnlineCourseId(campusOnlineCourseId);
        course.setCampusOnlineConfiguration(config);
        return course;
    }

    private CampusOnlinePerson createConfirmedStudent(String regNumber, String email) {
        return new CampusOnlinePerson("1", new CampusOnlinePersonName("Test", "Student"), new CampusOnlineContactData(email), new CampusOnlineExtension(regNumber),
                new CampusOnlineAttendance("J"));
    }
}
