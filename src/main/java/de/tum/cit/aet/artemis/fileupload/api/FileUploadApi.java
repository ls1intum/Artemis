package de.tum.cit.aet.artemis.fileupload.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.fileupload.domain.FileUpload;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadEntityType;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadSubmissionRepository;
import de.tum.cit.aet.artemis.fileupload.service.FileUploadService;

/**
 * General-purpose API for file upload exercises (but not for general upload functionality).
 */
@Profile(PROFILE_CORE)
@Controller
public class FileUploadApi extends AbstractFileModuleApi {

    private final FileUploadService fileUploadService;

    private final FileUploadSubmissionRepository fileUploadSubmissionRepository;

    public FileUploadApi(FileUploadService fileUploadService, FileUploadSubmissionRepository fileUploadSubmissionRepository) {
        this.fileUploadService = fileUploadService;
        this.fileUploadSubmissionRepository = fileUploadSubmissionRepository;
    }

    public FileUploadSubmission findWithTeamStudentsAndParticipationAndExerciseByIdAndExerciseIdElseThrow(long submissionId, long exerciseId) {
        return fileUploadSubmissionRepository.findWithTeamStudentsAndParticipationAndExerciseByIdAndExerciseIdElseThrow(submissionId, exerciseId);
    }

    public void createFileUpload(String path, String serverFilePath, String fileName, Long entityId, FileUploadEntityType entityType) {
        fileUploadService.createFileUpload(path, serverFilePath, fileName, entityId, entityType);
    }

    public Optional<FileUpload> findByPath(String path) {
        return fileUploadService.findByPath(path);
    }
}
