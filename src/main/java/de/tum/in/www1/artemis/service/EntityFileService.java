package de.tum.in.www1.artemis.service;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for handling file operations for entities.
 */
@Service
public class EntityFileService {

    private final Logger log = LoggerFactory.getLogger(EntityFileService.class);

    private final FileService fileService;

    private final FilePathService filePathService;

    public EntityFileService(FileService fileService, FilePathService filePathService) {
        this.fileService = fileService;
        this.filePathService = filePathService;
    }

    /**
     * Moves a temporary file to the target folder and returns the new path. A placeholder is used as id.
     * Use {@link #moveFileBeforeEntityPersistenceWithIdIfIsTemp(String, Path, boolean, Long)} to provide an existing id.
     *
     * @param entityFilePath the path of the temporary file
     * @param targetFolder   the target folder to move the file to
     * @param keepFilename   whether to keep the filename or generate a new one
     * @return the new file path as string
     */
    @Nonnull
    public String moveTempFileBeforeEntityPersistence(@Nonnull String entityFilePath, @Nonnull Path targetFolder, boolean keepFilename) {
        return moveFileBeforeEntityPersistenceWithIdIfIsTemp(entityFilePath, targetFolder, keepFilename, null);
    }

    /**
     * Moves a temporary file to the target folder and returns the new path. If the file is not a temporary file, the original path is returned without any changes.
     *
     * @param entityFilePath the path of the temporary file
     * @param targetFolder   the target folder to move the file to
     * @param keepFilename   whether to keep the filename or generate a new one
     * @param entityId       the id of the entity that is being persisted, if null, a placeholder gets used
     * @return the new file path as string
     */
    @Nonnull
    public String moveFileBeforeEntityPersistenceWithIdIfIsTemp(@Nonnull String entityFilePath, @Nonnull Path targetFolder, boolean keepFilename, @Nullable Long entityId) {
        URI filePath = URI.create(entityFilePath);
        String filename = Path.of(entityFilePath).getFileName().toString();
        String extension = FilenameUtils.getExtension(filename);
        try {
            Path source = filePathService.actualPathForPublicPathOrThrow(filePath);
            if (!source.startsWith(FilePathService.getTempFilePath())) {
                return entityFilePath;
            }
            Path target;
            if (keepFilename) {
                target = targetFolder.resolve(filename);
            }
            else {
                target = fileService.generateFilePath(fileService.generateTargetFilenameBase(targetFolder), extension, targetFolder);
            }
            FileUtils.moveFile(source.toFile(), target.toFile(), REPLACE_EXISTING);
            URI newPath = filePathService.publicPathForActualPathOrThrow(target, entityId);
            log.debug("Moved File from {} to {}", source, target);
            return newPath.toString();
        }
        catch (IOException e) {
            log.error("Error moving file: {}", filePath, e);
            // fallback return original path
            return filePath.toString();
        }
    }

    /**
     * Handles a potential file update before entity persistence. It thus does nothing if the optional file doesn't change and otherwise moves a temporary file to the target and/or
     * deletes the old file.
     *
     * @param entityId          the id of the entity that is being persisted
     * @param oldEntityFilePath the old file path of the file that is being updated
     * @param newEntityFilePath the new file path of the file that is being updated
     * @param targetFolder      the target folder to move the file to
     * @param keepFilename      whether to keep the filename or generate a new one
     * @return the new file path as string, null if no file exists
     */
    @Nullable
    public String handlePotentialFileUpdateBeforeEntityPersistence(@Nonnull Long entityId, @Nullable String oldEntityFilePath, @Nullable String newEntityFilePath,
            @Nonnull Path targetFolder, boolean keepFilename) {
        String resultingPath = newEntityFilePath;
        if (newEntityFilePath != null) {
            resultingPath = moveFileBeforeEntityPersistenceWithIdIfIsTemp(newEntityFilePath, targetFolder, keepFilename, entityId);
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
