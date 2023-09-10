package de.tum.in.www1.artemis.domain;

import java.nio.file.Path;
import java.util.*;

import javax.persistence.*;

import org.apache.commons.lang3.math.NumberUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    // used to distinguish the type when used in collections (e.g. SearchResultPageDTO --> resultsOnPage)
    public String getSubmissionExerciseType() {
        return "file-upload";
    }

    @Transient
    private transient FileService fileService = new FileService();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "file_submission_paths", joinColumns = @JoinColumn(name = "submission_id"))
    @Column(name = "path")
    private List<String> filePaths;

    /**
     * Deletes solution files for this submission
     */
    @PostRemove
    public void onDelete() {
        final List<String> filePaths = getFilePaths(); // this.filePaths might still be null. getFilePaths() lazily initializes to an empty list
        for (String filePath : filePaths)
            onDeleteSingleFile(filePath);
    }

    /**
     * Deletes a single solution file from this submission
     *
     * @param filePath the path to the file
     */
    public void onDeleteSingleFile(String filePath) {
        // delete old file if necessary
        final var splittedPath = filePath.split("/");
        final var shouldBeExerciseId = splittedPath.length >= 5 ? splittedPath[4] : null;
        if (!NumberUtils.isCreatable(shouldBeExerciseId)) {
            throw new FilePathParsingException("Unexpected String in upload file path. Should contain the exercise ID: " + shouldBeExerciseId);
        }
        final var exerciseId = Long.parseLong(shouldBeExerciseId);
        fileService.manageFilesForUpdatedFilePath(filePath, null, FileUploadSubmission.buildFilePath(exerciseId, getId()), getId(), true);
    }

    /**
     * Returns the filePaths and lazily initializes to an empty list if necessary.
     *
     * @return file paths for file upload submission.
     */
    public List<String> getFilePaths() {
        if (filePaths == null)
            filePaths = new ArrayList<>();
        return filePaths;
    }

    @JsonIgnore
    public String getFilePath() {
        return isEmpty() ? null : filePaths.get(0);
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

    public void setFilePath(String filePath) {
        this.filePaths = new ArrayList<>();
        filePaths.add(filePath);
    }

    public void setFilePaths(List<String> filePaths) {
        this.filePaths = filePaths;
    }

    @Override
    public boolean isEmpty() {
        return getFilePaths().isEmpty();
    }

    @Override
    public String toString() {
        return "FileUploadSubmission{" + "id=" + getId() + ", filePaths='" + getFilePaths() + "'" + "}";
    }
}
