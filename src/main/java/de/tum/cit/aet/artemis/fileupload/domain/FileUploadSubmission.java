package de.tum.cit.aet.artemis.fileupload.domain;

import java.nio.file.Path;
import java.util.Optional;

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
import de.tum.cit.aet.artemis.core.util.PublicFileUrl;
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

    /**
     * The path the submitted file is served under, relative to {@code api/core/files/}, as
     * {@link de.tum.cit.aet.artemis.core.util.PublicFileUrl.FileUploadSubmission} renders it.
     * <p>
     * <b>The one file reference column that is not reduced to a filename, and deliberately so.</b> Every other column holds a filename and lets the owning entity supply the
     * ids its URL and its directory need. A submission cannot: its file lives under {@code file-upload-exercises/{exerciseId}/{submissionId}/}, and the exercise id is not a
     * property of a submission at all. It is reachable only by navigating {@code participation.exercise}, and the two places that need it most are exactly the two that cannot
     * count on that navigation:
     * <ul>
     * <li>{@link #onDelete()} is a {@code @PostRemove} callback and sees whatever graph the delete happened to load;</li>
     * <li>{@code FileUploadSubmissionDTO#of} already branches on the participation being absent or an uninitialised proxy, so a URL built from it would sometimes be missing.</li>
     * </ul>
     * Reducing this column would therefore trade a self-contained value for one that cannot always be read back. What has to happen first is that the exercise id becomes part
     * of the submission, or that deleting the file moves out of the entity callback into the service that knows the exercise; both are changes of their own.
     */
    @Column(name = "file_path")
    private String filePath;

    /**
     * Deletes solution file for this submission.
     * <p>
     * The location comes from the stored value, which carries both ids, and only from the participation when it does not. That order is what makes the deletion independent of
     * an association being initialised: the previous release derived the path from the stored value alone, and requiring the participation instead turned a cascade delete whose
     * graph is not loaded into a silently leaked file.
     */
    @PostRemove
    public void onDelete() {
        if (filePath == null) {
            return;
        }
        // Best-effort: a malformed or legacy stored path must not abort the surrounding deletion transaction (e.g. a
        // course/exam reset), which would otherwise leave the course permanently half-reset.
        try {
            Optional<Path> location = storedLocation().or(this::locationFromParticipation).map(FileSystemLocation::path);
            if (location.isEmpty()) {
                log.warn("Could not schedule the file of file-upload submission {} for deletion: the stored path '{}' names no location and its exercise is not known", getId(),
                        filePath);
                return;
            }
            fileService.schedulePathForDeletion(location.get(), 0);
        }
        catch (RuntimeException e) {
            log.warn("Could not schedule the file of file-upload submission {} for deletion (stored path '{}')", getId(), filePath, e);
        }
    }

    /**
     * @return the location the stored value describes on its own, or empty when it is not a whole submission path
     */
    private Optional<FileSystemLocation> storedLocation() {
        return PublicFileUrl.FileUploadSubmission.ofStoredValue(filePath).flatMap(FileSystemLocation::of);
    }

    /**
     * The fallback for a value that is not a whole submission path, which is nothing this application writes but is cheap to survive.
     *
     * @return the location built from the exercise of this submission's participation, or empty when that is not reachable
     */
    private Optional<FileSystemLocation> locationFromParticipation() {
        Exercise exercise = getParticipation() != null ? getParticipation().getExercise() : null;
        if (exercise == null || exercise.getId() == null || getId() == null) {
            return Optional.empty();
        }
        return Optional.of(new FileSystemLocation.FileUploadSubmission(exercise.getId(), getId(), filePath));
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
