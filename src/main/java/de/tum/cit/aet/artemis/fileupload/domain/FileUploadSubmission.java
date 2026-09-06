package de.tum.cit.aet.artemis.fileupload.domain;

import java.nio.file.Path;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PostRemove;
import jakarta.persistence.Transient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.util.FileSystemLocation;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;

/**
 * A FileUploadSubmission.
 */
@Entity
@DiscriminatorValue(value = "F")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class FileUploadSubmission extends Submission {

    private static final Logger log = LoggerFactory.getLogger(FileUploadSubmission.class);

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
        if (filePath == null) {
            return;
        }
        // Best-effort: a malformed or legacy stored path must not abort the surrounding deletion transaction (e.g. a
        // course/exam reset), which would otherwise leave the course permanently half-reset.
        try {
            Exercise exercise = getParticipation() != null ? getParticipation().getExercise() : null;
            if (exercise == null || exercise.getId() == null) {
                log.warn("Could not schedule the file of file-upload submission {} for deletion: its exercise is not known", getId());
                return;
            }
            Path actualPath = new FileSystemLocation.FileUploadSubmission(exercise.getId(), getId(), filePath).path();
            fileService.schedulePathForDeletion(actualPath, 0);
        }
        catch (RuntimeException e) {
            log.warn("Could not schedule the file of file-upload submission {} for deletion (stored path '{}')", getId(), filePath, e);
        }
    }

    public String getFilePath() {
        return filePath;
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
