package de.tum.cit.aet.artemis.metis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.metis.domain.MetisExerciseConfig;
import de.tum.cit.aet.artemis.metis.domain.MetisGroupWindow;
import de.tum.cit.aet.artemis.metis.dto.MetisGroupMembershipDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisStudentGroupWindowDTO;
import de.tum.cit.aet.artemis.metis.repository.MetisGroupWindowRepository;
import de.tum.cit.aet.artemis.metis.service.MetisWindowService.EffectiveWindow;

/**
 * Unit tests of the window in which a student may answer the comprehension check: which window applies, how the effective due date moves its start, and
 * when a window still accepts a new question.
 */
class MetisWindowServiceTest {

    /** The due date of the exercise; every other time is an offset from it. */
    private static final ZonedDateTime DUE = ZonedDateTime.parse("2026-03-02T10:00:00Z");

    private static final ZonedDateTime CONFIG_START = DUE.plusHours(1);

    private static final ZonedDateTime CONFIG_END = DUE.plusHours(3);

    private static final long EXERCISE_ID = 11L;

    private static final long STUDENT_ID = 21L;

    private static final int MIN_SECONDS = 60;

    private final MetisGroupWindowRepository groupWindowRepository = mock(MetisGroupWindowRepository.class);

    private final MetisWindowService windowService = new MetisWindowService(groupWindowRepository);

    @Nested
    class Resolve {

        @Test
        void shouldUseTheWindowOfTheConfigurationWithoutGroupTitle() {
            EffectiveWindow window = MetisWindowService.resolve(CONFIG_START, CONFIG_END, List.of(), DUE);

            assertThat(window).isEqualTo(new EffectiveWindow(CONFIG_START, CONFIG_END, null));
        }

        @Test
        void shouldStartAtTheDueDateWhenTheConfigurationHasNoStart() {
            EffectiveWindow window = MetisWindowService.resolve(null, CONFIG_END, List.of(), DUE);

            assertThat(window).isEqualTo(new EffectiveWindow(DUE, CONFIG_END, null));
        }

        @Test
        void shouldMoveTheStartToALaterIndividualDueDate() {
            ZonedDateTime extendedDueDate = CONFIG_START.plusMinutes(30);

            EffectiveWindow window = MetisWindowService.resolve(CONFIG_START, CONFIG_END, List.of(), extendedDueDate);

            assertThat(window).isEqualTo(new EffectiveWindow(extendedDueDate, CONFIG_END, null));
        }

        @Test
        void shouldLeaveAnEmptyWindowWhenTheIndividualDueDateLiesAfterTheEnd() {
            ZonedDateTime extendedDueDate = CONFIG_END.plusHours(1);

            EffectiveWindow window = MetisWindowService.resolve(CONFIG_START, CONFIG_END, List.of(), extendedDueDate);

            assertThat(window).isEqualTo(new EffectiveWindow(extendedDueDate, CONFIG_END, null));
            assertThat(window.hasTimeLeft(DUE, MIN_SECONDS)).isFalse();
            assertThat(window.hasTimeLeft(extendedDueDate.plusMinutes(1), MIN_SECONDS)).isFalse();
            assertThat(window.acceptsNewQuestion(extendedDueDate.plusMinutes(1), MIN_SECONDS)).isFalse();
        }

        @Test
        void shouldPreferAGroupWindowAndNameItsGroup() {
            var groupWindow = new MetisStudentGroupWindowDTO(5L, "Monday group", DUE.plusDays(1), DUE.plusDays(1).plusHours(2));

            EffectiveWindow window = MetisWindowService.resolve(CONFIG_START, CONFIG_END, List.of(groupWindow), DUE);

            assertThat(window).isEqualTo(new EffectiveWindow(DUE.plusDays(1), DUE.plusDays(1).plusHours(2), "Monday group"));
        }

        @Test
        void shouldTakeTheEarliestOfSeveralGroupWindowsWhateverTheirOrder() {
            var tuesday = new MetisStudentGroupWindowDTO(6L, "Tuesday group", DUE.plusDays(2), DUE.plusDays(2).plusHours(2));
            var mondayLong = new MetisStudentGroupWindowDTO(7L, "Monday long", DUE.plusDays(1), DUE.plusDays(1).plusHours(4));
            var mondayShort = new MetisStudentGroupWindowDTO(8L, "Monday short", DUE.plusDays(1), DUE.plusDays(1).plusHours(2));

            EffectiveWindow window = MetisWindowService.resolve(null, null, List.of(tuesday, mondayLong, mondayShort), DUE);

            assertThat(window).isEqualTo(new EffectiveWindow(DUE.plusDays(1), DUE.plusDays(1).plusHours(2), "Monday short"));
        }

        @Test
        void shouldMoveTheStartOfAGroupWindowToTheEffectiveDueDate() {
            var groupWindow = new MetisStudentGroupWindowDTO(5L, "Monday group", DUE.minusHours(1), DUE.plusHours(2));

            EffectiveWindow window = MetisWindowService.resolve(CONFIG_START, CONFIG_END, List.of(groupWindow), DUE);

            assertThat(window).isEqualTo(new EffectiveWindow(DUE, DUE.plusHours(2), "Monday group"));
        }

        @Test
        void shouldHaveNoEndWhenTheConfigurationHasNone() {
            EffectiveWindow window = MetisWindowService.resolve(CONFIG_START, null, List.of(), DUE);

            assertThat(window).isEqualTo(new EffectiveWindow(CONFIG_START, null, null));
            assertThat(window.hasEnded(DUE.plusYears(1))).isFalse();
            assertThat(window.hasTimeLeft(DUE.plusYears(1), MIN_SECONDS)).isTrue();
            assertThat(window.acceptsNewQuestion(DUE.plusYears(1), MIN_SECONDS)).isTrue();
        }

        @Test
        void shouldStartAtTheStartOfTheConfigurationWithoutDueDate() {
            assertThat(MetisWindowService.resolve(CONFIG_START, CONFIG_END, List.of(), null)).isEqualTo(new EffectiveWindow(CONFIG_START, CONFIG_END, null));
            assertThat(MetisWindowService.resolve(null, null, List.of(), null)).isEqualTo(new EffectiveWindow(null, null, null));
        }
    }

    @Nested
    class Timing {

        private final EffectiveWindow window = new EffectiveWindow(CONFIG_START, CONFIG_END, null);

        @Test
        void shouldOpenAtTheStartAndEndAtTheEnd() {
            assertThat(window.hasOpened(CONFIG_START.minusSeconds(1))).isFalse();
            assertThat(window.hasOpened(CONFIG_START)).isTrue();
            assertThat(window.hasEnded(CONFIG_END.minusSeconds(1))).isFalse();
            assertThat(window.hasEnded(CONFIG_END)).isTrue();
            assertThat(window.isOpen(CONFIG_START.minusSeconds(1))).isFalse();
            assertThat(window.isOpen(CONFIG_START)).isTrue();
            assertThat(window.isOpen(CONFIG_END.minusSeconds(1))).isTrue();
            assertThat(window.isOpen(CONFIG_END)).isFalse();
        }

        @Test
        void shouldNeverOpenWithoutStart() {
            EffectiveWindow withoutStart = new EffectiveWindow(null, CONFIG_END, null);

            assertThat(withoutStart.hasOpened(CONFIG_START)).isFalse();
            assertThat(withoutStart.acceptsNewQuestion(CONFIG_START, MIN_SECONDS)).isFalse();
        }

        @Test
        void shouldHaveTimeLeftBeforeTheStartWhenTheWholeWindowIsLongEnough() {
            assertThat(window.hasTimeLeft(DUE, MIN_SECONDS)).isTrue();
            assertThat(new EffectiveWindow(CONFIG_START, CONFIG_START.plusSeconds(MIN_SECONDS - 1), null).hasTimeLeft(DUE, MIN_SECONDS)).isFalse();
        }

        @Test
        void shouldHaveTimeLeftInsideTheWindowUntilLessThanOneQuestionRemains() {
            assertThat(window.hasTimeLeft(CONFIG_START.plusMinutes(10), MIN_SECONDS)).isTrue();
            assertThat(window.hasTimeLeft(CONFIG_END.minusSeconds(MIN_SECONDS), MIN_SECONDS)).isTrue();
            assertThat(window.hasTimeLeft(CONFIG_END.minusSeconds(MIN_SECONDS - 1), MIN_SECONDS)).isFalse();
        }

        @Test
        void shouldHaveNoTimeLeftAfterTheEndOrInAnEmptyWindow() {
            assertThat(window.hasTimeLeft(CONFIG_END, MIN_SECONDS)).isFalse();
            assertThat(window.hasTimeLeft(CONFIG_END.plusHours(1), MIN_SECONDS)).isFalse();
            EffectiveWindow empty = new EffectiveWindow(CONFIG_END, CONFIG_START, null);
            assertThat(empty.hasTimeLeft(DUE, MIN_SECONDS)).isFalse();
        }

        @Test
        void shouldAcceptANewQuestionOnlyInsideTheWindowWithTimeLeft() {
            assertThat(window.acceptsNewQuestion(DUE, MIN_SECONDS)).as("before the start").isFalse();
            assertThat(window.acceptsNewQuestion(CONFIG_START, MIN_SECONDS)).as("at the start").isTrue();
            assertThat(window.acceptsNewQuestion(CONFIG_END.minusSeconds(MIN_SECONDS), MIN_SECONDS)).as("exactly one question left").isTrue();
            assertThat(window.acceptsNewQuestion(CONFIG_END.minusSeconds(MIN_SECONDS - 1), MIN_SECONDS)).as("less than one question left").isFalse();
            assertThat(window.acceptsNewQuestion(CONFIG_END, MIN_SECONDS)).as("at the end").isFalse();
        }
    }

    @Nested
    class ResolveForStudent {

        @Test
        void shouldReadTheGroupWindowsOfTheStudent() {
            var groupWindow = new MetisStudentGroupWindowDTO(5L, "Monday group", DUE.plusDays(1), DUE.plusDays(1).plusHours(2));
            when(groupWindowRepository.findGroupWindowsOfStudent(EXERCISE_ID, STUDENT_ID)).thenReturn(List.of(groupWindow));

            EffectiveWindow window = windowService.resolveForStudent(config(CONFIG_START, CONFIG_END), DUE, EXERCISE_ID, STUDENT_ID);

            assertThat(window).isEqualTo(new EffectiveWindow(DUE.plusDays(1), DUE.plusDays(1).plusHours(2), "Monday group"));
        }

        @Test
        void shouldUseTheWindowOfTheConfigurationWhenTheStudentHasNoGroupWindow() {
            when(groupWindowRepository.findGroupWindowsOfStudent(EXERCISE_ID, STUDENT_ID)).thenReturn(List.of());

            EffectiveWindow window = windowService.resolveForStudent(config(CONFIG_START, CONFIG_END), DUE, EXERCISE_ID, STUDENT_ID);

            assertThat(window).isEqualTo(new EffectiveWindow(CONFIG_START, CONFIG_END, null));
            verify(groupWindowRepository).findGroupWindowsOfStudent(EXERCISE_ID, STUDENT_ID);
        }

        @Test
        void shouldStartAtTheDueDateWithoutConfigurationAndWithoutReadingGroupWindows() {
            EffectiveWindow window = windowService.resolveForStudent(null, DUE, EXERCISE_ID, STUDENT_ID);

            assertThat(window).isEqualTo(new EffectiveWindow(DUE, null, null));
            verifyNoInteractions(groupWindowRepository);
        }
    }

    @Nested
    class LatestWindowEnd {

        @Test
        void shouldReturnTheLatestEndOfTheConfigurationAndTheGroupWindows() {
            List<MetisGroupWindow> groupWindows = List.of(groupWindow(1L, DUE, DUE.plusHours(5)), groupWindow(2L, DUE, DUE.plusHours(2)));

            assertThat(MetisWindowService.latestWindowEnd(config(CONFIG_START, CONFIG_END), groupWindows)).isEqualTo(DUE.plusHours(5));
            assertThat(MetisWindowService.latestWindowEnd(config(CONFIG_START, DUE.plusHours(9)), groupWindows)).isEqualTo(DUE.plusHours(9));
        }

        @Test
        void shouldReturnTheEndOfTheConfigurationWithoutGroupWindows() {
            assertThat(MetisWindowService.latestWindowEnd(config(CONFIG_START, CONFIG_END), List.of())).isEqualTo(CONFIG_END);
        }

        @Test
        void shouldReturnTheLatestGroupEndWithoutConfiguration() {
            assertThat(MetisWindowService.latestWindowEnd(null, List.of(groupWindow(1L, DUE, DUE.plusHours(5))))).isEqualTo(DUE.plusHours(5));
        }

        @Test
        void shouldReturnNullWhenNoWindowHasAnEnd() {
            assertThat(MetisWindowService.latestWindowEnd(config(CONFIG_START, null), List.of())).isNull();
            assertThat(MetisWindowService.latestWindowEnd(null, List.of())).isNull();
        }
    }

    @Nested
    class TutorialGroupTitlesByStudent {

        private static final long COURSE_ID = 3L;

        @Test
        void shouldNameTheGroupOfTheEarliestWindowAmongTheGroupsOfAStudent() {
            var memberships = List.of(membership(STUDENT_ID, 1L, "A without window"), membership(STUDENT_ID, 2L, "Tuesday"), membership(STUDENT_ID, 3L, "Monday"));
            when(groupWindowRepository.findGroupMembershipsByCourseId(COURSE_ID)).thenReturn(memberships);
            MetisGroupWindow tuesday = groupWindow(2L, DUE.plusDays(2), DUE.plusDays(2).plusHours(2));
            MetisGroupWindow monday = groupWindow(3L, DUE.plusDays(1), DUE.plusDays(1).plusHours(2));

            assertThat(windowService.tutorialGroupTitlesByStudent(COURSE_ID, List.of(tuesday, monday))).containsExactlyEntriesOf(Map.of(STUDENT_ID, "Monday"));
        }

        @Test
        void shouldNameTheAlphabeticallyFirstGroupWhenNoGroupOfTheStudentHasAWindow() {
            long otherStudentId = STUDENT_ID + 1;
            var memberships = List.of(membership(STUDENT_ID, 1L, "zeta"), membership(STUDENT_ID, 2L, "Alpha"), membership(otherStudentId, 4L, "Windowed"));
            when(groupWindowRepository.findGroupMembershipsByCourseId(COURSE_ID)).thenReturn(memberships);
            List<MetisGroupWindow> windows = List.of(groupWindow(4L, DUE, DUE.plusHours(1)));

            assertThat(windowService.tutorialGroupTitlesByStudent(COURSE_ID, windows)).containsOnly(Map.entry(STUDENT_ID, "Alpha"), Map.entry(otherStudentId, "Windowed"));
        }

        @Test
        void shouldLeaveOutStudentsWithoutGroup() {
            when(groupWindowRepository.findGroupMembershipsByCourseId(COURSE_ID)).thenReturn(List.of());

            assertThat(windowService.tutorialGroupTitlesByStudent(COURSE_ID, List.of(groupWindow(4L, DUE, DUE.plusHours(1))))).isEmpty();
        }

        @Test
        void shouldReadNoMembershipsForAnExerciseWithoutCourse() {
            assertThat(windowService.tutorialGroupTitlesByStudent(null, List.of())).isEmpty();
            verifyNoInteractions(groupWindowRepository);
        }
    }

    private static MetisExerciseConfig config(@Nullable ZonedDateTime windowStart, @Nullable ZonedDateTime windowEnd) {
        MetisExerciseConfig config = new MetisExerciseConfig();
        config.setExerciseId(EXERCISE_ID);
        config.setEnabled(true);
        config.setWindowStart(windowStart);
        config.setWindowEnd(windowEnd);
        return config;
    }

    private static MetisGroupMembershipDTO membership(long studentId, long tutorialGroupId, String title) {
        return new MetisGroupMembershipDTO(studentId, tutorialGroupId, title);
    }

    private static MetisGroupWindow groupWindow(long tutorialGroupId, ZonedDateTime start, ZonedDateTime end) {
        MetisGroupWindow window = new MetisGroupWindow();
        window.setExerciseId(EXERCISE_ID);
        window.setTutorialGroupId(tutorialGroupId);
        window.setWindowStart(start);
        window.setWindowEnd(end);
        return window;
    }
}
