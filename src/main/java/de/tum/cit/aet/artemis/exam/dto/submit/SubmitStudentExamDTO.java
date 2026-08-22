package de.tum.cit.aet.artemis.exam.dto.submit;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request body for the exam hand-in ({@code POST courses/{courseId}/exams/{examId}/student-exams/submit}).
 * <p>
 * It carries only the id of the student exam and the last-second submission changes to persist. Everything else the
 * old full-entity {@code StudentExam} body used to carry ({@code user}, {@code exam}, {@code submitted}, dates,
 * {@code workingTime}, {@code examSessions}, {@code testRun}/{@code testExam}) is deliberately dropped: the server
 * loads the authoritative student exam from the database by {@link #id()} and derives ownership, exam/course
 * validation, the submitted flag, the submission date and the test-run/test-exam gating from that DB truth rather than
 * from the client. Dropping the client-supplied {@code user} in particular removes the previous anti-manipulation
 * gate's reliance on a client claim: ownership is now checked against the persisted student exam's owner.
 * <p>
 * Every record in this package is {@code @JsonIgnoreProperties(ignoreUnknown = true)} and mirrors the discriminator
 * property names of the underlying entities, so a stale client tab that still posts the full-entity body deserializes
 * losslessly across the DTO rollout.
 *
 * @param id        the id of the student exam being submitted
 * @param exercises the exercises with their participations and submissions carrying the last-second changes
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SubmitStudentExamDTO(Long id, List<SubmitExamExerciseDTO> exercises) {
}
