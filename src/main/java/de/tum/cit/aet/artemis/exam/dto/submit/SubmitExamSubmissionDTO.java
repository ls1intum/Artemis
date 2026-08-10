package de.tum.cit.aet.artemis.exam.dto.submit;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Polymorphic request DTO for a single submission sent as part of the exam hand-in
 * ({@code POST courses/{courseId}/exams/{examId}/student-exams/submit}).
 * <p>
 * The discriminator property and subtype names deliberately match the {@code submissionExerciseType}
 * {@link com.fasterxml.jackson.annotation.JsonTypeInfo}/{@link com.fasterxml.jackson.annotation.JsonSubTypes}
 * on the {@link de.tum.cit.aet.artemis.exercise.domain.Submission} entity, so that a full-entity
 * {@code StudentExam} body posted by a stale client tab opened before the DTO rollout still binds
 * losslessly (every record additionally sets {@code @JsonIgnoreProperties(ignoreUnknown = true)}).
 * <p>
 * Only the fields the server actually persists are bound: the database id (matched against the existing
 * submission) plus the per-type content. Programming and file-upload submissions carry no content and are
 * accepted-and-ignored (the server saves those only through their dedicated submission pages), but they are
 * still modelled so a legacy body that includes them deserializes.
 */
@Schema(discriminatorProperty = "submissionExerciseType", discriminatorMapping = { @DiscriminatorMapping(value = "text", schema = TextExamSubmissionDTO.class),
        @DiscriminatorMapping(value = "modeling", schema = ModelingExamSubmissionDTO.class), @DiscriminatorMapping(value = "quiz", schema = QuizExamSubmissionDTO.class),
        @DiscriminatorMapping(value = "programming", schema = ProgrammingExamSubmissionDTO.class),
        @DiscriminatorMapping(value = "file-upload", schema = FileUploadExamSubmissionDTO.class) }, oneOf = { TextExamSubmissionDTO.class, ModelingExamSubmissionDTO.class,
                QuizExamSubmissionDTO.class, ProgrammingExamSubmissionDTO.class, FileUploadExamSubmissionDTO.class })
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "submissionExerciseType")
@JsonSubTypes({ @JsonSubTypes.Type(value = TextExamSubmissionDTO.class, name = "text"), @JsonSubTypes.Type(value = ModelingExamSubmissionDTO.class, name = "modeling"),
        @JsonSubTypes.Type(value = QuizExamSubmissionDTO.class, name = "quiz"), @JsonSubTypes.Type(value = ProgrammingExamSubmissionDTO.class, name = "programming"),
        @JsonSubTypes.Type(value = FileUploadExamSubmissionDTO.class, name = "file-upload") })
public sealed interface SubmitExamSubmissionDTO
        permits TextExamSubmissionDTO, ModelingExamSubmissionDTO, QuizExamSubmissionDTO, ProgrammingExamSubmissionDTO, FileUploadExamSubmissionDTO {

    Long id();
}
