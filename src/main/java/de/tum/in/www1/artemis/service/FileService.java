package de.tum.in.www1.artemis.service;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.icu.text.CharsetDetector;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.FileUploadSubmission;
import de.tum.in.www1.artemis.exception.FilePathParsingException;

@Service
public class FileService implements DisposableBean {

    private final Logger log = LoggerFactory.getLogger(FileService.class);

    private final Map<Path, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    @Override
    public void destroy() {
        futures.values().forEach(future -> future.cancel(true));
        futures.clear();
    }

    /**
     * Get the file for the given path as a byte[]
     *
     * @param path the path for the file to load
     * @return file contents as a byte[], or null, if the file doesn't exist
     * @throws IOException if the file can't be accessed.
     */
    @Cacheable(value = "files", unless = "#result == null")
    public byte[] getFileForPath(String path) throws IOException {
        File file = new File(path);
        if (file.exists()) {
            return Files.readAllBytes(file.toPath());
        }
        else {
            return null;
        }
    }

    @CacheEvict(value = "files", key = "#path")
    public void resetOnPath(String path) {
        log.info("Invalidate files cache for {}", path);
        // Intentionally blank
    }

    /**
     * Takes care of any changes that have to be made to the filesystem (deleting old files, moving temporary files into their proper location) and returns the public path for the
     * resulting file (as it might have been moved from newFilePath to another path)
     *
     * @param oldFilePath  the old file path (this file will be deleted if not null and different from newFilePath)
     * @param newFilePath  the new file path (this file will be moved into its proper location, if it was a temporary file)
     * @param targetFolder the folder that a temporary file should be moved to
     * @param entityId     id of the entity this file belongs to (needed to generate public path). If this is null, a placeholder will be inserted where the id would be
     * @return the resulting public path (is identical to newFilePath, if file didn't need to be moved)
     */
    public String manageFilesForUpdatedFilePath(String oldFilePath, String newFilePath, String targetFolder, Long entityId) {
        return manageFilesForUpdatedFilePath(oldFilePath, newFilePath, targetFolder, entityId, false);
    }

    /**
     * Takes care of any changes that have to be made to the filesystem (deleting old files, moving temporary files into their proper location) and returns the public path for the
     * resulting file (as it might have been moved from newFilePath to another path)
     *
     * @param oldFilePath  the old file path (this file will be deleted if not null and different from newFilePath)
     * @param newFilePath  the new file path (this file will be moved into its proper location, if it was a temporary file)
     * @param targetFolder the folder that a temporary file should be moved to
     * @param entityId     id of the entity this file belongs to (needed to generate public path). If this is null, a placeholder will be inserted where the id would be
     * @param keepFileName flag for determining if the current filename should be kept.
     * @return the resulting public path (is identical to newFilePath, if file didn't need to be moved)
     */
    public String manageFilesForUpdatedFilePath(String oldFilePath, String newFilePath, String targetFolder, Long entityId, Boolean keepFileName) {
        if (oldFilePath != null) {
            if (oldFilePath.equals(newFilePath)) {
                // Do nothing
                return newFilePath;
            }
            else {
                // delete old file
                log.debug("Delete old file {}", oldFilePath);
                try {
                    File oldFile = new File(actualPathForPublicPath(oldFilePath));

                    if (!FileSystemUtils.deleteRecursively(oldFile)) {
                        log.warn("FileService.manageFilesForUpdatedFilePath: Could not delete old file: {}", oldFile);
                    }
                    else {
                        log.debug("Deleted Orphaned File: {}", oldFile);
                    }
                }
                catch (Exception ex) {
                    log.warn("FileService.manageFilesForUpdatedFilePath: Could not delete old file '{}' due to exception {}", oldFilePath, ex.getMessage());
                }
            }
        }
        // check if newFilePath is a temp file
        if (newFilePath != null && newFilePath.contains("files/temp")) {
            // rename and move file
            try {
                Path source = Paths.get(actualPathForPublicPath(newFilePath));
                File targetFile = generateTargetFile(newFilePath, targetFolder, keepFileName);
                Path target = targetFile.toPath();
                Files.move(source, target, REPLACE_EXISTING);
                newFilePath = publicPathForActualPath(target.toString(), entityId);
                log.debug("Moved File from {} to {}", source, target);
            }
            catch (IOException e) {
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
    public String actualPathForPublicPath(String publicPath) {
        // first extract the filename from the url
        String filename = publicPath.substring(publicPath.lastIndexOf("/") + 1);

        // check for known path to convert
        if (publicPath.contains("files/temp")) {
            return Paths.get(FilePathService.getTempFilePath(), filename).toString();
        }
        if (publicPath.contains("files/drag-and-drop/backgrounds")) {
            return Paths.get(FilePathService.getDragAndDropBackgroundFilePath(), filename).toString();
        }
        if (publicPath.contains("files/drag-and-drop/drag-items")) {
            return Paths.get(FilePathService.getDragItemFilePath(), filename).toString();
        }
        if (publicPath.contains("files/course/icons")) {
            return Paths.get(FilePathService.getCourseIconFilePath(), filename).toString();
        }
        if (publicPath.contains("files/attachments/lecture")) {
            String lectureId = publicPath.replace(filename, "").replace("/api/files/attachments/lecture/", "");
            return Paths.get(FilePathService.getLectureAttachmentFilePath(), lectureId, filename).toString();
        }
        if (publicPath.contains("files/attachments/attachment-unit")) {
            String attachmentUnitId = publicPath.replace(filename, "").replace("/api/files/attachments/attachment-unit/", "");
            return Paths.get(FilePathService.getAttachmentUnitFilePath(), attachmentUnitId, filename).toString();
        }
        if (publicPath.contains("files/file-upload-exercises")) {
            final var uploadSubPath = publicPath.replace(filename, "").replace("/api/files/file-upload-exercises/", "").split("/");
            final var shouldBeExerciseId = uploadSubPath[0];
            final var shouldBeSubmissionId = uploadSubPath.length >= 3 ? uploadSubPath[2] : null;
            if (!NumberUtils.isCreatable(shouldBeExerciseId) || !NumberUtils.isCreatable(shouldBeSubmissionId)) {
                throw new FilePathParsingException("Public path does not contain correct exerciseId or submissionId: " + publicPath);
            }
            final var exerciseId = Long.parseLong(shouldBeExerciseId);
            final var submissionId = Long.parseLong(shouldBeSubmissionId);
            return Paths.get(FileUploadSubmission.buildFilePath(exerciseId, submissionId), filename).toString();
        }

        // path is unknown => cannot convert
        throw new FilePathParsingException("Unknown Filepath: " + publicPath);
    }

    /**
     * Generate the public path for the file at the given path
     *
     * @param actualPath the path to the file in the local filesystem
     * @param entityId   the id of the entity associated with the file (may be null)
     * @return the public file url that can be used by users to access the file from outside
     */
    public String publicPathForActualPath(String actualPath, Long entityId) {
        // first extract filename
        String filename = Paths.get(actualPath).getFileName().toString();

        // generate part for id
        String id = entityId == null ? Constants.FILEPATH_ID_PLACEHOLDER : entityId.toString();

        // check for known path to convert
        if (actualPath.contains(FilePathService.getTempFilePath())) {
            return "/api/files/temp/" + filename;
        }
        if (actualPath.contains(FilePathService.getDragAndDropBackgroundFilePath())) {
            return "/api/files/drag-and-drop/backgrounds/" + id + "/" + filename;
        }
        if (actualPath.contains(FilePathService.getDragItemFilePath())) {
            return "/api/files/drag-and-drop/drag-items/" + id + "/" + filename;
        }
        if (actualPath.contains(FilePathService.getCourseIconFilePath())) {
            return "/api/files/course/icons/" + id + "/" + filename;
        }
        if (actualPath.contains(FilePathService.getLectureAttachmentFilePath())) {
            return "/api/files/attachments/lecture/" + id + "/" + filename;
        }
        if (actualPath.contains(FilePathService.getAttachmentUnitFilePath())) {
            return "/api/files/attachments/attachment-unit/" + id + "/" + filename;
        }
        if (actualPath.contains(FilePathService.getFileUploadExercisesFilePath())) {
            final var path = Paths.get(actualPath);
            final long exerciseId;
            try {
                // The last name is the file name, the one one before that is the submissionId and the one before that is the exerciseId, in which we are interested
                final var shouldBeExerciseId = path.getName(path.getNameCount() - 3).toString();
                exerciseId = Long.parseLong(shouldBeExerciseId);
            }
            catch (IllegalArgumentException e) {
                throw new FilePathParsingException("Unexpected String in upload file path. Exercise ID should be present here: " + actualPath);
            }
            return "/api/files/file-upload-exercises/" + exerciseId + "/submissions/" + id + "/" + filename;
        }

        // path is unknown => cannot convert
        throw new FilePathParsingException("Unknown Filepath: " + actualPath);
    }

    /**
     * Creates a new file at the given location with a proper filename consisting of type, timestamp and a random part
     *
     * @param originalFilename the original filename of the file (needed to determine the file type)
     * @param targetFolder     the folder where the new file should be created
     * @return the newly created file
     * @throws IOException if the file can't be generated.
     */
    private File generateTargetFile(String originalFilename, String targetFolder, Boolean keepFileName) throws IOException {
        // determine the base for the filename
        String filenameBase = "Unspecified_";
        if (targetFolder.equals(FilePathService.getDragAndDropBackgroundFilePath())) {
            filenameBase = "DragAndDropBackground_";
        }
        if (targetFolder.equals(FilePathService.getDragItemFilePath())) {
            filenameBase = "DragItem_";
        }
        if (targetFolder.equals(FilePathService.getCourseIconFilePath())) {
            filenameBase = "CourseIcon_";
        }
        if (targetFolder.contains(FilePathService.getLectureAttachmentFilePath())) {
            filenameBase = "LectureAttachment_";
        }
        if (targetFolder.contains(FilePathService.getAttachmentUnitFilePath())) {
            filenameBase = "AttachmentUnit_";
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
            if (keepFileName) {
                if (originalFilename.contains("/api/files/temp/")) {
                    originalFilename = originalFilename.replace("/api/files/temp/", "");
                }
                filename = originalFilename;
            }
            else {
                filename = filenameBase + ZonedDateTime.now().toString().substring(0, 23).replaceAll("[:.]", "-") + "_" + UUID.randomUUID().toString().substring(0, 8) + "."
                        + fileExtension;
            }
            var path = Paths.get(targetFolder, filename).toString();

            newFile = new File(path);
            if (keepFileName && newFile.exists()) {
                newFile.delete();
            }
            fileCreated = newFile.createNewFile();
        }
        while (!fileCreated);

        return newFile;
    }

    /**
     * This copies the directory at the old directory path to the new path, including all files and sub folders
     *
     * @param resources           the resources that should be copied
     * @param prefix              cut everything until the end of the prefix (e.g. exercise-abc -> abc when prefix = exercise)
     * @param targetDirectoryPath the path of the folder where the copy should be located
     * @param keepParentFolder    if true also creates the resources with the folder they are currently in (e.g. current/parent/* -> new/parent/*)
     * @throws IOException if the copying operation fails.
     */
    public void copyResources(Resource[] resources, String prefix, String targetDirectoryPath, Boolean keepParentFolder) throws IOException {

        for (Resource resource : resources) {

            // Replace windows separator with "/"
            String fileUrl = java.net.URLDecoder.decode(resource.getURL().toString(), StandardCharsets.UTF_8).replaceAll("\\\\", "/");
            // cut the prefix (e.g. 'exercise', 'solution', 'test') from the actual path
            int index = fileUrl.indexOf(prefix);
            String targetFilePath = keepParentFolder ? fileUrl.substring(index + prefix.length()) : "/" + resource.getFilename();
            // special case for '.git.ignore.file' file which would not be included in build otherwise
            if (targetFilePath.endsWith("git.ignore.file")) {
                targetFilePath = targetFilePath.replaceAll("git.ignore.file", ".gitignore");
            }
            // special case for '.gitattributes' file which would not be included in build otherwise
            if (targetFilePath.endsWith("git.attributes.file")) {
                targetFilePath = targetFilePath.replaceAll("git.attributes.file", ".gitattributes");
            }
            // special case for 'Makefile' files which would not be included in the build otherwise
            if (targetFilePath.endsWith("Makefile.file")) {
                targetFilePath = targetFilePath.replace("Makefile.file", "Makefile");
            }
            // special case for '.project' files which would not be included in the build otherwise
            if (targetFilePath.endsWith("project.file")) {
                targetFilePath = targetFilePath.replace("project.file", ".project");
            }
            // special case for '.classpath' files which would not be included in the build otherwise
            if (targetFilePath.endsWith("classpath.file")) {
                targetFilePath = targetFilePath.replace("classpath.file", ".classpath");
            }
            // special case for 'dune' files which would not be included in the build otherwise
            if (targetFilePath.endsWith("dune.file")) {
                targetFilePath = targetFilePath.replace("dune.file", "dune");
            }
            // special case for 'Fastfile' files which would not be included in the build otherwise
            if (targetFilePath.endsWith("Fast.file")) {
                targetFilePath = targetFilePath.replace("Fast.file", "Fastfile");
            }
            // special case for 'Appfile' files which would not be included in the build otherwise
            if (targetFilePath.endsWith("App.file")) {
                targetFilePath = targetFilePath.replace("App.file", "Appfile");
            }
            // special case for 'Scanfile' files which would not be included in the build otherwise
            if (targetFilePath.endsWith("Scan.file")) {
                targetFilePath = targetFilePath.replace("Scan.file", "Scanfile");
            }
            // special case for Xcode where directories get falsely scanned as files
            if (targetFilePath.endsWith(".xcassets/") || targetFilePath.endsWith(".colorset/") || targetFilePath.endsWith(".appiconset/")
                    || targetFilePath.endsWith(".xcworkspace/") || targetFilePath.endsWith(".xcodeproj/")) {
                continue;
            }

            Path copyPath = Paths.get(targetDirectoryPath + targetFilePath);
            File parentFolder = copyPath.toFile().getParentFile();
            if (!parentFolder.exists()) {
                Files.createDirectories(parentFolder.toPath());
            }

            Files.copy(resource.getInputStream(), copyPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * This renames the directory at the old directory path to the new path
     *
     * @param oldDirectoryPath    the path of the folder that should be renamed
     * @param targetDirectoryPath the path of the folder where the renamed folder should be located
     * @throws IOException if the directory could not be renamed.
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
     * Look for sections that start and end with a section marker (e.g. %section-start% and %section-end%). Overrides the given file in filePath with a new file!
     *
     * @param filePath of file to look for replaceable sections in.
     * @param sections of structure String (section name) / Boolean (keep content in section or remove it).
     */
    public void replacePlaceholderSections(String filePath, Map<String, Boolean> sections) {
        Map<Pattern, Boolean> patternBooleanMap = sections.entrySet().stream().collect(Collectors.toMap(e -> Pattern.compile(".*%" + e.getKey() + ".*%.*"), Map.Entry::getValue));
        File file = new File(filePath);
        File tempFile = new File(filePath + "_temp");
        FileReader fr;
        FileWriter fw;
        BufferedReader reader;
        BufferedWriter writer;

        try {
            fr = new FileReader(file);
            fw = new FileWriter(tempFile);
        }
        catch (IOException ex) {
            throw new FilePathParsingException("File " + filePath + " should be updated but does not exist.");
        }

        reader = new BufferedReader(fr);
        writer = new BufferedWriter(fw);

        try {

            Map.Entry<Pattern, Boolean> matchingStartPattern = null;
            String line = reader.readLine();
            while (line != null) {
                // If there is no starting pattern matched atm, check if the current line is a start pattern.
                if (matchingStartPattern == null) {
                    for (Map.Entry<Pattern, Boolean> entry : patternBooleanMap.entrySet()) {
                        if (entry.getKey().matcher(line).matches()) {
                            matchingStartPattern = entry;
                            break;
                        }
                    }
                    // If a pattern is matched, don't write anything so that the section qualifier is removed.
                    if (matchingStartPattern != null) {
                        line = reader.readLine();
                        continue;
                    }
                }
                else {
                    // If there is a starting pattern matched, check if an ending pattern is encountered.
                    boolean endMatcherFound = false;
                    for (Map.Entry<Pattern, Boolean> entry : patternBooleanMap.entrySet()) {
                        if (entry.getKey().matcher(line).matches()) {
                            endMatcherFound = true;
                            break;
                        }
                    }
                    if (endMatcherFound) {
                        matchingStartPattern = null;
                        line = reader.readLine();
                        continue;
                    }
                }

                if (matchingStartPattern == null || matchingStartPattern.getValue()) {
                    writer.write(line);
                    writer.newLine();
                }

                line = reader.readLine();
            }
            // Accessing already opened files will cause an exception on Windows machines, therefore close the streams
            reader.close();
            writer.close();
            Files.delete(file.toPath());
            FileUtils.moveFile(tempFile, new File(filePath));
        }
        catch (IOException ex) {
            throw new RuntimeException("Error encountered when reading File " + filePath + ".");
        }
        finally {
            try {
                reader.close();
                writer.close();
            }
            catch (IOException ex) {
                log.info("Error encountered when closing file readers / writers for {}", file);
            }
        }
    }

    /**
     * This replace all occurrences of the target String with the replacement String in the given directory (recursive!)
     *
     * @param startPath         the path where the file is located
     * @param targetString      the string that should be replaced
     * @param replacementString the string that should be used to replace the target
     * @throws IOException if an issue occurs on file access for the replacement of the variables.
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
        final var subDirectories = directory.list((current, name) -> new File(current, name).isDirectory());

        if (subDirectories != null) {
            for (String subDirectory : subDirectories) {
                replaceVariablesInDirectoryName(Paths.get(directory.getAbsolutePath(), subDirectory).toString(), targetString, replacementString);
            }
        }
    }

    /**
     * This replace all occurrences of the target String with the replacement String within a source file of a given directory (recursive!)
     *
     * @param startPath         the path where the file is located
     * @param targetString      the string that should be replaced
     * @param replacementString the string that should be used to replace the target
     * @throws IOException if an issue occurs on file access for the replacement of the variables.
     */
    public void replaceVariablesInFileName(String startPath, String targetString, String replacementString) throws IOException {
        log.debug("Replacing {} with {} in directory {}", targetString, replacementString, startPath);
        File directory = new File(startPath);
        if (!directory.exists() || !directory.isDirectory()) {
            throw new RuntimeException("Files in the directory " + startPath + " should be replaced but it does not exist.");
        }

        // rename all files in the file tree
        Files.find(Paths.get(startPath), Integer.MAX_VALUE, (filePath, fileAttr) -> fileAttr.isRegularFile() && filePath.toString().contains(targetString)).forEach(filePath -> {
            try {
                Files.move(new File(filePath.toString()).toPath(), new File(filePath.toString().replace(targetString, replacementString)).toPath());
            }
            catch (IOException e) {
                throw new RuntimeException("File " + filePath + " should be replaced but does not exist.");
            }
        });
    }

    /**
     * This replaces all occurrences of the target Strings with the replacement Strings in the given file and saves the file
     * <p>
     * {@link #replaceVariablesInFile(String, Map) replaceVariablesInFile}
     *
     * @param startPath    the path where the start directory is located
     * @param replacements the replacements that should be applied
     * @throws IOException if an issue occurs on file access for the replacement of the variables.
     */
    public void replaceVariablesInFileRecursive(String startPath, Map<String, String> replacements) throws IOException {
        log.debug("Replacing {} in files in directory {}", replacements, startPath);
        File directory = new File(startPath);
        if (!directory.exists() || !directory.isDirectory()) {
            throw new RuntimeException("Files in directory " + startPath + " should be replaced but the directory does not exist.");
        }

        // Get all files in directory
        String[] files = directory.list((current, name) -> new File(current, name).isFile());
        if (files != null) {
            for (String file : files) {
                replaceVariablesInFile(Paths.get(directory.getAbsolutePath(), file).toString(), replacements);
            }
        }

        // Recursive call: get all subdirectories
        String[] subDirectories = directory.list((current, name) -> new File(current, name).isDirectory());
        if (subDirectories != null) {
            for (String subDirectory : subDirectories) {
                if (subDirectory.equalsIgnoreCase(".git")) {
                    // ignore files in the '.git' folder
                    continue;
                }
                replaceVariablesInFileRecursive(Paths.get(directory.getAbsolutePath(), subDirectory).toString(), replacements);
            }
        }
    }

    /**
     * This replaces all occurrences of the target Strings with the replacement Strings in the given file and saves the file. It assumes that the size of the lists is equal and the
     * order of the argument is the same
     *
     * @param filePath     the path where the file is located
     * @param replacements the replacements that should be applied
     * @throws IOException if an issue occurs on file access for the replacement of the variables.
     */
    public void replaceVariablesInFile(String filePath, Map<String, String> replacements) throws IOException {
        log.debug("Replacing {} in file {}", replacements, filePath);
        // https://stackoverflow.com/questions/3935791/find-and-replace-words-lines-in-a-file
        Path replaceFilePath = Paths.get(filePath);
        Charset charset = StandardCharsets.UTF_8;

        String fileContent = Files.readString(replaceFilePath, charset);
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            fileContent = fileContent.replace(replacement.getKey(), replacement.getValue());
        }
        Files.writeString(replaceFilePath, fileContent, charset);
    }

    /**
     * This normalizes all line endings to UNIX-line-endings recursively from the startPath.
     * <p>
     * {@link #normalizeLineEndings(String) normalizeLineEndings}
     *
     * @param startPath the path where the start directory is located
     * @throws IOException if an issue occurs on file access for the normalizing of the line endings.
     */
    public void normalizeLineEndingsDirectory(String startPath) throws IOException {
        log.debug("Normalizing file endings in directory {}", startPath);
        File directory = new File(startPath);
        if (!directory.exists() || !directory.isDirectory()) {
            throw new RuntimeException("File endings in directory " + startPath + " should be normalized but the directory does not exist.");
        }

        // Ignore the .git repository
        IOFileFilter directoryFileFilter = FileFilterUtils.notFileFilter(FileFilterUtils.nameFileFilter(".git"));
        // Get all files in directory
        Collection<File> files = FileUtils.listFiles(directory, FileFilterUtils.trueFileFilter(), directoryFileFilter);

        for (File file : files) {
            normalizeLineEndings(file.getAbsolutePath());
        }
    }

    /**
     * This normalizes all line endings to UNIX-line-endings in a specific file.
     * '\r\n' gets replaced to '\n'
     * '\r' gets replaced to '\n'
     *
     * @param filePath the path where the file is located
     * @throws IOException if an issue occurs on file access for the normalizing of the line endings.
     */
    public void normalizeLineEndings(String filePath) throws IOException {
        log.debug("Normalizing line endings in file {}", filePath);
        // https://stackoverflow.com/questions/3776923/how-can-i-normalize-the-eol-character-in-java
        Path replaceFilePath = Paths.get(filePath);
        Charset charset = StandardCharsets.UTF_8;

        String fileContent = Files.readString(replaceFilePath, charset);
        fileContent = fileContent.replaceAll("\\r\\n?", "\n");
        Files.writeString(replaceFilePath, fileContent, charset);
    }

    /**
     * This converts all files to the UTF-8 encoding recursively from the startPath.
     * <p>
     * {@link #convertToUTF8(String) convertToUTF8}
     *
     * @param startPath the path where the start directory is located
     * @throws IOException if an issue occurs on file access when converting to UTF-8.
     */
    public void convertToUTF8Directory(String startPath) throws IOException {
        log.debug("Converting files in directory {} to UTF-8", startPath);
        File directory = new File(startPath);
        if (!directory.exists() || !directory.isDirectory()) {
            throw new RuntimeException("Files in directory " + startPath + " should be converted to UTF-8 but the directory does not exist.");
        }

        // Ignore the .git repository
        IOFileFilter directoryFileFilter = FileFilterUtils.notFileFilter(FileFilterUtils.nameFileFilter(".git"));
        // Get all files in directory
        Collection<File> files = FileUtils.listFiles(directory, FileFilterUtils.trueFileFilter(), directoryFileFilter);

        for (File file : files) {
            convertToUTF8(file.getAbsolutePath());
        }
    }

    /**
     * This converts a specific file to the UTF-8 encoding.
     * To determine the encoding of the file, the library com.ibm.icu.text is used.
     *
     * @param filePath the path where the file is located
     * @throws IOException if an issue occurs on file access when converting to UTF-8.
     */
    public void convertToUTF8(String filePath) throws IOException {
        log.debug("Converting file {} to UTF-8", filePath);
        Path replaceFilePath = Paths.get(filePath);
        byte[] contentArray = Files.readAllBytes(replaceFilePath);

        Charset charset = detectCharset(contentArray);
        log.debug("Detected charset for file {} is {}", filePath, charset.name());

        String fileContent = new String(contentArray, charset);

        Files.writeString(replaceFilePath, fileContent, StandardCharsets.UTF_8);
    }

    /**
     * Detect the charset of a byte array
     *
     * @param contentArray The content that should be checked
     * @return The detected charset
     */
    public Charset detectCharset(byte[] contentArray) {
        CharsetDetector charsetDetector = new CharsetDetector();
        charsetDetector.setText(contentArray);
        String charsetName = charsetDetector.detect().getName();
        return Charset.forName(charsetName);
    }

    /**
     * Schedule the deletion of the given path with a given delay
     *
     * @param path           The path that should be deleted
     * @param delayInMinutes The delay in minutes after which the path should be deleted
     */
    public void scheduleForDeletion(Path path, long delayInMinutes) {
        ScheduledFuture<?> future = executor.schedule(() -> {
            try {
                if (Files.exists(path)) {
                    log.info("Delete file {}", path);
                    Files.delete(path);
                }
                futures.remove(path);
            }
            catch (IOException e) {
                log.error("Deleting the file " + path + " did not work", e);
            }
        }, delayInMinutes, TimeUnit.MINUTES);

        futures.put(path, future);
    }

    /**
     * Schedule the recursive deletion of the given directory with a given delay.
     *
     * @param path           The path to the directory that should be deleted
     * @param delayInMinutes The delay in minutes after which the path should be deleted
     */
    public void scheduleForDirectoryDeletion(Path path, long delayInMinutes) {
        ScheduledFuture<?> future = executor.schedule(() -> {
            try {
                if (Files.exists(path) && Files.isDirectory(path)) {
                    log.debug("Delete directory {}", path);
                    FileUtils.deleteDirectory(path.toFile());
                }
                futures.remove(path);
            }
            catch (IOException e) {
                log.error("Deleting the directory " + path + " did not work", e);
            }
        }, delayInMinutes, TimeUnit.MINUTES);

        futures.put(path, future);
    }

    /**
     * create a unique path by appending a folder named with the current milliseconds (e.g. 1609579674868) of the system
     * Note: the method also tries to create the mentioned folder
     *
     * @param path the original path, e.g. /opt/artemis/repos-download
     * @return the unique path as string, e.g. /opt/artemis/repos-download/1609579674868
     */
    public String getUniquePathString(String path) {
        return getUniquePath(path).toString();
    }

    /**
     * create a unique path by appending a folder named with the current milliseconds (e.g. 1609579674868) of the system
     * Note: the method also tries to create the mentioned folder
     *
     * @param path the original path, e.g. /opt/artemis/repos-download
     * @return the unique path, e.g. /opt/artemis/repos-download/1609579674868
     */
    public Path getUniquePath(String path) {
        var uniquePath = Paths.get(path, String.valueOf(System.currentTimeMillis()));
        if (!Files.exists(uniquePath) && Files.isDirectory(uniquePath)) {
            try {
                Files.createDirectories(uniquePath);
            }
            catch (IOException e) {
                log.warn("could not create the directories for the path {}", uniquePath);
            }
        }
        return uniquePath;
    }

    /**
     * Removes illegal characters for filenames from the string.
     *
     * See: https://stackoverflow.com/questions/15075890/replacing-illegal-character-in-filename/15075907#15075907
     * @param string the string with the characters
     * @return stripped string
     */
    public String removeIllegalCharacters(String string) {
        return string.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
    }

    /**
     * create a directory at a given path
     *
     * @param path the original path, e.g. /opt/artemis/repos-download
     */
    public void createDirectory(Path path) {
        try {
            Files.createDirectories(path);
        }
        catch (IOException e) {
            var error = "Failed to create temporary directory at path " + path + " : " + e.getMessage();
            log.info(error);
        }
    }

    /**
     * Write a given string into a file at a given path
     *
     * @param stringToWrite     The string that will be written into a file
     * @param path              The path where the file will be written to
     * @return Path to the written file
     */
    public Path writeStringToFile(String stringToWrite, Path path) {
        try {
            var fos = new FileOutputStream(path.toString());
            var outStream = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            outStream.write(stringToWrite);
            outStream.close();
        }
        catch (IOException e) {
            log.warn("Could not write given string in file {}.", path);
        }
        return path;
    }

    /**
     * Serialize an object and write into file at a given path
     *
     * @param object        The object that is serialized and written into a file
     * @param objectMapper  The objectMapper that is used for serialization
     * @param path          The path where the file will be written to
     * @return Path to the written file
     */
    public Path writeObjectToJsonFile(Object object, ObjectMapper objectMapper, Path path) {
        try {
            objectMapper.writeValue(new File(path.toString()), object);
        }
        catch (IOException e) {
            log.warn("Could not write given object in file {}", path);
        }
        return path;
    }

    /**
     * Merge the PDF files located in the given paths.
     *
     * @param paths list of paths to merge
     * @return byte array of the merged file
     */
    public Optional<byte[]> mergePdfFiles(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return Optional.empty();
        }
        PDFMergerUtility pdfMerger = new PDFMergerUtility();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            for (String path : paths) {
                File file = new File(path);
                if (file.exists()) {
                    pdfMerger.addSource(new File(path));
                }
            }
            pdfMerger.setDestinationStream(outputStream);
            pdfMerger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly());
        }
        catch (IOException e) {
            log.warn("Could not merge files");
            return Optional.empty();
        }

        return Optional.of(outputStream.toByteArray());
    }

    /**
     * Deletes all specified files.
     * @param filePaths A list of all paths to the files that should be deleted
     */
    public void deleteFiles(List<Path> filePaths) {
        for (Path filePath : filePaths) {
            try {
                Files.delete(filePath);
            }
            catch (Exception ex) {
                log.warn("Could not delete file {}. Error message: {}", filePath, ex.getMessage());
            }
        }
    }
}
