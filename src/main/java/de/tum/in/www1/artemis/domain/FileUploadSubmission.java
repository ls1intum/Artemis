package de.tum.in.www1.artemis.domain;

import java.nio.file.Path;
import java.util.List;

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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "file_upload_paths", joinColumns = @JoinColumn(name = "id"))
    @Column(name = "path")
    private List<String> filePaths;

    /**
     * Deletes solution file for this submission.
     */
    public void onDeleteAt(int i) {
        // delete old file if necessary
        String filePath = filePaths.get(i);
        final var splittedPath = filePath.split("/");
        final var shouldBeExerciseId = splittedPath.length >= 5 ? splittedPath[4] : null;
        if (!NumberUtils.isCreatable(shouldBeExerciseId)) {
            throw new FilePathParsingException("Unexpected String in upload file path. Should contain the exercise ID: " + shouldBeExerciseId);
        }
        final var exerciseId = Long.parseLong(shouldBeExerciseId);
        fileService.manageFilesForUpdatedFilePath(filePath, null, FileUploadSubmission.buildFilePath(exerciseId, getId()), getId(), true);
    }

    /**
     * Deletes solution files for this submission
     */
    @PostRemove
    public void onDelete() {
        if (filePaths != null) {
            for (int i = 0; i < filePaths.size(); i++) {
                onDeleteAt(i);
            }
        }
    }

    public List<String> getFilePaths() {
        return filePaths;
    }

    /**
     * Builds file path for file upload submission.
     *
     * @param exerciseId   the id of the exercise
     * @param submissionId the id of the submission
     * @return path where submission for file upload exercise is stored
     */
    public static String buildFilePath(Long exerciseId, Long submissionId) {
        return Path.of(FilePathService.getFileUploadExercisesFilePath(), String.valueOf(exerciseId), String.valueOf(submissionId)).toString();
    }

    public void setFilePaths(String[] filePaths) {
        if (filePaths == null || filePaths.length == 0)
            this.filePaths = null;
        else
            this.filePaths = List.of(filePaths);
    }

    @Override
    public boolean isEmpty() {
        return filePaths == null;
    }

    @Override
    public String toString() {
        return "FileUploadSubmission{" + "id=" + getId() + ", filePaths='" + getFilePaths() + "'" + "}";
    }
}
