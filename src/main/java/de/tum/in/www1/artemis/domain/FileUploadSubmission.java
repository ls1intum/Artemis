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

    @Transient
    private transient FileService fileService = new FileService();

    @Column(name = "file_path")
    private String filePath;

    /**
     * Deletes solution file for this submission
     */
    @PostRemove
    public void onDelete() {
        if (filePath != null) {
            // delete old file if necessary
            final var splittedPath = filePath.split("/");
            final var shouldBeExerciseId = splittedPath.length >= 5 ? splittedPath[4] : null;
            if (!NumberUtils.isCreatable(shouldBeExerciseId)) {
                throw new FilePathParsingException("Unexpected String in upload file path. Should contain the exercise ID: " + shouldBeExerciseId);
            }
            final var exerciseId = Long.parseLong(shouldBeExerciseId);
            fileService.manageFilesForUpdatedFilePath(filePath, null, FileUploadSubmission.buildFilePath(exerciseId, getId()), getId(), true);
        }
    }

    public String getFilePath() {
        return filePath;
    }

    /**
     * Builds file path for file upload submission.
     * @param exerciseId the id of the exercise
     * @param submissionId the id of the submission
     * @return path where submission for file upload exercise is stored
     */
    public static String buildFilePath(Long exerciseId, Long submissionId) {
        return Path.of(FilePathService.getFileUploadExercisesFilePath(), String.valueOf(exerciseId), String.valueOf(submissionId)).toString();
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
