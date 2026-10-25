package de.tum.cit.aet.artemis.course.service.home;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase.BEFORE_START;
import static de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase.CAUGHT_UP;
import static de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase.COURSE_ENDED;
import static de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase.DEADLINE_SOON;
import static de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase.DEADLINE_TODAY;
import static de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase.EXAM_SOON;
import static de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase.FIRST_VISIT;
import static de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase.NEW_FEEDBACK;
import static de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase.RETURNING;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.course.dto.home.CourseHomeDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeTargetDTO;

/** Selects a useful next step without making claims about grades or mastery. */
@Service
@Lazy
@Profile(PROFILE_CORE)
public class CourseHomeContextService {

    public CourseHomeSelection select(CourseHomeContext context) {
        if (context.startDate() != null && context.startDate().isAfter(context.asOf())) {
            return new CourseHomeSelection(BEFORE_START, reviewItem(context));
        }
        if (context.endDate() != null && !context.endDate().isAfter(context.asOf())) {
            return new CourseHomeSelection(COURSE_ENDED, reviewItem(context));
        }
        var candidates = context.candidates().stream().filter(CourseHomeContext.Candidate::actionable).map(c -> normalizeDeadline(c, context.asOf()))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt((CourseHomeContext.Candidate c) -> c.welcomeCase().ordinal())
                        .thenComparing(CourseHomeContext.Candidate::dueAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CourseHomeContext.Candidate::activityAt, Comparator.nullsLast(Comparator.reverseOrder())).thenComparingLong(c -> c.target().id()))
                .toList();
        var first = candidates.isEmpty() ? null : candidates.getFirst();
        if (context.firstVisit() && (first == null || first.welcomeCase().ordinal() > FIRST_VISIT.ordinal())) {
            return new CourseHomeSelection(FIRST_VISIT, first == null ? null : item(first));
        }
        boolean returning = context.previousVisitAt() != null && !context.previousVisitAt().plus(Duration.ofDays(7)).isAfter(context.asOf());
        if (returning && (first == null || first.welcomeCase().ordinal() > RETURNING.ordinal())) {
            return new CourseHomeSelection(RETURNING, first == null ? null : item(first));
        }
        return new CourseHomeSelection(first == null ? CAUGHT_UP : first.welcomeCase(), first == null ? null : item(first));
    }

    private CourseHomeContext.Candidate normalizeDeadline(CourseHomeContext.Candidate c, Instant now) {
        if (c.welcomeCase() != DEADLINE_TODAY && c.welcomeCase() != DEADLINE_SOON && c.welcomeCase() != EXAM_SOON) {
            return c;
        }
        if (c.dueAt() == null || !c.dueAt().isAfter(now)) {
            return null;
        }
        var hours = Duration.between(now, c.dueAt());
        if (c.welcomeCase() == EXAM_SOON) {
            return hours.compareTo(Duration.ofHours(24)) <= 0 ? c : null;
        }
        if (hours.compareTo(Duration.ofHours(72)) > 0) {
            return null;
        }
        return new CourseHomeContext.Candidate(hours.compareTo(Duration.ofHours(24)) <= 0 ? DEADLINE_TODAY : DEADLINE_SOON, c.target(), c.title(), c.dueAt(), c.activityAt(), true);
    }

    private CourseHomeDTO.Item reviewItem(CourseHomeContext context) {
        return context.candidates().stream().filter(c -> c.target().kind() == CourseHomeTargetDTO.Kind.LECTURE || c.welcomeCase() == NEW_FEEDBACK)
                .sorted(Comparator.comparingLong(c -> c.target().id())).map(this::item).findFirst().orElse(null);
    }

    private CourseHomeDTO.Item item(CourseHomeContext.Candidate c) {
        return new CourseHomeDTO.Item(c.title(), c.target(), c.activityAt(), c.dueAt(), c.welcomeCase().name());
    }
}
