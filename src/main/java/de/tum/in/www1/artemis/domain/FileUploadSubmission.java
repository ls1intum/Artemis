package de.tum.in.www1.artemis.domain;

import java.nio.file.Path;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonInclude;

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
            fileService.schedulePathForDeletion(Path.of(filePath), 0);
        }
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
