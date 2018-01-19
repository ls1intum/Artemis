package de.tum.in.www1.exerciseapp.domain.util;

import de.tum.in.www1.exerciseapp.config.Constants;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.UUID;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class FileManagement {

    private static final Logger log = LoggerFactory.getLogger(FileManagement.class);

    /**
     * Takes care of any changes that have to be made to the filesystem
     * (deleting old files, moving temporary files into their proper location)
     * and returns the public path for the resulting file (as it might have been moved from newFilePath to another path)
     *
     * @param oldFilePath  the old file path (this file will be deleted if not null and different from newFilePath)
     * @param newFilePath  the new file path (this file will be moved into its proper location, if it was a temporary file)
     * @param targetFolder the folder that a temporary file should be moved to
     * @param entityId     id of the entity this file belongs to (needed to generate public path). If this is null, a placeholder will be inserted where the id would be
     * @return the resulting public path (is identical to newFilePath, if file didn't need to be moved)
     */
    public static String manageFilesForUpdatedFilePath(String oldFilePath, String newFilePath, String targetFolder, Long entityId) {
        log.debug("Manage files for {} to {}", oldFilePath, newFilePath);

        if (oldFilePath != null) {
            if (oldFilePath.equals(newFilePath)) {
                // Do nothing
                return newFilePath;
            } else {
                // delete old file
                File oldFile = new File(Paths.get(actualPathForPublicPath(oldFilePath)).toString());
                if (!oldFile.delete()) {
                    log.warn("Could not delete file: {}", oldFile);
                } else {
                    log.debug("Deleted Orphaned File: {}", oldFile);
                }
            }
        }
        // check if newFilePath is a temp file
        if (newFilePath != null && newFilePath.contains("files/temp")) {
            // rename and move file
            try {
                Path source = Paths.get(actualPathForPublicPath(newFilePath));
                File targetFile = generateTargetFile(newFilePath, targetFolder);
                Path target = targetFile.toPath();
                Files.move(source, target, REPLACE_EXISTING);
                newFilePath = publicPathForActualPath(target.toString(), entityId);
                log.debug("Moved File from {} to {}", source, target);
            } catch (IOException e) {
                log.error("Error moving file: {}", newFilePath);
            }
        }
        return newFilePath;
    }

    /**
     * Convert the given public path to its corresponding local path
     *
     * @param filePath the public file path to convert
     * @return the actual path to that file in the local filesystem
     */
    private static String actualPathForPublicPath(String filePath) {
        // first extract filename
        String filename = filePath.substring(filePath.lastIndexOf("/") + 1);

        // check for known path to convert
        if (filePath.contains("files/temp")) {
            return Constants.TEMP_FILEPATH + filename;
        }
        if (filePath.contains("files/drag-and-drop/backgrounds")) {
            return Constants.DRAG_AND_DROP_BACKGROUND_FILEPATH + filename;
        }
        if (filePath.contains("files/drag-and-drop/drag-items")) {
            return Constants.DRAG_ITEM_FILEPATH + filename;
        }

        // path is unknown => cannot convert
        throw new RuntimeException("Unknown Filepath: " + filePath);
    }

    /**
     * Generate the public path for the file at the given path
     *
     * @param actualPath the path to the file in the local filesystem
     * @param entityId   the id of the entity associated with the file (may be null)
     * @return the public path that can be used by users to access the file from outside
     */
    private static String publicPathForActualPath(String actualPath, Long entityId) {
        // first extract filename
        String filename = actualPath.substring(actualPath.lastIndexOf(File.separator) + 1);

        // generate part for id
        String id = entityId == null ? Constants.FILEPATH_ID_PLACHEOLDER : entityId.toString();

        // check for known path to convert
        if (actualPath.contains(Constants.TEMP_FILEPATH.replaceAll("/", File.separator))) {
            return "/api/files/temp/" + filename;
        }
        if (actualPath.contains(Constants.DRAG_AND_DROP_BACKGROUND_FILEPATH.replaceAll("/", File.separator))) {
            return "/api/files/drag-and-drop/backgrounds/" + id + "/" + filename;
        }
        if (actualPath.contains(Constants.DRAG_ITEM_FILEPATH.replaceAll("/", File.separator))) {
            return "/api/files/drag-and-drop/drag-items/" + id + "/" + filename;
        }

        // path is unknown => cannot convert
        throw new RuntimeException("Unknown Filepath: " + actualPath);
    }

    /**
     * Creates a new file at the given location with a proper filename consisting of type, timestamp and a random part
     *
     * @param originalFilename the original filename of the file (needed to determine the file type)
     * @param targetFolder     the folder where the new file should be created
     * @return the newly created file
     * @throws IOException
     */
    private static File generateTargetFile(String originalFilename, String targetFolder) throws IOException {
        // determine the base for the filename
        String filenameBase = "Unspecified_";
        if (targetFolder.equals(Constants.DRAG_AND_DROP_BACKGROUND_FILEPATH)) {
            filenameBase = "DragAndDropBackground_";
        }
        if (targetFolder.equals(Constants.DRAG_ITEM_FILEPATH)) {
            filenameBase = "DragItem_";
        }

        // extract the file extension
        String fileExtension = FilenameUtils.getExtension(originalFilename);

        // create folder if necessary
        File folder = new File(Paths.get(targetFolder).toString());
        if (!folder.exists()) {
            if (!folder.mkdirs()) {
                log.error("Could not create directory: {}", targetFolder);
                throw new IOException("Could not create directory: " + targetFolder);
            }
        }

        // create the file (retry if filename already exists)
        boolean fileCreated;
        File newFile;
        String filename;
        do {
            filename = filenameBase + ZonedDateTime.now().toString().substring(0, 23).replaceAll(":|\\.", "-") + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + fileExtension;
            String path = targetFolder + filename;

            newFile = new File(Paths.get(path).toString());
            fileCreated = newFile.createNewFile();
        } while (!fileCreated);

        return newFile;
    }
}
