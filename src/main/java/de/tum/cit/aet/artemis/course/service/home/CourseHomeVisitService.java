package de.tum.cit.aet.artemis.course.service.home;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.course.domain.CourseHomeVisit;
import de.tum.cit.aet.artemis.course.repository.CourseHomeVisitRepository;

/** Reading Home does not persist a visit; the client acknowledges a successful render. */
@Service
@Lazy
@Profile(PROFILE_CORE)
public class CourseHomeVisitService {

    private final CourseHomeVisitRepository repository;

    public CourseHomeVisitService(CourseHomeVisitRepository repository) {
        this.repository = repository;
    }

    public record VisitState(@Nullable Instant firstSeenAt, @Nullable Instant lastSeenAt, long variantCursor) {
    }

    public VisitState read(long courseId, long userId) {
        return repository.findByCourseIdAndUserId(courseId, userId).map(v -> new VisitState(v.getFirstSeenAt(), v.getLastSeenAt(), v.getVariantCursor()))
                .orElse(new VisitState(null, null, 0));
    }

    public void acknowledge(long courseId, long userId, Instant snapshotAt, long observedVariantCursor, Instant receivedAt) {
        if (snapshotAt == null || observedVariantCursor < 0 || snapshotAt.isAfter(receivedAt.plusSeconds(5)) || snapshotAt.isBefore(receivedAt.minusSeconds(900))) {
            throw new BadRequestAlertException("Invalid Home visit acknowledgment", "courseHome", "invalidVisit");
        }
        Instant snapshot = (snapshotAt.isAfter(receivedAt) ? receivedAt : snapshotAt).truncatedTo(ChronoUnit.MILLIS);
        if (repository.acknowledge(courseId, userId, snapshot, observedVariantCursor) != 0) {
            return;
        }
        if (observedVariantCursor != 0) {
            throw new BadRequestAlertException("Unknown Home visit cursor", "courseHome", "invalidVisit");
        }
        var visit = new CourseHomeVisit();
        visit.setCourseId(courseId);
        visit.setUserId(userId);
        visit.setFirstSeenAt(snapshot);
        visit.setLastSeenAt(snapshot);
        visit.setVariantCursor(1);
        try {
            repository.saveAndFlush(visit);
        }
        catch (DataIntegrityViolationException ex) {
            // The insert transaction has ended. A competing tab may have inserted the unique row.
            if (repository.acknowledge(courseId, userId, snapshot, observedVariantCursor) == 0) {
                throw ex;
            }
        }
    }
}
