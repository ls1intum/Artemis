package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.config.Constants;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@Service
public class FileService {

    private final Logger log = LoggerFactory.getLogger(FileService.class);

    /**
     * Get the file for the given path as a byte[]
     *
     * @param path the path for the file to load
     * @return file contents as a byte[], or null, if the file doesn't exist
     * @throws IOException
     */
    @Cacheable(value="files", unless="#result == null")
    public byte[] getFileForPath(String path) throws IOException {
        File file = new File(path);
        if (file.exists()) {
            return Files.readAllBytes(file.toPath());
        } else {
            return null;
        }
    }

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
    public String manageFilesForUpdatedFilePath(String oldFilePath, String newFilePath, String targetFolder, Long entityId) {
        log.debug("Manage files for {} to {}", oldFilePath, newFilePath);

        if (oldFilePath != null) {
            if (oldFilePath.equals(newFilePath)) {
                // Do nothing
                return newFilePath;
            } else {
                // delete old file
                File oldFile = new File(actualPathForPublicPath(oldFilePath));
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
     * Convert the given public file url to its corresponding local path
     *
     * @param publicPath the public file url to convert
     * @return the actual path to that file in the local filesystem
     */
    private String actualPathForPublicPath(String publicPath) {
        // first extract the filename from the url
        String filename = publicPath.substring(publicPath.lastIndexOf("/") + 1);

        // check for known path to convert
        if (publicPath.contains("files/temp")) {
            return Constants.TEMP_FILEPATH + filename;
        }
        if (publicPath.contains("files/drag-and-drop/backgrounds")) {
            return Constants.DRAG_AND_DROP_BACKGROUND_FILEPATH + filename;
        }
        if (publicPath.contains("files/drag-and-drop/drag-items")) {
            return Constants.DRAG_ITEM_FILEPATH + filename;
        }

        // path is unknown => cannot convert
        throw new RuntimeException("Unknown Filepath: " + publicPath);
    }

    /**
     * Generate the public path for the file at the given path
     *
     * @param actualPath the path to the file in the local filesystem
     * @param entityId   the id of the entity associated with the file (may be null)
     * @return the public file url that can be used by users to access the file from outside
     */
    private String publicPathForActualPath(String actualPath, Long entityId) {
        // first extract filename
        String filename = Paths.get(actualPath).getFileName().toString();

        // generate part for id
        String id = entityId == null ? Constants.FILEPATH_ID_PLACHEOLDER : entityId.toString();

        // check for known path to convert
        if (actualPath.contains(Constants.TEMP_FILEPATH)) {
            return "/api/files/temp/" + filename;
        }
        if (actualPath.contains(Constants.DRAG_AND_DROP_BACKGROUND_FILEPATH)) {
            return "/api/files/drag-and-drop/backgrounds/" + id + "/" + filename;
        }
        if (actualPath.contains(Constants.DRAG_ITEM_FILEPATH)) {
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
    private File generateTargetFile(String originalFilename, String targetFolder) throws IOException {
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
        File folder = new File(targetFolder);
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

            newFile = new File(path);
            fileCreated = newFile.createNewFile();
        } while (!fileCreated);

        return newFile;
    }

    /**
     * This copies the directory at the old directory path to the new path, including all files and subfolders
     *
     * @param resources the resources that should be copied
     * @param targetDirectoryPath the path of the folder where the copy should be lcoated
     * @throws IOException
     */
    public void copyResources(Resource[] resources, String prefix, String targetDirectoryPath) throws IOException {

        for (Resource resource : resources) {

            String fileUrl = java.net.URLDecoder.decode(resource.getURL().toString(), "UTF-8");
            int index = fileUrl.indexOf(prefix);
            String targetFilePath = fileUrl.substring(index + prefix.length());//.replaceAll("%7B", "{").replaceAll("%7D", "}");

            Path copyPath = Paths.get(targetDirectoryPath + targetFilePath);
            File parentFolder = copyPath.toFile().getParentFile();
            if(!parentFolder.exists()) {
                Files.createDirectories(parentFolder.toPath());
            }

            log.info("resource: " + resource.getURL().toString());
            log.info("copyPath: " + copyPath);
            Files.copy(resource.getInputStream(), copyPath);
        }
    }

    /**
     * This renames the directory at the old directory path to the new path
     *
     * @param oldDirectoryPath    the path of the folder that should be renamed
     * @param targetDirectoryPath the path of the folder where the renamed folder should be located
     * @throws IOException
     */
    public void renameDirectory(String oldDirectoryPath, String targetDirectoryPath) throws IOException {
        File oldDirectory = new File(oldDirectoryPath);
        if (!oldDirectory.exists()) {
            log.error("Directory {} should be renamed but does not exist.", oldDirectoryPath);
            throw new RuntimeException("Directory " + oldDirectoryPath + " should be renamed but does not exist.");
        }

        File targetDirectory = new File(targetDirectoryPath);

        FileUtils.moveDirectory(oldDirectory, targetDirectory);
    }

    /**
     * This replace all occurences of the target String with the replacement String in the given directory (recursive!)
     *
     * @param startPath         the path where the file is located
     * @param targetString      the string that should be replaced
     * @param replacementString the string that should be used to replace the target
     * @throws IOException
     */
    public void replaceVariablesInDirectoryName(String startPath, String targetString, String replacementString) throws IOException {
        log.debug("Replacing {} with {} in directory {}", targetString, replacementString, startPath);
        File directory = new File(startPath);
        if (!directory.exists() || !directory.isDirectory()) {
            throw new RuntimeException("Directory " + startPath + " should be replaced but does not exist.");
        }

        if (startPath.contains(targetString)) {
            log.debug("Target String found, replacing..");
            String targetPath = startPath.replace(targetString, replacementString);
            renameDirectory(startPath, targetPath);
            directory = new File(targetPath);
        }

        // Get all subdirectories
        String[] subdirectories = directory.list((current, name) -> new File(current, name).isDirectory());

        for (String subdirectory : subdirectories) {
            replaceVariablesInDirectoryName(directory.getAbsolutePath() + File.separator + subdirectory, targetString, replacementString);
        }
    }

    /**
     * This replace all occurences of the target Strings with the replacement Strings in the given file and saves the file
     *
     * @see {@link #replaceVariablesInFile(String, List, List) replaceVariablesInFile}
     * @param startPath          the path where the start directory is located
     * @param targetStrings      the strings that should be replaced
     * @param replacementStrings the strings that should be used to replace the target strings
     * @throws IOException
     */
    public void replaceVariablesInFileRecursive(String startPath, List<String> targetStrings, List<String> replacementStrings) throws IOException {
        log.debug("Replacing {} with {} in files in directory {}", targetStrings, replacementStrings, startPath);
        File directory = new File(startPath);
        if (!directory.exists() || !directory.isDirectory()) {
            throw new RuntimeException("Files in directory " + startPath + " should be replaced but the directory does not exist.");
        }

        String[] files = directory.list(new FilenameFilter() { // Get all files in directory
            @Override
            public boolean accept(File current, String name) {
                return new File(current, name).isFile();
            }
        });

        for (String file : files) {
            replaceVariablesInFile(directory.getAbsolutePath() + File.separator + file, targetStrings, replacementStrings);
        }

        // Recursive call
        String[] subdirectories = directory.list(new FilenameFilter() { // Get all subdirectories
            @Override
            public boolean accept(File current, String name) {
                return new File(current, name).isDirectory();
            }
        });

        for (String subdirectory : subdirectories) {
            replaceVariablesInFileRecursive(directory.getAbsolutePath() + File.separator + subdirectory, targetStrings, replacementStrings);
        }
    }

    /**
     * This replace all occurrences of the target Strings with the replacement Strings in the given file and saves the file. It assumes that the size of the lists is equal and the order of the argument is the same
     *
     * @param filePath           the path where the file is located
     * @param targetStrings      the strings that should be replaced
     * @param replacementStrings the strings that should be used to replace the target strings
     * @throws IOException
     */
    public void replaceVariablesInFile(String filePath, List<String> targetStrings, List<String> replacementStrings) throws IOException {
        log.debug("Replacing {} with {} in file {}", targetStrings, replacementStrings, filePath);
        // https://stackoverflow.com/questions/3935791/find-and-replace-words-lines-in-a-file
        Path replaceFilePath = Paths.get(filePath);
        Charset charset = StandardCharsets.UTF_8;

        String fileContent = new String(Files.readAllBytes(replaceFilePath), charset);
        for (int i = 0; i < targetStrings.size(); i++) {
            fileContent = fileContent.replace(targetStrings.get(i), replacementStrings.get(i));
        }
        Files.write(replaceFilePath, fileContent.getBytes(charset));
    }
}
