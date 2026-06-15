package de.tum.cit.aet.artemis.fileupload.dto;

import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;

/**
 * DTO representing a file upload submission.
 *
 * @param id                     the ID of the submission
 * @param submitted              whether the submission has been submitted (true) or is a draft (false)
 * @param type                   the type of the submission (e.g. MANUAL, TIMEOUT)
 * @param exampleSubmission      whether this is an example submission used for tutor training
 * @param submissionDate         the date and time when the submission was submitted or saved
 * @param durationInMinutes      the duration in minutes spent on the submission
 * @param submissionExerciseType the type of the exercise (should be file-upload)
 * @param filePath               the path to the uploaded file on the server
 * @param participation          the participation this submission belongs to
 * @param results                the list of results/assessments for this submission
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FileUploadSubmissionDTO(Long id, Boolean submitted, SubmissionType type, Boolean exampleSubmission, ZonedDateTime submissionDate, Long durationInMinutes,
        String submissionExerciseType, String filePath, FileUploadParticipationDTO participation, List<FileUploadResultDTO> results) {

    /**
     * Factory method to map a {@link FileUploadSubmission} to a DTO after a successful submission.
     *
     * @param submission the file upload submission entity to map
     * @return the mapped DTO, or null if the input was null
     */
    public static FileUploadSubmissionDTO ofAfterSubmit(FileUploadSubmission submission) {
        return of(submission, true, false, true);
    }

    /**
     * Factory method to map a {@link FileUploadSubmission} to a DTO for listing purposes.
     *
     * @param submission the file upload submission entity to map
     * @return the mapped DTO, or null if the input was null
     */
    public static FileUploadSubmissionDTO ofForList(FileUploadSubmission submission) {
        return of(submission, true, false, true);
    }

    /**
     * Factory method to map a {@link FileUploadSubmission} to a DTO for assessment purposes.
     *
     * @param submission the file upload submission entity to map
     * @return the mapped DTO, or null if the input was null
     */
    public static FileUploadSubmissionDTO ofForAssessment(FileUploadSubmission submission) {
        return of(submission, true, true, true);
    }

    /**
     * Factory method to map a {@link FileUploadSubmission} to a DTO for the editor view.
     *
     * @param submission the file upload submission entity to map
     * @return the mapped DTO, or null if the input was null
     */
    public static FileUploadSubmissionDTO ofForEditor(FileUploadSubmission submission) {
        return of(submission, true, true, true);
    }

    /**
     * Package-private factory method to map a {@link FileUploadSubmission} nested within a top-level result.
     *
     * @param submission the file upload submission entity to map
     * @return the mapped DTO, or null if the input was null
     */
    static FileUploadSubmissionDTO ofForTopLevelResult(FileUploadSubmission submission) {
        return of(submission, true, false, false);
    }

    /**
     * Internal mapping method to convert a {@link FileUploadSubmission} to {@link FileUploadSubmissionDTO}.
     *
     * @param submission           the submission entity
     * @param includeParticipation whether to map the participation
     * @param includeExercise      whether to map the exercise details inside the participation
     * @param includeResults       whether to map the submission results
     * @return the mapped DTO, or null if the input was null
     */
    private static FileUploadSubmissionDTO of(FileUploadSubmission submission, boolean includeParticipation, boolean includeExercise, boolean includeResults) {
        if (submission == null) {
            return null;
        }

        FileUploadParticipationDTO participationDTO = null;
        Participation participation = submission.getParticipation();
        if (includeParticipation && participation != null && Hibernate.isInitialized(participation)) {
            participationDTO = FileUploadParticipationDTO.of(participation, includeExercise);
        }

        List<FileUploadResultDTO> resultDTOs = null;
        if (includeResults && submission.getResults() != null && Hibernate.isInitialized(submission.getResults())) {
            resultDTOs = submission.getResults().stream().map(result -> result == null ? null : FileUploadResultDTO.ofNested(result)).toList();
        }

        return new FileUploadSubmissionDTO(submission.getId(), submission.isSubmitted(), submission.getType(), submission.isExampleSubmission(), submission.getSubmissionDate(),
                submission.getDurationInMinutes(), submission.getSubmissionExerciseType(), submission.getFilePath(), participationDTO, resultDTOs);
    }
}
