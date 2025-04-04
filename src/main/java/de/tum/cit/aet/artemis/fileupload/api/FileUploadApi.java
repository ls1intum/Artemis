package de.tum.cit.aet.artemis.fileupload.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.fileupload.config.FileUploadEnabled;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadSubmissionRepository;

/**
 * General-purpose API for file upload exercises (but not for general upload functionality).
 */
@Conditional(FileUploadEnabled.class)
@Controller
public class FileUploadApi extends AbstractFileModuleApi {

    private final FileUploadSubmissionRepository fileUploadSubmissionRepository;

    public FileUploadApi(FileUploadSubmissionRepository fileUploadSubmissionRepository) {
        this.fileUploadSubmissionRepository = fileUploadSubmissionRepository;
    }

    public FileUploadSubmission findWithTeamStudentsAndParticipationAndExerciseByIdAndExerciseIdElseThrow(long submissionId, long exerciseId) {
        return fileUploadSubmissionRepository.findWithTeamStudentsAndParticipationAndExerciseByIdAndExerciseIdElseThrow(submissionId, exerciseId);
    }
}
