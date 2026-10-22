package de.tum.cit.aet.artemis.globalsearch.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.globalsearch.dto.CourseBrowserDataDTO;
import de.tum.cit.aet.artemis.globalsearch.exception.WeaviateException;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionBrowserService;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionBrowserWeaviateReadService;

class IngestionBrowserResourceTest {

    private final CourseRepository courses = mock(CourseRepository.class);

    private final IngestionBrowserService browser = mock(IngestionBrowserService.class);

    private final IngestionBrowserWeaviateReadService reads = mock(IngestionBrowserWeaviateReadService.class);

    private final IngestionBrowserResource resource = new IngestionBrowserResource(browser, reads, courses);

    @Test
    void unknownCourseStopsEveryReadBeforeQueryValidation() {
        var missing = new EntityNotFoundException("Course", 7L);
        when(courses.findByIdElseThrow(7L)).thenThrow(missing);
        assertThatThrownBy(() -> resource.getCourseBrowserData(7L)).isSameAs(missing);
        assertThatThrownBy(() -> resource.getIndexedEntityRecords(7L, "")).isSameAs(missing);
        assertThatThrownBy(() -> resource.getUnitContent(7L, 0L, "")).isSameAs(missing);
        verifyNoInteractions(browser, reads);
    }

    @Test
    void invalidTypeOrKeyNeverReachesTheIndex() {
        when(courses.findByIdElseThrow(7L)).thenReturn(mock(Course.class));
        for (String invalid : List.of("", "unknown", "lecture+unit")) {
            assertThatThrownBy(() -> resource.getIndexedEntityRecords(7L, invalid)).isInstanceOf(BadRequestAlertException.class);
            assertThatThrownBy(() -> resource.getUnitContent(7L, 0L, invalid)).isInstanceOf(BadRequestAlertException.class);
        }
        verifyNoInteractions(browser, reads);
    }

    @Test
    void delegatesTheCombinedLoadAndScopedLazyReadsWithoutExtraWork() {
        var course = mock(Course.class);
        var payload = new CourseBrowserDataDTO(List.of(), List.of(), List.of(), List.of(), List.of());
        when(courses.findByIdElseThrow(7L)).thenReturn(course);
        when(browser.loadCourseBrowserData(course)).thenReturn(payload);
        when(reads.listIndexedEntityRecords(7L, "lecture_unit")).thenReturn(List.of());
        when(reads.listContentObjectsForUnit(7L, 0L, "slides")).thenReturn(List.of());
        assertThat(resource.getCourseBrowserData(7L).getBody()).isSameAs(payload);
        assertThat(resource.getIndexedEntityRecords(7L, "lecture_unit").getBody()).isEmpty();
        assertThat(resource.getUnitContent(7L, 0L, "slides").getBody()).isEmpty();
        verify(browser).loadCourseBrowserData(course);
        verify(reads).listIndexedEntityRecords(7L, "lecture_unit");
        verify(reads).listContentObjectsForUnit(7L, 0L, "slides");
    }

    @Test
    void unreadableContentRemainsAFailureRatherThanAnEmptySuccess() {
        var failure = new WeaviateException("unreadable", new IllegalStateException("unavailable"));
        when(courses.findByIdElseThrow(7L)).thenReturn(mock(Course.class));
        when(reads.listContentObjectsForUnit(7L, 0L, "slides")).thenThrow(failure);
        assertThatThrownBy(() -> resource.getUnitContent(7L, 0L, "slides")).isSameAs(failure);
    }
}
