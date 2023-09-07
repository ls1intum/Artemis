package de.tum.in.www1.artemis.domain;

import java.nio.file.Path;
import java.util.*;

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

    // used to distinguish the type when used in collections (e.g. SearchResultPageDTO --> resultsOnPage)
    public String getSubmissionExerciseType() {
        return "file-upload";
    }

    @Transient
    private transient FileService fileService = new FileService();

    @ElementCollection(targetClass = String.class, fetch = FetchType.EAGER) // nocheckin: try lazy. see https://www.baeldung.com/java-jpa-persist-string-list
    @CollectionTable(name = "file_submission_paths", joinColumns = @JoinColumn(name = "submission_id"))
    @Column(name = "path")
    private List<String> filePaths;

    /**
     * Deletes solution file for this submission
     */
    @PostRemove
    public void onDelete() {
        // nocheckin
        final List<String> filePaths = getFilePaths(); // this.filePaths might still be null. getFilePaths() lazily initializes to an empty list
        for (String filePath : filePaths)
            onDeleteSingleFile(filePath);
    }

    // nocheckin
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

    @com.fasterxml.jackson.annotation.JsonIgnore // nocheckin: temporary until we remove the function
    public String getFilePath() {
        return isEmpty() ? null : filePaths.get(0); // nocheckin
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

    // nocheckin: Maybe we should remove this function in the end.
    public void setFilePath(String filePath) {
        this.filePaths = new ArrayList<>();
        filePaths.add(filePath);
    }

    // nocheckin
    public void setFilePathsList(String[] filePaths) {
        this.filePaths = Arrays.stream(filePaths).toList();
    }

    @Override
    public boolean isEmpty() {
        return getFilePaths().isEmpty(); // nocheckin: This should only work with filePaths set to eager, not lazy... gonna have to figure out how lazy works after all
    }

    @Override
    public String toString() {
        return "FileUploadSubmission{" + "id=" + getId() + ", filePaths='" + getFilePaths() + "'" + "}";
    }
}
