package de.tum.cit.aet.artemis.globalsearch.dto;

import org.jspecify.annotations.Nullable;

/**
 * One pre-fetched, pre-authorized {@code SearchableEntities} row, handed to the Iris module for the
 * global-search answer path. The access filtering has already been applied by
 * {@code SearchableEntityAccessFilterService}; consumers must not widen visibility. Dates are ISO
 * strings exactly as stored in Weaviate.
 *
 * @param entityType          the type discriminator (exercise, lecture, lecture_unit, exam, faq, channel, course, post, answer_post)
 * @param entityId            the Artemis id of the entity
 * @param courseId            the id of the course the entity belongs to
 * @param courseName          the resolved course title, when known
 * @param title               the display title of the entity
 * @param description         the body text of the entity, when present
 * @param shortName           the short name (courses and exercises)
 * @param link                the Artemis-relative deep link to the entity, when one can be built
 * @param releaseDate         ISO release date (exercises, lecture units)
 * @param startDate           ISO start date (exercises, lectures)
 * @param dueDate             ISO due date (exercises)
 * @param endDate             ISO end date (lectures, courses)
 * @param visibleDate         ISO visible-from date (exams)
 * @param examStartDate       ISO exam start date
 * @param examEndDate         ISO exam end date
 * @param maxPoints           the maximum achievable points (exercises)
 * @param quizDurationSeconds the quiz working time in seconds (quiz exercises)
 * @param programmingLanguage the programming language (programming exercises)
 * @param exerciseType        the exercise type (programming, quiz, modeling, text, file-upload)
 * @param unitType            the lecture unit type
 * @param faqState            the FAQ state (ACCEPTED, REJECTED, PROPOSED)
 * @param channelIsPublic     whether the channel is public (channels)
 */
public record SearchableEntityCandidateDTO(String entityType, @Nullable Long entityId, @Nullable Long courseId, @Nullable String courseName, @Nullable String title,
        @Nullable String description, @Nullable String shortName, @Nullable String link, @Nullable String releaseDate, @Nullable String startDate, @Nullable String dueDate,
        @Nullable String endDate, @Nullable String visibleDate, @Nullable String examStartDate, @Nullable String examEndDate, @Nullable Double maxPoints,
        @Nullable Long quizDurationSeconds, @Nullable String programmingLanguage, @Nullable String exerciseType, @Nullable String unitType, @Nullable String faqState,
        @Nullable Boolean channelIsPublic) {
}
