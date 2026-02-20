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

import de.tum.cit.aet.artemis.core.domain.CampusOnlineConfiguration;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.dto.CampusOnlineCourseDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.CampusOnlineClientService;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.CampusOnlineCourseImportService;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineOrgCourseDTO;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineOrgCoursesResponseDTO;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;

/**
 * Unit tests for {@link CampusOnlineCourseImportService}.
 * Tests search, link, and unlink functionality with mocked dependencies.
 */
class CampusOnlineCourseImportServiceTest {

    private CampusOnlineClientService campusOnlineClient;

    private CourseTestRepository courseRepository;

    private CampusOnlineCourseImportService service;

    @BeforeEach
    void setUp() {
        campusOnlineClient = mock(CampusOnlineClientService.class);
        courseRepository = mock(CourseTestRepository.class);
        service = new CampusOnlineCourseImportService(campusOnlineClient, courseRepository);
    }

    @Test
    void searchCoursesByName_shouldReturnFilteredResults() {
        // Given
        var course1 = new CampusOnlineOrgCourseDTO("CO-101", "Introduction to Computer Science", "2025W");
        var course2 = new CampusOnlineOrgCourseDTO("CO-202", "Advanced Mathematics", "2025W");
        var course3 = new CampusOnlineOrgCourseDTO("CO-303", "Computer Networks", "2025W");
        var response = new CampusOnlineOrgCoursesResponseDTO(List.of(course1, course2, course3));
        when(campusOnlineClient.fetchCoursesForOrg(anyString(), anyString(), anyString())).thenReturn(response);
        when(courseRepository.findAllWithCampusOnlineConfiguration()).thenReturn(Set.of());

        // When
        List<CampusOnlineCourseDTO> results = service.searchCoursesByName("Computer", "12345", "2025W");

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).extracting(CampusOnlineCourseDTO::title).containsExactlyInAnyOrder("Introduction to Computer Science", "Computer Networks");
    }

    @Test
    void searchCoursesByName_shouldReturnEmptyList_whenOrgUnitIdBlank() {
        // When
        List<CampusOnlineCourseDTO> results = service.searchCoursesByName("Computer", "", null);

        // Then
        assertThat(results).isEmpty();
        verify(campusOnlineClient, never()).fetchCoursesForOrg(anyString(), anyString(), anyString());
    }

    @Test
    void searchCoursesByName_shouldReturnEmptyList_whenOrgUnitIdNull() {
        // When
        List<CampusOnlineCourseDTO> results = service.searchCoursesByName("Computer", null, null);

        // Then
        assertThat(results).isEmpty();
        verify(campusOnlineClient, never()).fetchCoursesForOrg(anyString(), anyString(), anyString());
    }

    @Test
    void searchCoursesByName_shouldReturnEmptyList_whenResponseIsNull() {
        // Given
        var response = new CampusOnlineOrgCoursesResponseDTO(null);
        when(campusOnlineClient.fetchCoursesForOrg(anyString(), anyString(), anyString())).thenReturn(response);

        // When
        List<CampusOnlineCourseDTO> results = service.searchCoursesByName("Computer", "12345", null);

        // Then
        assertThat(results).isEmpty();
    }

    @Test
    void searchCoursesByName_shouldMarkAlreadyImportedCourses() {
        // Given
        var course1 = new CampusOnlineOrgCourseDTO("CO-101", "Computer Science", "2025W");
        var response = new CampusOnlineOrgCoursesResponseDTO(List.of(course1));
        when(campusOnlineClient.fetchCoursesForOrg(anyString(), anyString(), anyString())).thenReturn(response);

        Course existingCourse = new Course();
        existingCourse.setId(1L);
        CampusOnlineConfiguration config = new CampusOnlineConfiguration();
        config.setCampusOnlineCourseId("CO-101");
        existingCourse.setCampusOnlineConfiguration(config);
        when(courseRepository.findAllWithCampusOnlineConfiguration()).thenReturn(Set.of(existingCourse));

        // When
        List<CampusOnlineCourseDTO> results = service.searchCoursesByName("Computer", "12345", "2025W");

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().alreadyImported()).isTrue();
    }

    @Test
    void searchCoursesByName_shouldBeCaseInsensitive() {
        // Given
        var course1 = new CampusOnlineOrgCourseDTO("CO-101", "COMPUTER Science", "2025W");
        var response = new CampusOnlineOrgCoursesResponseDTO(List.of(course1));
        when(campusOnlineClient.fetchCoursesForOrg(anyString(), anyString(), anyString())).thenReturn(response);
        when(courseRepository.findAllWithCampusOnlineConfiguration()).thenReturn(Set.of());

        // When
        List<CampusOnlineCourseDTO> results = service.searchCoursesByName("computer", "12345", null);

        // Then
        assertThat(results).hasSize(1);
    }

    @Test
    void linkCourse_shouldCreateConfigurationAndSave() {
        // Given
        Course course = new Course();
        course.setId(1L);
        course.setTitle("Test Course");
        course.setSemester("2025W");
        when(courseRepository.findByIdForUpdateElseThrow(1L)).thenReturn(course);
        when(courseRepository.save(course)).thenReturn(course);
        when(courseRepository.existsByCampusOnlineCourseId("CO-101")).thenReturn(false);

        // When
        CampusOnlineCourseDTO result = service.linkCourse(1L, "CO-101", "Prof. Smith", "CS Department", "Informatik BSc");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.campusOnlineCourseId()).isEqualTo("CO-101");
        assertThat(result.responsibleInstructor()).isEqualTo("Prof. Smith");
        assertThat(result.department()).isEqualTo("CS Department");
        assertThat(result.studyProgram()).isEqualTo("Informatik BSc");
        verify(courseRepository).save(course);
    }

    @Test
    void linkCourse_shouldThrowException_whenCourseIdBlank() {
        assertThatThrownBy(() -> service.linkCourse(1L, "", "Prof. Smith", "CS Dept", "Informatik BSc")).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void linkCourse_shouldThrowException_whenCourseIdNull() {
        assertThatThrownBy(() -> service.linkCourse(1L, null, "Prof. Smith", "CS Dept", "Informatik BSc")).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void linkCourse_shouldThrowException_whenDuplicateLink() {
        // Given - existing course already linked to CO-101
        when(courseRepository.existsByCampusOnlineCourseId("CO-101")).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> service.linkCourse(2L, "CO-101", "Prof. Smith", "CS Dept", "Informatik BSc")).isInstanceOf(BadRequestAlertException.class);
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
        var response = new CampusOnlineOrgCoursesResponseDTO(List.of());
        when(campusOnlineClient.fetchCoursesForOrg("12345", "2025-10-01", "2026-03-31")).thenReturn(response);
        when(courseRepository.findAllWithCampusOnlineConfiguration()).thenReturn(Set.of());

        // When
        service.searchCoursesByName("Test", "12345", "2025W");

        // Then
        verify(campusOnlineClient).fetchCoursesForOrg("12345", "2025-10-01", "2026-03-31");
    }

    @Test
    void searchCoursesByName_shouldUseSemesterDatesForSummerSemester() {
        // Given
        var response = new CampusOnlineOrgCoursesResponseDTO(List.of());
        when(campusOnlineClient.fetchCoursesForOrg("12345", "2025-04-01", "2025-09-30")).thenReturn(response);
        when(courseRepository.findAllWithCampusOnlineConfiguration()).thenReturn(Set.of());

        // When
        service.searchCoursesByName("Test", "12345", "2025S");

        // Then
        verify(campusOnlineClient).fetchCoursesForOrg("12345", "2025-04-01", "2025-09-30");
    }

    @Test
    void searchCoursesByName_shouldHandleInvalidSemesterFormat() {
        // Given - invalid semester format should use dynamic fallback dates
        var response = new CampusOnlineOrgCoursesResponseDTO(List.of());
        when(campusOnlineClient.fetchCoursesForOrg(anyString(), anyString(), anyString())).thenReturn(response);
        when(courseRepository.findAllWithCampusOnlineConfiguration()).thenReturn(Set.of());

        // When - should not throw
        List<CampusOnlineCourseDTO> results = service.searchCoursesByName("Test", "12345", "XX");

        // Then
        assertThat(results).isEmpty();
    }
}
