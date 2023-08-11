package de.tum.in.www1.artemis.service;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.io.FilenameUtils;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EntityFileService {

    private final Logger log = LoggerFactory.getLogger(EntityFileService.class);

    private final FileService fileService;

    private final FilePathService filePathService;

    public EntityFileService(FileService fileService, FilePathService filePathService) {
        this.fileService = fileService;
        this.filePathService = filePathService;
    }

    @Nonnull
    public String moveTempFileBeforeEntityPersistence(@Nonnull String entityFilePath, @Nonnull Path targetFolder, boolean keepFilename) {
        return moveTempFileBeforeEntityPersistenceWithId(entityFilePath, targetFolder, keepFilename, null);
    }

    @Nonnull
    public String moveTempFileBeforeEntityPersistenceWithId(@Nonnull String entityFilePath, @Nonnull Path targetFolder, boolean keepFilename, @Nullable Long entityId) {
        URI filePath = URI.create(entityFilePath);
        String filename = Path.of(entityFilePath).getFileName().toString();
        String extension = FilenameUtils.getExtension(filename);
        try {
            Path source = filePathService.actualPathForPublicPathOrThrow(filePath);
            Path target;
            if (keepFilename) {
                target = targetFolder.resolve(filename);
            }
            else {
                target = fileService.generateFilePath(fileService.generateTargetFilenameBase(targetFolder), extension, targetFolder);
            }
            Files.move(source, target, REPLACE_EXISTING);
            URI newPath = filePathService.publicPathForActualPathOrThrow(target, entityId);
            log.debug("Moved File from {} to {}", source, target);
            return newPath.toString();
        }
        catch (IOException e) {
            log.error("Error moving file: " + filePath, e);
            // fallback return original path
            return filePath.toString();
        }
    }

    @Nullable
    public String handlePotentialFileUpdateBeforeEntityPersistence(@Nonnull Long entityId, @Nullable String oldEntityFilePath, @Nullable String newEntityFilePath,
            @Nonnull Path targetFolder, boolean keepFilename) {
        String resultingPath = newEntityFilePath;
        if (newEntityFilePath != null) {
            resultingPath = moveTempFileBeforeEntityPersistenceWithId(newEntityFilePath, targetFolder, keepFilename, entityId);
        }
        if (oldEntityFilePath != null && !oldEntityFilePath.equals(newEntityFilePath)) {
            Path oldFilePath = filePathService.actualPathForPublicPathOrThrow(URI.create(oldEntityFilePath));
            if (oldFilePath.toFile().exists()) {
                fileService.schedulePathForDeletion(oldFilePath, 0);
            }
        }
        return resultingPath;
    }
}
