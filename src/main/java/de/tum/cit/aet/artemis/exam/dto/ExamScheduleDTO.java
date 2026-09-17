package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The three fields of an exam that decide whether a submission is in time.
 * <p>
 * Read on its own so the submission gate does not have to hydrate the exam entity and, through its eager
 * {@code @ManyToOne} course association, the course as well. The gate reads nothing else from either.
 *
 * @param startDate   when the exam starts
 * @param endDate     when the exam ends
 * @param gracePeriod the grace period in seconds, null if none is configured
 * @param testExam    whether this is a test exam, which the individual end date branches on
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamScheduleDTO(ZonedDateTime startDate, ZonedDateTime endDate, @Nullable Integer gracePeriod, boolean testExam) {
}
