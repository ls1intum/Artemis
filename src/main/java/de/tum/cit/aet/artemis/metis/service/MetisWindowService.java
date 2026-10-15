package de.tum.cit.aet.artemis.metis.service;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.metis.config.MetisEnabled;
import de.tum.cit.aet.artemis.metis.domain.MetisExerciseConfig;
import de.tum.cit.aet.artemis.metis.domain.MetisGroupWindow;
import de.tum.cit.aet.artemis.metis.dto.MetisGroupMembershipDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisStudentGroupWindowDTO;
import de.tum.cit.aet.artemis.metis.repository.MetisGroupWindowRepository;

/**
 * The window in which a student may answer the comprehension check of an exercise.
 * <p>
 * A student registered in a tutorial group that has a window for the exercise gets that window; of several such groups the earliest window counts. Every
 * other student gets the window of the exercise's configuration, which starts at the due date when it has no start of its own and has no end when it has no
 * end. Either window opens no earlier than the student's effective due date, the individual due date if there is one: questions do not exist before it, so an
 * extension moves the start of the student's window. An extension that ends after the end of the window leaves an empty window, in which no question is ever
 * shown.
 * <p>
 * The tutorial groups are read with Metis' own statements, so the window does not depend on the tutorial group module being active.
 */
@Conditional(MetisEnabled.class)
@Lazy
@Service
public class MetisWindowService {

    /** The order of group titles: alphabetical without regard to case. */
    private static final Comparator<String> BY_TITLE = String.CASE_INSENSITIVE_ORDER;

    /**
     * Orders the windows of a student's tutorial groups: the earliest start first, then the earliest end. Equal windows are ordered by the title and the id of
     * their group, so that the student's view and the dashboard pick the same group.
     */
    private static final Comparator<MetisStudentGroupWindowDTO> EARLIEST_STUDENT_WINDOW = Comparator
            .comparing((MetisStudentGroupWindowDTO window) -> window.windowStart().toInstant()).thenComparing(window -> window.windowEnd().toInstant())
            .thenComparing(MetisStudentGroupWindowDTO::title, BY_TITLE).thenComparingLong(MetisStudentGroupWindowDTO::tutorialGroupId);

    private final MetisGroupWindowRepository groupWindowRepository;

    public MetisWindowService(MetisGroupWindowRepository groupWindowRepository) {
        this.groupWindowRepository = groupWindowRepository;
    }

    /**
     * The window of one student, with the title of the tutorial group whose window it is.
     *
     * @param start              when the student may start, or null without a due date and without a start of the configuration (no questions exist then)
     * @param end                when the window closes, or null for a window without end
     * @param tutorialGroupTitle the title of the tutorial group whose window applies, or null for the window of the configuration
     */
    public record EffectiveWindow(@Nullable ZonedDateTime start, @Nullable ZonedDateTime end, @Nullable String tutorialGroupTitle) {

        /**
         * Whether the window has started.
         *
         * @param now the current time
         * @return true from the start of the window on; false for a window without start
         */
        public boolean hasOpened(ZonedDateTime now) {
            return start != null && !now.isBefore(start);
        }

        /**
         * Whether the window has closed.
         *
         * @param now the current time
         * @return true from the end of the window on; never for a window without end
         */
        public boolean hasEnded(ZonedDateTime now) {
            return end != null && !now.isBefore(end);
        }

        /**
         * Whether the window is open right now.
         *
         * @param now the current time
         * @return true between the start, inclusive, and the end, exclusive
         */
        public boolean isOpen(ZonedDateTime now) {
            return hasOpened(now) && !hasEnded(now);
        }

        /**
         * Whether a question shown at the start of the window, or now if the window has started, could still run for the given time before the window
         * ends. An ended window and an empty one (an extension past its end) have no time left.
         *
         * @param now        the current time
         * @param minSeconds the time one question needs at least
         * @return true if a question shown at {@code max(now, start)} would end no later than the end of the window, or if the window has no end
         */
        public boolean hasTimeLeft(ZonedDateTime now, int minSeconds) {
            if (end == null) {
                return true;
            }
            ZonedDateTime from = start != null && start.isAfter(now) ? start : now;
            return !from.plusSeconds(minSeconds).isAfter(end);
        }

        /**
         * Whether a new question, the first one, the next main question or a follow-up, may be shown now. Once less time than one question needs is left
         * before the end of the window, none is shown: it would be cut short and rated on what the window allowed rather than on what the student understood.
         *
         * @param now        the current time
         * @param minSeconds the time one question needs at least
         * @return true if the window is open and leaves enough time for one more question
         */
        public boolean acceptsNewQuestion(ZonedDateTime now, int minSeconds) {
            return isOpen(now) && hasTimeLeft(now, minSeconds);
        }
    }

    /**
     * The window of one student for the comprehension check of an exercise: the earliest window of a tutorial group the student is registered in, else the
     * window of the configuration, opening no earlier than the student's effective due date.
     *
     * @param config           the configuration of the exercise, or null if it has none (then it has no group windows either, since they belong to it)
     * @param effectiveDueDate the individual due date of the student's participation if there is one, else the due date of the exercise; null without due date
     * @param exerciseId       the exercise
     * @param studentId        the student
     * @return the effective window of the student
     */
    public EffectiveWindow resolveForStudent(@Nullable MetisExerciseConfig config, @Nullable ZonedDateTime effectiveDueDate, long exerciseId, long studentId) {
        if (config == null) {
            return resolve(null, null, List.of(), effectiveDueDate);
        }
        List<MetisStudentGroupWindowDTO> groupWindows = groupWindowRepository.findGroupWindowsOfStudent(exerciseId, studentId);
        return resolve(config.getWindowStart(), config.getWindowEnd(), groupWindows, effectiveDueDate);
    }

    /**
     * Resolves the window of one student from the windows that could apply to them.
     *
     * @param configStart      the start of the configuration's window, or null to start at the due date
     * @param configEnd        the end of the configuration's window, or null for no end
     * @param groupWindows     the windows of the exercise for the tutorial groups the student is registered in, in any order
     * @param effectiveDueDate the student's effective due date, or null without due date
     * @return the earliest group window if there is one, else the configuration's window, in both cases starting no earlier than the effective due date
     */
    static EffectiveWindow resolve(@Nullable ZonedDateTime configStart, @Nullable ZonedDateTime configEnd, List<MetisStudentGroupWindowDTO> groupWindows,
            @Nullable ZonedDateTime effectiveDueDate) {
        Optional<MetisStudentGroupWindowDTO> groupWindow = groupWindows.stream().min(EARLIEST_STUDENT_WINDOW);
        if (groupWindow.isPresent()) {
            MetisStudentGroupWindowDTO window = groupWindow.get();
            return new EffectiveWindow(notBefore(window.windowStart(), effectiveDueDate), window.windowEnd(), window.title());
        }
        ZonedDateTime start = configStart != null ? configStart : effectiveDueDate;
        return new EffectiveWindow(notBefore(start, effectiveDueDate), configEnd, null);
    }

    /**
     * The latest end of any window of an exercise, the window of the configuration and those of the tutorial groups.
     *
     * @param config       the configuration of the exercise, or null if it has none
     * @param groupWindows the windows of the tutorial groups of the exercise
     * @return the latest end, or null if no window has an end
     */
    @Nullable
    public static ZonedDateTime latestWindowEnd(@Nullable MetisExerciseConfig config, List<MetisGroupWindow> groupWindows) {
        Stream<ZonedDateTime> configEnd = Stream.ofNullable(config).map(MetisExerciseConfig::getWindowEnd);
        Stream<ZonedDateTime> groupEnds = groupWindows.stream().map(MetisGroupWindow::getWindowEnd);
        return Stream.concat(configEnd, groupEnds).filter(Objects::nonNull).max(Comparator.comparing(ZonedDateTime::toInstant)).orElse(null);
    }

    /**
     * The tutorial group shown for each student of a course, for the dashboard and the export of an exercise: the group whose window is the student's effective
     * window (the earliest window among the student's groups, chosen like {@link #resolveForStudent}), else the alphabetically first group of the student.
     * Students without a tutorial group are not in the result.
     *
     * @param courseId     the course of the exercise, or null for an exercise without a course (then nobody has a group)
     * @param groupWindows the windows of the tutorial groups of the exercise
     * @return the title of the group of each student who is registered in a tutorial group of the course, by the student's id
     */
    public Map<Long, String> tutorialGroupTitlesByStudent(@Nullable Long courseId, List<MetisGroupWindow> groupWindows) {
        if (courseId == null) {
            return Map.of();
        }
        Map<Long, MetisGroupWindow> windowsByGroup = groupWindows.stream()
                .collect(Collectors.toMap(MetisGroupWindow::getTutorialGroupId, Function.identity(), (first, second) -> first));
        Map<Long, List<MetisGroupMembershipDTO>> membershipsByStudent = groupWindowRepository.findGroupMembershipsByCourseId(courseId).stream()
                .collect(Collectors.groupingBy(MetisGroupMembershipDTO::studentId));
        Map<Long, String> titles = new HashMap<>();
        membershipsByStudent.forEach((studentId, memberships) -> titles.put(studentId, titleOfShownGroup(memberships, windowsByGroup)));
        return titles;
    }

    /**
     * The title of the group shown for one student: the group of the earliest window among the student's groups, else the alphabetically first group.
     */
    private static String titleOfShownGroup(List<MetisGroupMembershipDTO> memberships, Map<Long, MetisGroupWindow> windowsByGroup) {
        Optional<MetisStudentGroupWindowDTO> earliestWindow = memberships.stream().filter(membership -> windowsByGroup.containsKey(membership.tutorialGroupId()))
                .map(membership -> studentWindow(membership, windowsByGroup.get(membership.tutorialGroupId()))).min(EARLIEST_STUDENT_WINDOW);
        if (earliestWindow.isPresent()) {
            return earliestWindow.get().title();
        }
        Comparator<MetisGroupMembershipDTO> alphabetically = Comparator.comparing(MetisGroupMembershipDTO::title, BY_TITLE)
                .thenComparingLong(MetisGroupMembershipDTO::tutorialGroupId);
        return memberships.stream().min(alphabetically).map(MetisGroupMembershipDTO::title).orElseThrow();
    }

    private static MetisStudentGroupWindowDTO studentWindow(MetisGroupMembershipDTO membership, MetisGroupWindow window) {
        return new MetisStudentGroupWindowDTO(membership.tutorialGroupId(), membership.title(), window.getWindowStart(), window.getWindowEnd());
    }

    /**
     * Moves a start that lies before the effective due date to the due date.
     */
    @Nullable
    private static ZonedDateTime notBefore(@Nullable ZonedDateTime start, @Nullable ZonedDateTime effectiveDueDate) {
        if (start == null) {
            return effectiveDueDate;
        }
        if (effectiveDueDate == null || !start.isBefore(effectiveDueDate)) {
            return start;
        }
        return effectiveDueDate;
    }
}
