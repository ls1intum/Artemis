package de.tum.cit.aet.artemis.course.service.home;

import static de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase.BEFORE_START;
import static de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase.CAUGHT_UP;
import static de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase.COURSE_ENDED;
import static de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase.DEADLINE_SOON;
import static de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase.DEADLINE_TODAY;
import static de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase.EXAM_SOON;
import static de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase.FIRST_VISIT;
import static de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase.RETURNING;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeTargetDTO;

class CourseHomeContextServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");

    private final CourseHomeContextService service = new CourseHomeContextService();

    @ParameterizedTest
    @EnumSource(CourseHomeWelcomeCase.class)
    void selectsEveryContext(CourseHomeWelcomeCase expected) {
        Instant start = expected == BEFORE_START ? NOW.plusSeconds(1) : null;
        Instant end = expected == COURSE_ENDED ? NOW : null;
        boolean first = expected == FIRST_VISIT;
        Instant previous = expected == RETURNING ? NOW.minus(Duration.ofDays(7)) : NOW.minusSeconds(60);
        var candidates = switch (expected) {
            case BEFORE_START, COURSE_ENDED, FIRST_VISIT, RETURNING, CAUGHT_UP -> List.<CourseHomeContext.Candidate>of();
            default -> List.of(candidate(expected, 1, (expected == DEADLINE_TODAY || expected == EXAM_SOON) ? NOW.plusSeconds(3600) : NOW.plusSeconds(48 * 3600)));
        };
        var selected = service.select(new CourseHomeContext(NOW, start, end, first, previous, candidates));
        assertThat(selected.welcomeCase()).isEqualTo(expected);
    }

    @Test
    void urgentDeadlineBeatsFirstVisitAndPinsTarget() {
        var selected = service.select(new CourseHomeContext(NOW, null, null, true, null, List.of(candidate(DEADLINE_SOON, 42, NOW.plus(Duration.ofHours(24))))));
        assertThat(selected.welcomeCase()).isEqualTo(DEADLINE_TODAY);
        assertThat(selected.nextStep().target().id()).isEqualTo(42);
    }

    @Test
    void deadlineBoundariesAndStableTies() {
        assertThat(selectDeadline(Duration.ofHours(24).plusMillis(1)).welcomeCase()).isEqualTo(DEADLINE_SOON);
        assertThat(selectDeadline(Duration.ofHours(72)).welcomeCase()).isEqualTo(DEADLINE_SOON);
        assertThat(selectDeadline(Duration.ofHours(72).plusMillis(1)).welcomeCase()).isEqualTo(CAUGHT_UP);
        assertThat(selectDeadline(Duration.ZERO).welcomeCase()).isEqualTo(CAUGHT_UP);
        var context = new CourseHomeContext(NOW, null, null, false, NOW, List.of(candidate(DEADLINE_SOON, 9, NOW.plusSeconds(1)), candidate(DEADLINE_SOON, 3, NOW.plusSeconds(1))));
        assertThat(service.select(context).nextStep().target().id()).isEqualTo(3);
    }

    @Test
    void lifecycleDoesNotTurnUnfinishedWorkIntoCompletion() {
        var selection = service.select(new CourseHomeContext(NOW, null, NOW, false, NOW, List.of(candidate(DEADLINE_SOON, 9, NOW.plusSeconds(1)))));
        assertThat(selection.welcomeCase()).isEqualTo(COURSE_ENDED);
        assertThat(selection.nextStep()).isNull();
    }

    private CourseHomeSelection selectDeadline(Duration duration) {
        return service.select(new CourseHomeContext(NOW, null, null, false, NOW, List.of(candidate(DEADLINE_SOON, 42, NOW.plus(duration)))));
    }

    private CourseHomeContext.Candidate candidate(CourseHomeWelcomeCase type, long id, Instant due) {
        return new CourseHomeContext.Candidate(type, new CourseHomeTargetDTO(CourseHomeTargetDTO.Kind.EXERCISE, id, null, null), "Exercise", due, NOW, true);
    }
}
