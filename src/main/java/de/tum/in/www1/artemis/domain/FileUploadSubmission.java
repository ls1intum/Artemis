package de.tum.in.www1.artemis.domain;

import java.nio.file.Path;

import javax.persistence.*;

import org.apache.commons.lang3.math.NumberUtils;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.exception.FilePathParsingException;
import de.tum.in.www1.artemis.service.FilePathService;
import de.tum.in.www1.artemis.service.FileService;

/**
 * A FileUploadSubmission.
 */
@Entity
@DiscriminatorValue(value = "F")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class FileUploadSubmission extends Submission {

    @Override
    public String getSubmissionExerciseType() {
        return "file-upload";
    }

    @Transient
    private final transient FileService fileService = new FileService();

    @Column(name = "file_path")
    private String filePath;

    /**
     * Deletes solution file for this submission
     */
    @PostRemove
    public void onDelete() {
        if (filePath != null) {
            Path actualPath = getActualPathForPublicPath(filePath);
            fileService.schedulePathForDeletion(actualPath, 0);
        }
    }

    private Path getActualPathForPublicPath(String filePath) {
        // Note: This method duplicates functionality from actualPathForPublicPath on FilePathService
        // but to avoid this entity depending on the FilePathService service this is separate.

        final var splittedPath = filePath.split("/");
        final var shouldBeExerciseId = splittedPath.length >= 5 ? splittedPath[4] : null;
        if (!NumberUtils.isCreatable(shouldBeExerciseId)) {
            throw new FilePathParsingException("Unexpected String in upload file path. Should contain the exercise ID: " + shouldBeExerciseId);
        }
        final var exerciseId = Long.parseLong(shouldBeExerciseId);

        Path submissionDirectory = FileUploadSubmission.buildFilePath(exerciseId, getId());
        Path fileName = Path.of(filePath).getFileName();
        return submissionDirectory.resolve(fileName);
    }

    public String getFilePath() {
        return filePath;
    }

    /**
     * Builds file path for file upload submission.
     *
     * @param exerciseId   the id of the exercise
     * @param submissionId the id of the submission
     * @return path where submission for file upload exercise is stored
     */
    public static Path buildFilePath(Long exerciseId, Long submissionId) {
        return FilePathService.getFileUploadExercisesFilePath().resolve(exerciseId.toString()).resolve(submissionId.toString());
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    @Override
    public boolean isEmpty() {
        return filePath == null;
    }

    @Override
    public String toString() {
        return "FileUploadSubmission{" + "id=" + getId() + ", filePath='" + getFilePath() + "'" + "}";
    }
}
