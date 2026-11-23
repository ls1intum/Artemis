package de.tum.cit.aet.artemis.course.service.home;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeTargetDTO;

/** Authorized scalar inputs; the selector never fetches data or reads a clock. */
public record CourseHomeContext(Instant asOf, @Nullable Instant startDate, @Nullable Instant endDate, boolean firstVisit, @Nullable Instant previousVisitAt,
        List<Candidate> candidates) {

    public record Candidate(CourseHomeWelcomeCase welcomeCase, CourseHomeTargetDTO target, String title, @Nullable Instant dueAt, @Nullable Instant activityAt,
            boolean actionable) {
    }
}
