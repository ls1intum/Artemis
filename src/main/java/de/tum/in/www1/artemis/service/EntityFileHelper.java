package de.tum.in.www1.artemis.service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for handling file operations for entities.
 */
@Deprecated(forRemoval = true)
public class EntityFileHelper {

    private static final Logger log = LoggerFactory.getLogger(EntityFileHelper.class);

    private final FileService fileService;

    public EntityFileHelper(FileService fileService) {
        this.fileService = fileService;
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
    @NotNull
    @Deprecated(forRemoval = true)
    public String moveTempFileBeforeEntityPersistence(@NotNull String entityFilePath, @NotNull Path targetFolder, boolean keepFilename) {
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
    @NotNull
    @Deprecated(forRemoval = true)
    public String moveFileBeforeEntityPersistenceWithIdIfIsTemp(@NotNull String entityFilePath, @NotNull Path targetFolder, boolean keepFilename, @Nullable Long entityId) {
        URI filePath = URI.create(entityFilePath);
        String filename = Path.of(entityFilePath).getFileName().toString();
        try {
            Path source = FilePathService.actualPathForPublicPathOrThrow(filePath);
            if (!source.startsWith(FilePathService.getTempFilePath())) {
                return entityFilePath;
            }
            Path target;
            String generatedFilename = fileService.generateFilename(fileService.generateTargetFilenameBase(targetFolder), filename, keepFilename);
            target = targetFolder.resolve(generatedFilename);
            // remove target file before copying, because moveFile() ignores CopyOptions
            if (target.toFile().exists()) {
                FileUtils.delete(target.toFile());
            }
            FileUtils.moveFile(source.toFile(), target.toFile());
            URI newPath = FilePathService.publicPathForActualPathOrThrow(target, entityId);
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
    @Deprecated(forRemoval = true)
    public String handlePotentialFileUpdateBeforeEntityPersistence(@NotNull Long entityId, @Nullable String oldEntityFilePath, @Nullable String newEntityFilePath,
            @NotNull Path targetFolder, boolean keepFilename) {
        String resultingPath = newEntityFilePath;
        if (newEntityFilePath != null) {
            resultingPath = moveFileBeforeEntityPersistenceWithIdIfIsTemp(newEntityFilePath, targetFolder, keepFilename, entityId);
        }
        if (oldEntityFilePath != null && !oldEntityFilePath.equals(resultingPath)) {
            Path oldFilePath = FilePathService.actualPathForPublicPathOrThrow(URI.create(oldEntityFilePath));
            if (oldFilePath.toFile().exists()) {
                fileService.schedulePathForDeletion(oldFilePath, 0);
            }
        }
        return resultingPath;
    }
}
