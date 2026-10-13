package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.lock.DistributedLock;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.globalsearch.domain.IngestionCoverageEntry;
import de.tum.cit.aet.artemis.globalsearch.exception.WeaviateException;
import de.tum.cit.aet.artemis.globalsearch.repository.IngestionCoverageRepository;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageSetLoader.ExpectedSets;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageSetLoader.PresentSets;

/** Regression tests for the provider-backed full-recompute state and its narrowly retried course-row insert. */
class CoverageRecomputeServiceStateTest {

    private final IngestionCoverageSetLoader loader = mock(IngestionCoverageSetLoader.class);

    private final CourseRepository courses = mock(CourseRepository.class);

    private final IngestionCoverageRepository coverage = mock(IngestionCoverageRepository.class);

    private final DistributedDataProvider distributed = mock(DistributedDataProvider.class);

    private final Map<Long, IngestionCoverageEntry> rows = new LinkedHashMap<>();

    private final ExpectedSets expected = new ExpectedSets(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

    private final PresentSets present = new PresentSets(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

    private CoverageRecomputeService service(List<Course> courseList) throws Exception {
        return service(courseList, new LocalDataProviderService());
    }

    private CoverageRecomputeService service(List<Course> courseList, LocalDataProviderService localProvider) throws Exception {
        when(courses.findAll()).thenReturn(courseList);
        when(loader.loadExpected(anyCollection())).thenReturn(expected);
        when(loader.loadPresent(anyCollection())).thenReturn(present);
        when(coverage.findByCourseId(anyLong())).thenAnswer(invocation -> Optional.ofNullable(rows.get(invocation.getArgument(0, Long.class))));
        when(coverage.findAll()).thenAnswer(invocation -> new ArrayList<>(rows.values()));
        when(coverage.save(any(IngestionCoverageEntry.class))).thenAnswer(invocation -> {
            IngestionCoverageEntry row = invocation.getArgument(0);
            rows.put(row.getCourseId(), row);
            return row;
        });
        DistributedLock lock = mock(DistributedLock.class);
        when(lock.tryLock(any(Duration.class))).thenReturn(true);
        when(distributed.getLock("ingestion-coverage-recompute")).thenReturn(lock);
        when(distributed.getExpiringMap(any(), any(Duration.class))).thenAnswer(invocation -> localProvider.getExpiringMap(invocation.getArgument(0), invocation.getArgument(1)));
        return new CoverageRecomputeService(loader, courses, coverage, distributed);
    }

    @Test
    void openingOneCourseMustNotMakeAnIncompleteProjectionFresh() throws Exception {
        Course first = course(1L);
        Course second = course(2L);
        CoverageRecomputeService service = service(List.of(first, second));

        // This is the ordinary default live-matrix -> browser -> status-filter flow on a new installation.
        service.storeCourseCoverage(first, expected, present);
        assertThat(rows.keySet()).containsExactly(1L);
        service.triggerRecomputeIfStale();

        // A stored cross-course view must include the second course too, even if its browser was never opened.
        assertThat(rows.keySet()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void aFailedInitialRefreshMustNotLookLikeASuccessfulEmptyProjection() throws Exception {
        CoverageRecomputeService service = service(List.of(course(1L)));
        when(loader.loadPresent(anyCollection())).thenThrow(new WeaviateException("Connection refused", new IllegalStateException("unavailable")));

        // The resource translates this explicit outcome to HTTP 503 instead of returning a misleading empty 200 response.
        assertThat(service.triggerRecomputeIfStale()).isEqualTo(CoverageRecomputeService.RecomputeOutcome.FAILED);
    }

    @Test
    void failedAttemptUsesTheThirtySecondBackoffButAnExplicitRefreshRetriesIt() throws Exception {
        CoverageRecomputeService service = service(List.of(course(1L)));
        when(loader.loadPresent(anyCollection())).thenThrow(new WeaviateException("Connection refused", new IllegalStateException("unavailable"))).thenReturn(present);

        assertThat(service.triggerRecomputeIfStale()).isEqualTo(CoverageRecomputeService.RecomputeOutcome.FAILED);
        assertThat(service.triggerRecomputeIfStale()).isEqualTo(CoverageRecomputeService.RecomputeOutcome.FAILED);
        assertThat(service.forceRecompute()).isEqualTo(CoverageRecomputeService.RecomputeOutcome.RECOMPUTED);

        verify(distributed).getExpiringMap("ingestion-coverage-successful-full-recompute", Duration.ofMinutes(15));
        verify(distributed).getExpiringMap("ingestion-coverage-failed-recompute", Duration.ofSeconds(30));
    }

    @Test
    void providerMarkerIsSharedBetweenNodesButDoesNotSurviveAProviderRestart() throws Exception {
        LocalDataProviderService sharedProvider = new LocalDataProviderService();
        CoverageRecomputeService firstNode = service(List.of(course(1L)), sharedProvider);

        assertThat(firstNode.forceRecompute()).isEqualTo(CoverageRecomputeService.RecomputeOutcome.RECOMPUTED);
        CoverageRecomputeService secondNode = service(List.of(course(1L)), sharedProvider);
        assertThat(secondNode.triggerRecomputeIfStale()).isEqualTo(CoverageRecomputeService.RecomputeOutcome.FRESH);

        CoverageRecomputeService afterProviderRestart = service(List.of(course(1L)), new LocalDataProviderService());
        assertThat(afterProviderRestart.triggerRecomputeIfStale()).isEqualTo(CoverageRecomputeService.RecomputeOutcome.RECOMPUTED);
    }

    @Test
    void concurrentFirstInsertUpdatesTheRowThatWonTheCourseIdConstraint() throws Exception {
        CoverageRecomputeService service = service(List.of(course(1L)));
        IngestionCoverageEntry concurrentEntry = new IngestionCoverageEntry();
        concurrentEntry.setId(9876L);
        concurrentEntry.setCourseId(1L);
        AtomicInteger saves = new AtomicInteger();

        when(coverage.save(any(IngestionCoverageEntry.class))).thenAnswer(invocation -> {
            IngestionCoverageEntry entry = invocation.getArgument(0);
            if (saves.getAndIncrement() == 0) {
                rows.put(1L, concurrentEntry);
                throw uniqueCourseIdViolation();
            }
            rows.put(entry.getCourseId(), entry);
            return entry;
        });

        service.recomputeAllCourses();

        assertThat(saves.get()).isEqualTo(2);
        assertThat(rows.get(1L).getId()).isEqualTo(9876L);
    }

    @Test
    void aDifferentIntegrityViolationIsNotRetriedAsACourseIdCollision() throws Exception {
        CoverageRecomputeService service = service(List.of(course(1L)));
        DataIntegrityViolationException otherConstraint = new DataIntegrityViolationException("constraint violation", new ConstraintViolationException("constraint violation",
                new SQLException("constraint violation"), "insert", ConstraintViolationException.ConstraintKind.UNIQUE, "ux_ingestion_coverage_different_column"));
        when(coverage.save(any(IngestionCoverageEntry.class))).thenThrow(otherConstraint);

        assertThatThrownBy(service::recomputeAllCourses).isSameAs(otherConstraint);
    }

    private static DataIntegrityViolationException uniqueCourseIdViolation() {
        return new DataIntegrityViolationException("constraint violation", new ConstraintViolationException("constraint violation", new SQLException("duplicate key value"),
                "insert", ConstraintViolationException.ConstraintKind.UNIQUE, "ux_ingestion_coverage_course_id"));
    }

    private static Course course(long id) {
        Course course = new Course();
        course.setId(id);
        course.setTitle("Review course " + id);
        return course;
    }
}
