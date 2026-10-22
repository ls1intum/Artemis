package de.tum.cit.aet.artemis.lecture.factories;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;

/**
 * Factory for constructing {@link Lecture} and lecture unit objects that are not backed by user input, i.e. integration test fixtures and the demo course seeded by the
 * {@code demo} profile.
 * <p>
 * This factory only <b>constructs</b> the entities, it never persists them. Persisting a lecture unit in particular has to go through the lecture, see
 * {@code TextUnitResource#createTextUnit}, because the unit order is derived from the lecture's unit list.
 */
public final class LectureFactory {

    private LectureFactory() {
        // static factory, do not instantiate
    }

    /**
     * Generates a lecture for the given course.
     *
     * @param title       The title of the lecture.
     * @param description The description of the lecture.
     * @param startDate   The start date of the lecture, may be null.
     * @param endDate     The end date of the lecture, may be null.
     * @param course      The course the lecture belongs to.
     * @return The generated lecture.
     */
    public static Lecture generateLecture(String title, @Nullable String description, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime endDate, Course course) {
        Lecture lecture = new Lecture();
        lecture.setTitle(title);
        lecture.setDescription(description);
        lecture.setStartDate(startDate);
        lecture.setEndDate(endDate);
        lecture.setCourse(course);
        return lecture;
    }

    /**
     * Generates a text unit. The unit is not attached to a lecture, the caller decides how it is added and persisted.
     *
     * @param name    The name of the text unit.
     * @param content The markdown content of the text unit.
     * @return The generated text unit.
     */
    public static TextUnit generateTextUnit(String name, String content) {
        TextUnit textUnit = new TextUnit();
        textUnit.setName(name);
        textUnit.setContent(content);
        return textUnit;
    }
}
