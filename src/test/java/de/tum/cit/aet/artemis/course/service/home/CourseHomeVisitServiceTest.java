package de.tum.cit.aet.artemis.course.service.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.course.domain.CourseHomeVisit;
import de.tum.cit.aet.artemis.course.repository.CourseHomeVisitRepository;

class CourseHomeVisitServiceTest {

    private final CourseHomeVisitRepository repository = mock(CourseHomeVisitRepository.class);

    private final CourseHomeVisitService service = new CourseHomeVisitService(repository);

    private final Instant now = Instant.parse("2026-10-25T12:00:00Z");

    @Test
    void readDoesNotInsert() {
        when(repository.findByCourseIdAndUserId(1, 2)).thenReturn(Optional.empty());
        assertThat(service.read(1, 2).variantCursor()).isZero();
        assertThat(service.read(1, 2).lastSeenAt()).isNull();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsFutureExpiredAndNegativeCursors() {
        assertThatThrownBy(() -> service.acknowledge(1, 2, now.plusSeconds(6), 0, now)).isInstanceOf(BadRequestAlertException.class);
        assertThatThrownBy(() -> service.acknowledge(1, 2, now.minusSeconds(901), 0, now)).isInstanceOf(BadRequestAlertException.class);
        assertThatThrownBy(() -> service.acknowledge(1, 2, now, -1, now)).isInstanceOf(BadRequestAlertException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void clampsClockSkewAndCreatesOnlyOnSuccessfulAcknowledgment() {
        service.acknowledge(1, 2, now.plusSeconds(2), 0, now);
        verify(repository).saveAndFlush(argThat(visit -> visit.getLastSeenAt().equals(now) && visit.getVariantCursor() == 1 && visit.getUserId() == 2));
    }

    @Test
    void concurrentInsertIsRetriedOutsideFailedTransaction() {
        when(repository.acknowledge(1, 2, now, 0)).thenReturn(0, 1);
        when(repository.saveAndFlush(any(CourseHomeVisit.class))).thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));
        service.acknowledge(1, 2, now, 0, now);
        verify(repository, times(2)).acknowledge(1, 2, now, 0);
    }
}
