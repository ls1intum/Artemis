package de.tum.cit.aet.artemis.fileupload.api;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.fileupload.config.FileUploadEnabled;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadSubmissionRepository;

/**
 * General-purpose API for file upload exercises (but not for general upload functionality).
 */
@Conditional(FileUploadEnabled.class)
@Controller
@Lazy
public class FileUploadApi extends AbstractFileModuleApi {

    private final FileUploadSubmissionRepository fileUploadSubmissionRepository;

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    public FileUploadApi(FileUploadSubmissionRepository fileUploadSubmissionRepository, FileUploadExerciseRepository fileUploadExerciseRepository) {
        this.fileUploadSubmissionRepository = fileUploadSubmissionRepository;
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
    }

    public FileUploadSubmission findWithTeamStudentsAndParticipationAndExerciseByIdAndExerciseIdElseThrow(long submissionId, long exerciseId) {
        return fileUploadSubmissionRepository.findWithTeamStudentsAndParticipationAndExerciseByIdAndExerciseIdElseThrow(submissionId, exerciseId);
    }

    public Optional<FileUploadExercise> findForVersioningById(long exerciseId) {
        return fileUploadExerciseRepository.findForVersioningById(exerciseId);
    }
}
