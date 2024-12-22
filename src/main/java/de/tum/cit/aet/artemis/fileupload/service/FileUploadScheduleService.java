package de.tum.cit.aet.artemis.fileupload.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.fileupload.domain.FileUpload;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadEntityType;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadRepository;

@Service
@Profile(PROFILE_SCHEDULING)
public class FileUploadScheduleService {

    public static final int DAYS_UNTIL_NULL_ENTITY_FILES_ARE_DELETED = 3;

    private final FileUploadRepository fileUploadRepository;

    private final FileUploadService fileUploadService;

    public FileUploadScheduleService(FileUploadRepository fileUploadRepository, FileUploadService fileUploadService) {
        this.fileUploadRepository = fileUploadRepository;
        this.fileUploadService = fileUploadService;
    }

    /**
     * Cleans up all file uploads where corresponding entity does not exist anymore
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void cleanupOrphanedFileUploads() {
        var entityTypes = FileUploadEntityType.values();

        for (var entityType : entityTypes) {
            var fileUploadsToDelete = findByEntity(entityType);
            fileUploadService.deleteFileUploads(fileUploadsToDelete);
        }
    }

    /**
     * Cleans up all file uploads where entity id is null
     */
    @Scheduled(cron = "0 5 0 * * *")
    public void cleanupNullEntityFileUploads() {
        var cutoffDate = ZonedDateTime.now().minusDays(DAYS_UNTIL_NULL_ENTITY_FILES_ARE_DELETED);
        var fileUploadsToDelete = fileUploadRepository.findNullEntityReferences(cutoffDate);
        fileUploadService.deleteFileUploads(fileUploadsToDelete);
    }

    private List<FileUpload> findByEntity(FileUploadEntityType entityType) {
        if (entityType == FileUploadEntityType.CONVERSATION) {
            return fileUploadRepository.findOrphanedConversationReferences();
        }
        throw new IllegalArgumentException("Unimplemented entity type: " + entityType);
    }
}
