package de.tum.cit.aet.artemis.globalsearch.service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;

/**
 * Orders global search rows so that a student finds the current version of what they look for first.
 * <p>
 * Weaviate ranks by text relevance alone, so the same exercise from five past semesters ranks as high as the one in the
 * running course. This class re-ranks the rows Weaviate returned:
 * <ul>
 * <li>With a query, each row keeps a score from its Weaviate rank, scaled down when its course has ended. Rows of a
 * running course keep their full score, so a much better match from a past course still wins.</li>
 * <li>Without a query (browsing a filter), the rows are returned in Weaviate's order, unchanged.</li>
 * <li>The same item in several semesters of a course (same type and title in courses whose titles only differ in the
 * semester) is shown once, from the best-ranked course. Lecture units are never merged: their names, such as "Slides",
 * repeat across lectures, and a row does not name its lecture.</li>
 * <li>With a query, the channel Artemis creates for an exercise, lecture or exam is placed directly behind that item.</li>
 * </ul>
 */
public final class GlobalSearchResultRanker {

    /** Smooths the rank-based score, so the first few ranks do not dominate everything after them. */
    private static final int RANK_OFFSET = 5;

    /** Score factor for a row of a course that ended within the last year. */
    static final double RECENTLY_ENDED_FACTOR = 0.75;

    /** Score factor for a row of a course that ended more than a year ago. */
    static final double LONG_ENDED_FACTOR = 0.5;

    /** Semester markers in course titles, such as "WS23/24", "SS 2024", "WiSe 2023/24" or "Summer 2024", and bare years. */
    private static final Pattern SEMESTER = Pattern.compile(
            "\\b(?:ws|ss|wise|sose|winter(?:semester)?|summer(?:semester)?|sommer(?:semester)?|wintersemester|sommersemester)\\s*\\d{2,4}(?:\\s*/\\s*\\d{2,4})?\\b|\\b(?:19|20)\\d{2}(?:\\s*/\\s*\\d{2,4})?\\b");

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");

    /** Channel names hold only lowercase ASCII letters, digits and separators; Artemis replaces every other character. */
    private static final Pattern NOT_IN_CHANNEL_NAMES = Pattern.compile("[^a-z0-9]+");

    /** Item types for which Artemis creates a channel named after the item. */
    private static final List<String> DEFAULT_CHANNEL_TYPES = List.of(SearchableEntitySchema.TypeValues.EXERCISE, SearchableEntitySchema.TypeValues.LECTURE,
            SearchableEntitySchema.TypeValues.EXAM);

    /** A shorter channel name is too unspecific to tie the channel to one item. */
    private static final int MIN_DEFAULT_CHANNEL_TITLE_LENGTH = 3;

    private GlobalSearchResultRanker() {
        // utility class
    }

    /**
     * Re-ranks the rows Weaviate returned, in Weaviate's order, and drops the older semester copies of an item.
     *
     * @param rows        the rows in Weaviate's order (relevance order when there is a query)
     * @param hasQuery    whether the search had a query; without one the rows are returned unchanged
     * @param coursesById the courses the rows belong to, as far as they are known
     * @param now         the current time
     * @return the rows in their new order, without older semester copies
     */
    public static List<Map<String, Object>> rank(List<Map<String, Object>> rows, boolean hasQuery, Map<Long, Course> coursesById, ZonedDateTime now) {
        if (!hasQuery) {
            // A browse has no relevance order to adjust, and Weaviate cannot order this collection by course or title
            // before it cuts the list, so sorting the returned subset would only pretend to an order it cannot keep.
            return rows;
        }
        List<Ranked> ranked = new ArrayList<>(rows.size());
        // Semester copies of one item usually have the same text, so Weaviate gives them the same relevance and orders
        // them at random. A run of adjacent copies therefore shares the rank of its first row, and only the course factor
        // decides between them: otherwise the copy of the running course loses to an ended one just for coming last.
        String previousTwinKey = null;
        int sharedRank = 0;
        for (int index = 0; index < rows.size(); index++) {
            Map<String, Object> row = rows.get(index);
            Course course = coursesById.get(courseId(row));
            String twinKey = twinKey(row, course);
            if (twinKey == null || !twinKey.equals(previousTwinKey)) {
                sharedRank = index;
            }
            previousTwinKey = twinKey;
            ranked.add(new Ranked(row, course, (1.0 / (RANK_OFFSET + sharedRank)) * courseFactor(course, now), index));
        }
        ranked.sort(Comparator.comparingDouble(Ranked::score).reversed().thenComparingInt(Ranked::position));

        // Sorting put the best copy first; keep only that one. It is the copy with the best score, which is what the reader
        // wants: the current semester unless an old one matched much better. Only copies in other courses are dropped: two
        // rows with the same key in one course are different items, such as two units named "Slides" in two lectures.
        Map<String, Long> courseOfKeptCopy = new HashMap<>();
        List<Map<String, Object>> result = new ArrayList<>(ranked.size());
        for (Ranked entry : ranked) {
            String twinKey = twinKey(entry.row(), entry.course());
            if (twinKey != null) {
                long courseId = entry.course().getId();
                Long keptCourseId = courseOfKeptCopy.putIfAbsent(twinKey, courseId);
                if (keptCourseId != null && keptCourseId != courseId) {
                    continue;
                }
            }
            result.add(entry.row());
        }
        return placeDefaultChannelsAfterTheirItem(result);
    }

    /**
     * Moves the channel Artemis creates for an exercise, lecture or exam (named "exercise-", "lecture-" or "exam-" and
     * the start of the item's title) directly behind that item when both are in the list. The channel name repeats the
     * title, so it matches as well as the item itself and would otherwise often come first, although the reader looks
     * for the item.
     */
    private static List<Map<String, Object>> placeDefaultChannelsAfterTheirItem(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<>(rows);
        for (Map<String, Object> channel : rows) {
            Map<String, Object> item = defaultChannelItem(channel, result);
            if (item == null || result.indexOf(channel) == result.indexOf(item) + 1) {
                continue;
            }
            result.remove(channel);
            result.add(result.indexOf(item) + 1, channel);
        }
        return result;
    }

    /**
     * The exercise, lecture or exam a default channel row belongs to, or null. A name that fits several items of the
     * course is left alone: the channel name holds only the start of the title, which cannot tell such items apart.
     */
    private static @Nullable Map<String, Object> defaultChannelItem(Map<String, Object> channel, List<Map<String, Object>> rows) {
        if (!SearchableEntitySchema.TypeValues.CHANNEL.equals(channel.get(SearchableEntitySchema.Properties.TYPE))) {
            return null;
        }
        String name = normalizeCase(titleOf(channel));
        for (String type : DEFAULT_CHANNEL_TYPES) {
            String prefix = type + "-";
            String titleStart = name.startsWith(prefix) ? compact(name.substring(prefix.length())) : "";
            if (titleStart.length() < MIN_DEFAULT_CHANNEL_TITLE_LENGTH) {
                continue;
            }
            List<Map<String, Object>> matches = rows.stream().filter(row -> type.equals(row.get(SearchableEntitySchema.Properties.TYPE))
                    && Objects.equals(courseId(row), courseId(channel)) && compact(normalizeCase(titleOf(row))).startsWith(titleStart)).toList();
            return matches.size() == 1 ? matches.getFirst() : null;
        }
        return null;
    }

    /** Keeps only the characters a channel name can hold, so a title such as "Einführung" matches its channel "einf-hrung". */
    private static String compact(String text) {
        return NOT_IN_CHANNEL_NAMES.matcher(text).replaceAll("");
    }

    /**
     * Score factor of a row's course: full for a running course or an unknown one, lower the longer ago the course ended.
     */
    static double courseFactor(@Nullable Course course, ZonedDateTime now) {
        if (isRunning(course, now)) {
            return 1.0;
        }
        return course.getEndDate().isAfter(now.minusYears(1)) ? RECENTLY_ENDED_FACTOR : LONG_ENDED_FACTOR;
    }

    private static boolean isRunning(@Nullable Course course, ZonedDateTime now) {
        return course == null || course.getEndDate() == null || !course.getEndDate().isBefore(now);
    }

    /**
     * The key under which semester copies of an item collapse, or null when the row is never merged: a row without a
     * course, a course row (each course is its own result) and a lecture unit row (see the class description).
     */
    private static @Nullable String twinKey(Map<String, Object> row, @Nullable Course course) {
        String type = row.get(SearchableEntitySchema.Properties.TYPE) instanceof String value ? value : null;
        if (course == null || type == null || SearchableEntitySchema.TypeValues.COURSE.equals(type) || SearchableEntitySchema.TypeValues.LECTURE_UNIT.equals(type)) {
            return null;
        }
        String courseTitle = normalize(SEMESTER.matcher(normalizeCase(course.getTitle())).replaceAll(" "));
        String title = normalize(normalizeCase(titleOf(row)));
        if (courseTitle.isEmpty() || title.isEmpty()) {
            return null;
        }
        return type + '\u0000' + courseTitle + '\u0000' + title;
    }

    private static String normalizeCase(@Nullable String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private static String normalize(String text) {
        return NON_ALPHANUMERIC.matcher(text).replaceAll(" ").trim();
    }

    private static String titleOf(Map<String, Object> row) {
        return row.get(SearchableEntitySchema.Properties.TITLE) instanceof String title ? title : "";
    }

    private static @Nullable Long courseId(Map<String, Object> row) {
        return row.get(SearchableEntitySchema.Properties.COURSE_ID) instanceof Number number ? number.longValue() : null;
    }

    private record Ranked(Map<String, Object> row, @Nullable Course course, double score, int position) {
    }
}
