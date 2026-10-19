package de.tum.cit.aet.artemis.exam.dto;

/**
 * Marker for the two response shapes the detail {@code GET courses/{courseId}/exams/{examId}} endpoint returns,
 * selected by the {@code withExerciseGroups} flag: the scalar {@link ExamDTO} (flag {@code false}) or the
 * {@link ExamWithExerciseGroupsDTO} (flag {@code true}).
 * <p>
 * The two shapes carry no shared discriminator on the wire — {@link ExamDTO} adds {@code channelName} while
 * {@link ExamWithExerciseGroupsDTO} adds {@code started}, {@code numberOfExamUsers} and {@code exerciseGroups} — so this
 * interface only exists to give the endpoint a single typed return and to let OpenAPI resolve both variants via a
 * {@code oneOf} schema (see the {@code getExam} {@code @ApiResponse}). It intentionally declares no members and adds no
 * Jackson type information, leaving each record's JSON unchanged.
 */
public sealed interface ExamResponseDTO permits ExamDTO, ExamWithExerciseGroupsDTO {
}
