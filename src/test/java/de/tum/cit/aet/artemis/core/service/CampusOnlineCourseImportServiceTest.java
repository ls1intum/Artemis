package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.domain.CampusOnlineConfiguration;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.dto.CampusOnlineCourseDTO;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.CampusOnlineClient;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.CampusOnlineCourseImportService;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineOrgCourse;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineOrgCoursesResponse;

/**
 * Unit tests for {@link CampusOnlineCourseImportService}.
 * Tests search, link, and unlink functionality with mocked dependencies.
 */
class CampusOnlineCourseImportServiceTest {

    private CampusOnlineClient campusOnlineClient;

    private CourseRepository courseRepository;

    private CampusOnlineCourseImportService service;

    @BeforeEach
    void setUp() {
        campusOnlineClient = mock(CampusOnlineClient.class);
        courseRepository = mock(CourseRepository.class);
        service = new CampusOnlineCourseImportService(campusOnlineClient, courseRepository);
        ReflectionTestUtils.setField(service, "defaultOrgUnitId", "12345");
    }

    @Test
    void searchCoursesByName_shouldReturnFilteredResults() {
        // Given
        var course1 = new CampusOnlineOrgCourse("CO-101", "Introduction to Computer Science", "2025W");
        var course2 = new CampusOnlineOrgCourse("CO-202", "Advanced Mathematics", "2025W");
        var course3 = new CampusOnlineOrgCourse("CO-303", "Computer Networks", "2025W");
        var response = new CampusOnlineOrgCoursesResponse(List.of(course1, course2, course3));
        when(campusOnlineClient.fetchCoursesForOrg(anyString(), anyString(), anyString())).thenReturn(response);
        when(courseRepository.findAllWithCampusOnlineConfiguration()).thenReturn(Set.of());

        // When
        List<CampusOnlineCourseDTO> results = service.searchCoursesByName("Computer", "2025W");

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).extracting(CampusOnlineCourseDTO::title).containsExactlyInAnyOrder("Introduction to Computer Science", "Computer Networks");
    }

    @Test
    void searchCoursesByName_shouldReturnEmptyList_whenNoDefaultOrgUnit() {
        // Given
        ReflectionTestUtils.setField(service, "defaultOrgUnitId", "");

        // When
        List<CampusOnlineCourseDTO> results = service.searchCoursesByName("Computer", null);

        // Then
        assertThat(results).isEmpty();
        verify(campusOnlineClient, never()).fetchCoursesForOrg(anyString(), anyString(), anyString());
    }

    @Test
    void searchCoursesByName_shouldReturnEmptyList_whenResponseIsNull() {
        // Given
        var response = new CampusOnlineOrgCoursesResponse(null);
        when(campusOnlineClient.fetchCoursesForOrg(anyString(), anyString(), anyString())).thenReturn(response);

        // When
        List<CampusOnlineCourseDTO> results = service.searchCoursesByName("Computer", null);

        // Then
        assertThat(results).isEmpty();
    }

    @Test
    void searchCoursesByName_shouldMarkAlreadyImportedCourses() {
        // Given
        var course1 = new CampusOnlineOrgCourse("CO-101", "Computer Science", "2025W");
        var response = new CampusOnlineOrgCoursesResponse(List.of(course1));
        when(campusOnlineClient.fetchCoursesForOrg(anyString(), anyString(), anyString())).thenReturn(response);

        Course existingCourse = new Course();
        existingCourse.setId(1L);
        CampusOnlineConfiguration config = new CampusOnlineConfiguration();
        config.setCampusOnlineCourseId("CO-101");
        existingCourse.setCampusOnlineConfiguration(config);
        when(courseRepository.findAllWithCampusOnlineConfiguration()).thenReturn(Set.of(existingCourse));

        // When
        List<CampusOnlineCourseDTO> results = service.searchCoursesByName("Computer", "2025W");

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().alreadyImported()).isTrue();
    }

    @Test
    void searchCoursesByName_shouldBeCaseInsensitive() {
        // Given
        var course1 = new CampusOnlineOrgCourse("CO-101", "COMPUTER Science", "2025W");
        var response = new CampusOnlineOrgCoursesResponse(List.of(course1));
        when(campusOnlineClient.fetchCoursesForOrg(anyString(), anyString(), anyString())).thenReturn(response);
        when(courseRepository.findAllWithCampusOnlineConfiguration()).thenReturn(Set.of());

        // When
        List<CampusOnlineCourseDTO> results = service.searchCoursesByName("computer", null);

        // Then
        assertThat(results).hasSize(1);
    }

    @Test
    void linkCourse_shouldCreateConfigurationAndSave() {
        // Given
        Course course = new Course();
        course.setId(1L);
        when(courseRepository.findByIdForUpdateElseThrow(1L)).thenReturn(course);
        when(courseRepository.save(course)).thenReturn(course);

        // When
        Course result = service.linkCourse(1L, "CO-101", "Prof. Smith", "CS Department", "Informatik BSc");

        // Then
        assertThat(result.getCampusOnlineConfiguration()).isNotNull();
        assertThat(result.getCampusOnlineConfiguration().getCampusOnlineCourseId()).isEqualTo("CO-101");
        assertThat(result.getCampusOnlineConfiguration().getResponsibleInstructor()).isEqualTo("Prof. Smith");
        assertThat(result.getCampusOnlineConfiguration().getDepartment()).isEqualTo("CS Department");
        assertThat(result.getCampusOnlineConfiguration().getStudyProgram()).isEqualTo("Informatik BSc");
        verify(courseRepository).save(course);
    }

    @Test
    void linkCourse_shouldThrowException_whenCourseIdBlank() {
        assertThatThrownBy(() -> service.linkCourse(1L, "", "Prof. Smith", "CS Dept", "Informatik BSc")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void linkCourse_shouldThrowException_whenCourseIdNull() {
        assertThatThrownBy(() -> service.linkCourse(1L, null, "Prof. Smith", "CS Dept", "Informatik BSc")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void unlinkCourse_shouldRemoveConfigurationAndSave() {
        // Given
        Course course = new Course();
        course.setId(1L);
        CampusOnlineConfiguration config = new CampusOnlineConfiguration();
        config.setCampusOnlineCourseId("CO-101");
        course.setCampusOnlineConfiguration(config);
        when(courseRepository.findByIdForUpdateElseThrow(1L)).thenReturn(course);
        when(courseRepository.save(course)).thenReturn(course);

        // When
        service.unlinkCourse(1L);

        // Then
        assertThat(course.getCampusOnlineConfiguration()).isNull();
        verify(courseRepository).save(course);
    }

    @Test
    void searchCoursesByName_shouldUseSemesterDatesForWinterSemester() {
        // Given
        var response = new CampusOnlineOrgCoursesResponse(List.of());
        when(campusOnlineClient.fetchCoursesForOrg("12345", "2025-10-01", "2026-03-31")).thenReturn(response);
        when(courseRepository.findAllWithCampusOnlineConfiguration()).thenReturn(Set.of());

        // When
        service.searchCoursesByName("Test", "2025W");

        // Then
        verify(campusOnlineClient).fetchCoursesForOrg("12345", "2025-10-01", "2026-03-31");
    }

    @Test
    void searchCoursesByName_shouldUseSemesterDatesForSummerSemester() {
        // Given
        var response = new CampusOnlineOrgCoursesResponse(List.of());
        when(campusOnlineClient.fetchCoursesForOrg("12345", "2025-04-01", "2025-09-30")).thenReturn(response);
        when(courseRepository.findAllWithCampusOnlineConfiguration()).thenReturn(Set.of());

        // When
        service.searchCoursesByName("Test", "2025S");

        // Then
        verify(campusOnlineClient).fetchCoursesForOrg("12345", "2025-04-01", "2025-09-30");
    }

    @Test
    void searchCoursesByName_shouldHandleInvalidSemesterFormat() {
        // Given - invalid semester format should use dynamic fallback dates
        var response = new CampusOnlineOrgCoursesResponse(List.of());
        when(campusOnlineClient.fetchCoursesForOrg(anyString(), anyString(), anyString())).thenReturn(response);
        when(courseRepository.findAllWithCampusOnlineConfiguration()).thenReturn(Set.of());

        // When - should not throw
        List<CampusOnlineCourseDTO> results = service.searchCoursesByName("Test", "XX");

        // Then
        assertThat(results).isEmpty();
    }
}
