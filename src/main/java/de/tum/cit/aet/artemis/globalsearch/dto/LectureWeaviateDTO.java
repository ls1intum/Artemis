package de.tum.cit.aet.artemis.globalsearch.dto;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;

/**
 * DTO holding lecture data needed for Weaviate synchronization.
 * This DTO is extracted before the async boundary to avoid LazyInitializationException
 * when async methods run outside the original Hibernate session.
 * All fields are primitives, Strings, or immutable types safe to pass across thread boundaries.
 */
public record LectureWeaviateDTO(Long lectureId, Long courseId, String courseTitle, String lectureTitle, String description, ZonedDateTime startDate, ZonedDateTime endDate,
        boolean isTutorialLecture) {

    /**
     * Extracts all required data from a Lecture entity and its relationships.
     * MUST be called while the Hibernate session is still active.
     *
     * @param lecture the lecture entity (must have course relationship loaded)
     * @return the extracted data safe to use in async context
     * @throws org.hibernate.LazyInitializationException if required relationships are not loaded
     */
    public static LectureWeaviateDTO fromLecture(Lecture lecture) {
        Course course = lecture.getCourse();

        return new LectureWeaviateDTO(lecture.getId(), course.getId(), course.getTitle(), lecture.getTitle(), lecture.getDescription(), lecture.getStartDate(),
                lecture.getEndDate(), lecture.isTutorialLecture());
    }
}
