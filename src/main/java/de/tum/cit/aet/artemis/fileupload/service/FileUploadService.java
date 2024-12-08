package de.tum.cit.aet.artemis.fileupload.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUpload;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadEntityType;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadRepository;

@Service
@Profile(PROFILE_CORE)
public class FileUploadService {

    private static final Logger log = LoggerFactory.getLogger(FileUploadService.class);

    private final FileUploadRepository fileUploadRepository;

    private final FileService fileService;

    public FileUploadService(FileUploadRepository fileUploadRepository, FileService fileService) {
        this.fileUploadRepository = fileUploadRepository;
        this.fileService = fileService;
    }

    public void createFileUpload(String path, String serverFilePath, String fileName, Long entityId, FileUploadEntityType entityType) {
        var fileUpload = new FileUpload(path, serverFilePath, fileName, entityId, entityType);

        fileUploadRepository.save(fileUpload);
    }

    public Optional<FileUpload> findByPath(String path) {
        return Optional.ofNullable(fileUploadRepository.findFileUploadByPath(path));
    }

    public void deleteFileUploads(List<FileUpload> fileUploads) {
        for (var fileUpload : fileUploads) {
            try {
                var path = Path.of(fileUpload.getServerFilePath());
                fileService.schedulePathForDeletion(path, 1);
            }
            catch (InvalidPathException e) {
                log.error("Deleting the file {} did not work because it does not exist", fileUpload.getPath());
            }
        }

        fileUploadRepository.deleteAll(fileUploads);
    }
}
