package de.tum.cit.aet.artemis.fileupload.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentNote;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;

/**
 * DTO representing a result / assessment of a file upload submission.
 *
 * @param id             the ID of the result
 * @param completionDate the date and time when the assessment was completed
 * @param successful     whether the result is considered successful
 * @param score          the score achieved (in percent or points depending on context)
 * @param rated          whether the result is rated (counts for the final score)
 * @param assessmentType the type of assessment (e.g. MANUAL, AUTOMATIC)
 * @param hasComplaint   whether a complaint has been filed for this result
 * @param exampleResult  whether this result belongs to an example submission
 * @param assessmentNote the assessment note associated with the result
 * @param assessor       the tutor/instructor who assessed the submission
 * @param feedbacks      the feedbacks generated for this result
 * @param submission     the submission context if requested
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FileUploadResultDTO(Long id, ZonedDateTime completionDate, Boolean successful, Double score, Boolean rated, AssessmentType assessmentType, Boolean hasComplaint,
        Boolean exampleResult, FileUploadAssessmentNoteDTO assessmentNote, FileUploadUserDTO assessor, List<FileUploadFeedbackDTO> feedbacks, FileUploadSubmissionDTO submission) {

    /**
     * Factory method to map a {@link Result} entity to a nested {@link FileUploadResultDTO} (without including submission loop).
     *
     * @param result the result entity to map
     * @return the mapped DTO, or null if input was null
     */
    public static FileUploadResultDTO ofNested(Result result) {
        return of(result, false);
    }

    /**
     * Factory method to map a {@link Result} entity to a top-level {@link FileUploadResultDTO} (including the submission).
     *
     * @param result the result entity to map
     * @return the mapped DTO, or null if input was null
     */
    public static FileUploadResultDTO ofTopLevel(Result result) {
        return of(result, true);
    }

    /**
     * Maps a {@link Result} entity to {@link FileUploadResultDTO}.
     *
     * @param result            the result entity to map
     * @param includeSubmission whether to include the submission details
     * @return the mapped DTO, or null if input was null
     */
    private static FileUploadResultDTO of(Result result, boolean includeSubmission) {
        if (result == null) {
            return null;
        }

        List<FileUploadFeedbackDTO> feedbackDTOs = null;
        if (result.getFeedbacks() != null && Hibernate.isInitialized(result.getFeedbacks())) {
            feedbackDTOs = result.getFeedbacks().stream().filter(Objects::nonNull).map(FileUploadFeedbackDTO::of).toList();
        }

        User assessor = result.getAssessor();
        FileUploadUserDTO assessorDTO = assessor != null && Hibernate.isInitialized(assessor) ? FileUploadUserDTO.of(assessor) : null;
        AssessmentNote assessmentNote = result.getAssessmentNote();
        FileUploadAssessmentNoteDTO assessmentNoteDTO = FileUploadAssessmentNoteDTO.of(assessmentNote);

        FileUploadSubmissionDTO submissionDTO = null;
        Submission submission = result.getSubmission();
        if (includeSubmission && submission instanceof FileUploadSubmission fileUploadSubmission && Hibernate.isInitialized(fileUploadSubmission)) {
            submissionDTO = FileUploadSubmissionDTO.ofForTopLevelResult(fileUploadSubmission);
        }

        return new FileUploadResultDTO(result.getId(), result.getCompletionDate(), result.isSuccessful(), result.getScore(), result.isRated(), result.getAssessmentType(),
                result.hasComplaint(), result.isExampleResult(), assessmentNoteDTO, assessorDTO, feedbackDTOs, submissionDTO);
    }
}
