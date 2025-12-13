package de.tum.cit.aet.artemis.core.util;

import static de.tum.cit.aet.artemis.core.config.BinaryFileExtensionConfiguration.isBinaryFile;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.icu.text.CharsetDetector;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.FilePathInformation;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.FilePathParsingException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;

public class FileUtil {

    private static final Logger log = LoggerFactory.getLogger(FileUtil.class);

    public static final String DEFAULT_FILE_SUBPATH = "temp/";

    public static final String BACKGROUND_FILE_SUBPATH = "drag-and-drop/backgrounds/";

    public static final String PICTURE_FILE_SUBPATH = "drag-and-drop/drag-items/";

    /**
     * The list of file extensions that are allowed to be uploaded in a Markdown editor.
     * Extensions must be lower-case without leading dots.
     * NOTE: Has to be kept in sync with the client-side definitions in file-extensions.constants.ts
     */
    private static final Set<String> allowedMarkdownFileExtensions = Set.of("png", "jpg", "jpeg", "gif", "svg", "pdf");

    /**
     * The global list of file extensions that are allowed to be uploaded.
     * Extensions must be lower-case without leading dots.
     * NOTE: Has to be kept in sync with the client-side definitions in file-extensions.constants.ts
     */
    private static final Set<String> allowedFileExtensions = Set.of("png", "jpg", "jpeg", "gif", "svg", "pdf", "zip", "tar", "txt", "rtf", "md", "htm", "html", "json", "doc",
            "docx", "csv", "xls", "xlsx", "ppt", "pptx", "pages", "pages-tef", "numbers", "key", "odt", "ods", "odp", "odg", "odc", "odi", "odf", "mp4", "webm", "ogg", "mov",
            "avi", "mkv", "flv", "wmv", "m4v");

    /**
     * The list of video file extensions that are allowed to be uploaded.
     * Extensions must be lower-case without leading dots.
     */
    private static final Set<String> allowedVideoFileExtensions = Set.of("mp4", "webm", "ogg", "mov", "avi", "mkv", "flv", "wmv", "m4v");

    private static final String MARKDOWN_FILE_SUBPATH = "markdown/";

    /**
     * These directories get falsely marked as files and should be ignored during copying.
     */
    private static final List<String> IGNORED_DIRECTORY_SUFFIXES = List.of(".xcassets", ".colorset", ".appiconset", ".xcworkspace", ".xcodeproj", ".swiftpm", ".tests", ".mvn");

    /**
     * Sanitizes the provided filename string.
     * <ul>
     * <li>Replaces all characters that are not letters (a-z, A-Z), digits (0-9), dots (.), or hyphens (-) with underscores (_).</li>
     * <li>Collapses multiple consecutive dots (.) into a single dot (.) to avoid issues with file extensions or hidden files.</li>
     * </ul>
     *
     * @param filename the original filename string to sanitize
     * @return the sanitized filename, with invalid characters replaced and multiple dots reduced
     */
    public static String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z\\d.\\-]", "_").replaceAll("\\.+", ".");
    }

    /**
     * Helper method which handles the file creation for both normal file uploads and for markdown
     *
     * @param file         The file to be uploaded with a maximum file size set in resources/config/application.yml
     * @param keepFilename specifies if original file name should be kept
     * @param markdown     boolean which is set to true, when we are uploading a file within the markdown editor
     * @return The external URI of the file
     */
    @NonNull
    public static URI handleSaveFile(MultipartFile file, boolean keepFilename, boolean markdown) {
        // check for file type
        String filename = checkAndSanitizeFilename(file.getOriginalFilename());

        validateExtension(filename, markdown);

        final String filenamePrefix = markdown ? "Markdown_" : "Temp_";
        final Path path = markdown ? FilePathConverter.getMarkdownFilePath() : FilePathConverter.getTempFilePath();

        String generatedFilename = generateFilename(filenamePrefix, filename, keepFilename);
        Path filePath = path.resolve(generatedFilename);

        copyFile(file, filePath);

        String currentFilename = filePath.getFileName().toString();
        return URI.create(markdown ? MARKDOWN_FILE_SUBPATH : DEFAULT_FILE_SUBPATH).resolve(currentFilename);
    }

    /**
     * Handles the saving of a file in a conversation.
     *
     * @param file           The file to be uploaded.
     * @param courseId       The ID of the course.
     * @param conversationId The ID of the conversation.
     * @return The URI of the saved file.
     */
    public static FilePathInformation handleSaveFileInConversation(MultipartFile file, Long courseId, Long conversationId) {
        // TODO: Improve the access check. The course is already checked, but the user might not be a member of the conversation. The course may not belong to the conversation
        String sanitizedOriginalFilename = checkAndSanitizeFilename(file.getOriginalFilename());

        validateExtension(sanitizedOriginalFilename, true);

        final Path path = FilePathConverter.getMarkdownFilePathForConversation(courseId, conversationId);

        String fileName = generateFilename("", sanitizedOriginalFilename, true);
        Path filePath = path.resolve(fileName);

        copyFile(file, filePath);

        String currentFilename = filePath.getFileName().toString();
        return new FilePathInformation(filePath, URI.create("courses/" + courseId + "/conversations/" + conversationId + "/").resolve(currentFilename), sanitizedOriginalFilename);
    }

    /**
     * Saves a file to the given path using a generated filename.
     *
     * @param file         the file to save
     * @param basePath     the base path to save the file to
     * @param filePathType the type of the file path
     * @param keepFilename whether to keep the original filename or not
     * @return the path where the file was saved
     */
    @NonNull
    public static Path saveFile(MultipartFile file, Path basePath, FilePathType filePathType, boolean keepFilename) {
        String sanitizedFilename = checkAndSanitizeFilename(file.getOriginalFilename());
        validateExtension(sanitizedFilename, false);
        String generatedFilename = generateFilename(generateTargetFilenameBase(filePathType), sanitizedFilename, keepFilename);
        Path savePath = basePath.resolve(generatedFilename);
        return saveFile(file, savePath);
    }

    /**
     * Saves a file to the given path. If the file already exists, it will be <b>overwritten</b>. Make sure the path is <b>sanitized</b> and does not override files unexpectedly!
     *
     * @param file              the file to save
     * @param fullSanitizedPath the full path to save the file to
     * @return the path where the file was saved
     */
    @NonNull
    public static Path saveFile(MultipartFile file, Path fullSanitizedPath) {
        copyFile(file, fullSanitizedPath);
        return fullSanitizedPath;
    }

    private static void copyFile(MultipartFile file, Path filePath) {
        try {
            FileUtils.copyInputStreamToFile(file.getInputStream(), filePath.toFile());
        }
        catch (IOException e) {
            log.error("Could not save file {}", filePath.getFileName(), e);
            throw new InternalServerErrorException("Could not create file");
        }
    }

    /**
     * Nullsafe sanitation method for filenames.
     *
     * @param filename the filename to sanitize
     * @return the sanitized filename
     * @throws IllegalArgumentException if the filename is null
     */
    @NonNull
    public static String checkAndSanitizeFilename(@Nullable String filename) {
        if (filename == null) {
            throw new IllegalArgumentException("Filename cannot be null");
        }

        return sanitizeFilename(filename);
    }

    /**
     * Validates the file extension of the given filename. If the markdown flag is set to true, only markdown file extensions are allowed.
     *
     * @param filename the filename to validate
     * @param markdown whether the file is a markdown file
     * @throws BadRequestAlertException if the file extension is not allowed
     */
    public static void validateExtension(String filename, boolean markdown) {
        final String fileExtension = FilenameUtils.getExtension(filename);
        final Set<String> allowedExtensions = markdown ? allowedMarkdownFileExtensions : allowedFileExtensions;

        if (allowedExtensions.stream().noneMatch(fileExtension::equalsIgnoreCase)) {
            throw new BadRequestAlertException("Unsupported file type! Allowed file types: " + String.join(", ", allowedExtensions), "file", null, true);
        }
    }

    /**
     * Checks if the given filename has a video file extension.
     *
     * @param filename the filename to check
     * @return true if the file is a video file, false otherwise
     */
    public static boolean isVideoFile(String filename) {
        final String fileExtension = FilenameUtils.getExtension(filename);
        return allowedVideoFileExtensions.stream().anyMatch(fileExtension::equalsIgnoreCase);
    }

    /**
     * Validates that the file size does not exceed the maximum allowed size.
     * Throws a ResponseStatusException with HTTP 413 (Payload Too Large) if exceeded.
     *
     * @param file        the file to validate
     * @param maxFileSize the maximum allowed file size in bytes
     * @throws ResponseStatusException if the file size exceeds the maximum allowed size
     */
    public static void validateFileSize(MultipartFile file, long maxFileSize) {
        if (file != null && file.getSize() > maxFileSize) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "The file is too large. Maximum file size is " + (maxFileSize / (1024 * 1024)) + " MB.");
        }
    }

    /**
     * Validates the file size based on whether it's a video file or not.
     * Video files are allowed up to MAX_VIDEO_FILE_SIZE, other files up to MAX_FILE_SIZE.
     *
     * @param file the file to validate
     * @throws ResponseStatusException if the file size exceeds the maximum allowed size
     */
    public static void validateFileSize(MultipartFile file) {
        if (file == null || file.getOriginalFilename() == null) {
            return;
        }
        long maxSize = isVideoFile(file.getOriginalFilename()) ? Constants.MAX_VIDEO_FILE_SIZE : Constants.MAX_FILE_SIZE;
        validateFileSize(file, maxSize);
    }

    /**
     * Validates the file size for each file in a list.
     *
     * @param files       the files to validate
     * @param maxFileSize the maximum allowed file size in bytes
     * @throws ResponseStatusException if any file size exceeds the maximum allowed size
     */
    public static void validateFileSize(List<MultipartFile> files, long maxFileSize) {
        if (files == null) {
            return;
        }
        for (MultipartFile file : files) {
            validateFileSize(file, maxFileSize);
        }
    }

    /**
     * Generates a new filename based on the current time and either the supplied filename or a random UUID.
     *
     * @param filenamePrefix    the prefix of the filename
     * @param sanitizedFilename the sanitized filename including the extension
     * @param keepFilename      whether to keep the original filename or not
     * @return the generated filename
     */
    public static String generateFilename(String filenamePrefix, String sanitizedFilename, boolean keepFilename) {
        if (keepFilename) {
            return filenamePrefix + ZonedDateTime.now().toString().substring(0, 23).replaceAll("[:.]", "-") + "_" + sanitizedFilename;
        }
        String fileExtension = FilenameUtils.getExtension(sanitizedFilename);
        return filenamePrefix + ZonedDateTime.now().toString().substring(0, 23).replaceAll("[:.]", "-") + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + fileExtension;
    }

    /**
     * Copies an existing non-temporary file to a target location.
     *
     * @param oldFilePath  the old file path
     * @param targetFolder the folder that a file should be copied to
     * @param filePathType the type of the file path
     * @return the resulting file path or null on error
     */
    public static Path copyExistingFileToTarget(@NonNull Path oldFilePath, @NonNull Path targetFolder, FilePathType filePathType) {
        if (oldFilePath != null && !pathContains(oldFilePath, Path.of(("files/temp")))) {
            String filename = oldFilePath.getFileName().toString();
            try {
                Path target = targetFolder.resolve(generateFilename(generateTargetFilenameBase(filePathType), filename, false));
                FileUtils.copyFile(oldFilePath.toFile(), target.toFile());
                log.debug("Moved File from {} to {}", oldFilePath, target);
                return target;
            }
            catch (IOException e) {
                log.error("Error moving file: {}", oldFilePath, e);
            }
        }
        return null;
    }

    /**
     * Checks whether the path starts with the provided sub-path.
     *
     * @param path    URI to check if it starts with the sub-pat
     * @param subPath sub-path URI to search for
     * @throws IllegalArgumentException if the provided path does not start with the provided sub-path or the provided legacy-sub-path
     */
    public static void sanitizeByCheckingIfPathStartsWithSubPathElseThrow(@NonNull URI path, @NonNull URI subPath) {
        // Removes redundant elements (e.g. ../ or ./) from the path and sub-path
        URI normalisedPath = path.normalize();
        URI normalisedSubPath = subPath.normalize();
        // Indicates whether the path starts with the subPath
        boolean normalisedPathStartsWithNormalisedSubPath = normalisedPath.getPath().startsWith(normalisedSubPath.getPath());
        // Throws a IllegalArgumentException in case the normalisedPath does not start with the normalisedSubPath
        if (!normalisedPathStartsWithNormalisedSubPath) {
            throw new IllegalArgumentException(String.format("Invalid path: '%s'. Normalized to: '%s'. Expected to start with: '%s' (normalized from '%s').", path, normalisedPath,
                    normalisedSubPath, subPath));
        }
    }

    /**
     * Sanitizes a file path by checking for invalid characters or path traversal.
     *
     * @param filePath the file path to sanitize
     * @throws IllegalArgumentException if the file path is invalid
     */
    public static void sanitizeFilePathByCheckingForInvalidCharactersElseThrow(String filePath) {
        URI uriToCheck = URI.create(filePath);
        URI normalizedPath = uriToCheck.normalize();
        if (!uriToCheck.equals(normalizedPath)) {
            throw new IllegalArgumentException("Path is not valid!");
        }
    }

    /**
     * Generates a prefix for the filename based on the file path type
     *
     * @param filePathType the type of the file path
     * @return the prefix ending with an underscore character as a separator
     */
    @NonNull
    public static String generateTargetFilenameBase(@NonNull FilePathType filePathType) {
        return switch (filePathType) {
            case DRAG_AND_DROP_BACKGROUND -> "DragAndDropBackground_";
            case DRAG_ITEM -> "DragItem_";
            case COURSE_ICON -> "CourseIcon_";
            case PROFILE_PICTURE -> "ProfilePicture_";
            case EXAM_USER_SIGNATURE -> "ExamUserSignature_";
            case EXAM_USER_IMAGE -> "ExamUserImage_";
            case LECTURE_ATTACHMENT -> "LectureAttachment_";
            case ATTACHMENT_UNIT -> "AttachmentUnit_";
            case SLIDE -> "AttachmentUnitSlide_";
            case STUDENT_VERSION_SLIDES -> "StudentVersionSlides_";
            default -> "Unspecified_";
        };
    }

    private static boolean pathContains(Path path, Path subPath) {
        return path.normalize().toString().contains(subPath.normalize().toString());
    }

    /**
     * Copies the given resources to the target directory.
     *
     * @param resources             The resources that should be copied.
     * @param prefix                Cut everything until the end of the prefix.
     *                                  E.g. source {@code …/templates/java/gradle/wrapper.jar}, prefix {@code templates/java/} results in
     *                                  {@code <targetDirectory>/gradle/wrapper.jar}).
     * @param targetDirectory       The directory where the copy should be located.
     * @param keepParentDirectories Create the resources with the directory they are currently in (e.g. current/parent/* -> new/parent/*)
     * @throws IOException If the copying operation fails.
     */
    public static void copyResources(final Resource[] resources, final Path prefix, final Path targetDirectory, final boolean keepParentDirectories) throws IOException {
        for (final Resource resource : resources) {
            copyResource(resource, prefix, targetDirectory, keepParentDirectories);
        }
    }

    /**
     * Copies the given resource to the target directory.
     *
     * @param resource              The resource that should be copied.
     * @param prefix                Cut everything until the end of the prefix.
     *                                  E.g. source {@code …/templates/java/gradle/wrapper.jar}, prefix {@code templates/java/} results in
     *                                  {@code <targetDirectory>/gradle/wrapper.jar}).
     * @param targetDirectory       The directory where the copy should be located.
     * @param keepParentDirectories Create the resources with the directory they are currently in (e.g. current/parent/* -> new/parent/*)
     * @throws IOException If the copying operation fails.
     */
    public static void copyResource(final Resource resource, final Path prefix, final Path targetDirectory, final boolean keepParentDirectories) throws IOException {
        final Path targetPath = generateTargetPath(resource, prefix, targetDirectory, keepParentDirectories);

        if (isIgnoredDirectory(targetPath)) {
            return;
        }

        FileUtils.copyInputStreamToFile(resource.getInputStream(), targetPath.toFile());

        if (targetPath.endsWith("gradlew")) {
            targetPath.toFile().setExecutable(true);
        }
    }

    private static Path generateTargetPath(final Resource resource, final Path prefix, final Path targetDirectory, final boolean keepParentDirectory) throws IOException {
        final Path filePath;
        if (resource.isFile()) {
            filePath = resource.getFile().toPath();
        }
        else {
            final String url = URLDecoder.decode(resource.getURL().toString(), UTF_8);
            filePath = Path.of(url);
        }

        return generateTargetPath(filePath, prefix, targetDirectory, keepParentDirectory);
    }

    /**
     * Generates the target file path which a resource should be copied to.
     * <p>
     * Searches for {@code prefix} in the {@code source} and removes all path elements including and up to the prefix.
     * The target file path is then determined by resolving the remaining path against the target directory.
     *
     * @param source              The path where the resource is copied from.
     * @param prefix              The prefix that should be trimmed from the source path.
     * @param targetDirectory     The base target directory.
     * @param keepParentDirectory Keep directories in the path between prefix and filename.
     * @return The target path where the resource should be copied to.
     */
    private static Path generateTargetPath(final Path source, final Path prefix, final Path targetDirectory, final boolean keepParentDirectory) {
        if (!keepParentDirectory) {
            return targetDirectory.resolve(source.getFileName());
        }

        final List<Path> sourcePathElements = getPathElements(source);
        final List<Path> prefixPathElements = getPathElements(prefix);

        final int prefixStartIdx = Collections.indexOfSubList(sourcePathElements, prefixPathElements);

        if (prefixStartIdx < 0) {
            return targetDirectory.resolve(source);
        }

        final int startIdx = prefixStartIdx + prefixPathElements.size();
        final Path relativeSource = source.subpath(startIdx, sourcePathElements.size());

        return targetDirectory.resolve(relativeSource);
    }

    private static List<Path> getPathElements(final Path path) {
        final List<Path> elements = new ArrayList<>();

        for (final Path value : path) {
            elements.add(value);
        }

        return elements;
    }

    /**
     * Checks if the given path has been identified as a file, but it actually points to a directory.
     *
     * @param filePath The path to a file/directory.
     * @return True, if the path is assumed to be a file but actually points to a directory.
     */
    private static boolean isIgnoredDirectory(final Path filePath) {
        final String filename = filePath.getFileName().toString();
        return IGNORED_DIRECTORY_SUFFIXES.stream().anyMatch(filename::endsWith);
    }

    /**
     * This renames the directory at the old directory path to the new path
     *
     * @param oldDirectoryPath    the path of the folder that should be renamed
     * @param targetDirectoryPath the path of the folder where the renamed folder should be located
     * @throws IOException if the directory could not be renamed.
     */
    public static void renameDirectory(Path oldDirectoryPath, Path targetDirectoryPath) throws IOException {

        if (!Files.exists(oldDirectoryPath)) {
            throw new FilePathParsingException("Directory " + oldDirectoryPath + " should be renamed but does not exist.");
        }
        File oldDirectory = oldDirectoryPath.toFile();
        File targetDirectory = targetDirectoryPath.toFile();

        FileUtils.moveDirectory(oldDirectory, targetDirectory);
    }

    /**
     * Look for sections that start and end with a section marker (e.g. %section-start% and %section-end%). Overrides the given file in filePath with a new file!
     *
     * @param filePath of file to look for replaceable sections in.
     * @param sections of structure String (section name) / Boolean (keep content in section or remove it).
     */
    public static void replacePlaceholderSections(Path filePath, Map<String, Boolean> sections) {
        Map<Pattern, Boolean> patternBooleanMap = sections.entrySet().stream().collect(Collectors.toMap(e -> Pattern.compile(".*%" + e.getKey() + ".*%.*"), Map.Entry::getValue));
        File file = filePath.toFile();
        File tempFile = Path.of(filePath + "_temp").toFile();
        if (!file.exists()) {
            throw new FilePathParsingException("File " + filePath + " should be updated but does not exist.");
        }

        try (var reader = Files.newBufferedReader(file.toPath(), UTF_8); var writer = Files.newBufferedWriter(tempFile.toPath(), UTF_8)) {
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
        }
        catch (IOException ex) {
            throw new RuntimeException("Error encountered when reading File " + filePath + ".", ex);
        }
        // Accessing already opened files will cause an exception on Windows machines, therefore close the streams
        try {
            Files.delete(file.toPath());
            FileUtils.moveFile(tempFile, filePath.toFile());
        }
        catch (IOException ex) {
            throw new RuntimeException("Error encountered when reading File " + filePath + ".", ex);
        }
    }

    /**
     * This replaces all occurrences of the target String with the replacement String in the given directory (recursive!)
     *
     * @param startPath         the path where the file is located
     * @param targetString      the string that should be replaced
     * @param replacementString the string that should be used to replace the target
     * @throws IOException if an issue occurs on file access for the replacement of the variables.
     */
    public static void replaceVariablesInDirectoryName(Path startPath, String targetString, String replacementString) throws IOException {
        log.debug("Replace {} with {} in directory with path {}", targetString, replacementString, startPath);
        File directory = startPath.toFile();
        if (!directory.exists() || !directory.isDirectory()) {
            throw new RuntimeException("Directory " + startPath + " should be replaced but does not exist.");
        }
        String pathString = startPath.toString();
        if (pathString.contains(targetString)) {
            log.debug("Target String found, replacing..");
            String targetPath = pathString.replace(targetString, replacementString);
            final var path = Path.of(targetPath);
            renameDirectory(startPath, path);
            directory = path.toFile();
        }

        // Get all subdirectories
        final var subDirectories = directory.list((current, name) -> current.toPath().resolve(name).toFile().isDirectory());

        if (subDirectories != null) {
            for (String subDirectory : subDirectories) {
                replaceVariablesInDirectoryName(directory.toPath().toAbsolutePath().resolve(subDirectory), targetString, replacementString);
            }
        }
    }

    /**
     * This replaces all occurrences of the target String with the replacement String within a source file of a given directory (recursive!)
     *
     * @param startPath         the path where the file is located
     * @param targetString      the string that should be replaced
     * @param replacementString the string that should be used to replace the target
     * @throws IOException if an issue occurs on file access for the replacement of the variables.
     */
    public static void replaceVariablesInFilename(Path startPath, String targetString, String replacementString) throws IOException {
        log.debug("Replace {} with {} in directory with path {}", targetString, replacementString, startPath);
        File directory = startPath.toFile();
        if (!directory.exists() || !directory.isDirectory()) {
            throw new FileNotFoundException("Files in the directory " + startPath + " should be replaced but it does not exist.");
        }

        // rename all files in the file tree
        try (var files = Files.find(startPath, Integer.MAX_VALUE, (filePath, fileAttr) -> fileAttr.isRegularFile() && filePath.toString().contains(targetString))) {
            files.forEach(filePath -> {
                try {
                    // We expect the strings to be clean already, so the filename shouldn't change. If it does, we are on the safe side with the sanitation.
                    String cleanFileName = sanitizeFilename(filePath.toString().replace(targetString, replacementString));
                    FileUtils.moveFile(filePath.toFile(), Path.of(cleanFileName).toFile());
                }
                catch (IOException e) {
                    throw new RuntimeException("File " + filePath + " should be replaced but does not exist.");
                }
            });
        }
    }

    /**
     * This replaces all occurrences of the target Strings with the replacement Strings in the given file and saves the file
     * <p>
     * {@link #replaceVariablesInFile(Path, Map) replaceVariablesInFile}
     *
     * @param startPath    the path where the start directory is located
     * @param replacements the replacements that should be applied
     */
    public static void replaceVariablesInFileRecursive(Path startPath, Map<String, String> replacements) {
        replaceVariablesInFileRecursive(startPath, replacements, Collections.emptyList());
    }

    /**
     * This replaces all occurrences of the target Strings with the replacement Strings in the given file and saves the file
     * <p>
     * {@link #replaceVariablesInFile(Path, Map) replaceVariablesInFile}
     *
     * @param startPath     the path where the start directory is located
     * @param replacements  the replacements that should be applied
     * @param filesToIgnore the name of files for which no replacement should be done
     */
    public static void replaceVariablesInFileRecursive(Path startPath, Map<String, String> replacements, List<String> filesToIgnore) {
        log.debug("Replace {} in files in directory {}", replacements, startPath);
        File directory = startPath.toFile();
        if (!directory.exists() || !directory.isDirectory()) {
            throw new RuntimeException("Files in directory " + startPath + " should be replaced but the directory does not exist.");
        }

        // Get all files in directory
        String[] files = directory.list((current, name) -> current.toPath().resolve(name).toFile().isFile());
        if (files != null) {
            // filter out files that should be ignored
            files = Arrays.stream(files).filter(Predicate.not(filesToIgnore::contains)).toArray(String[]::new);
            for (String file : files) {
                replaceVariablesInFile(directory.toPath().toAbsolutePath().resolve(file), replacements);
            }
        }

        // Recursive call: get all subdirectories
        String[] subDirectories = directory.list((current, name) -> current.toPath().resolve(name).toFile().isDirectory());
        if (subDirectories != null) {
            for (String subDirectory : subDirectories) {
                if (subDirectory.equalsIgnoreCase(".git")) {
                    // ignore files in the '.git' folder
                    continue;
                }
                replaceVariablesInFileRecursive(directory.toPath().toAbsolutePath().resolve(subDirectory), replacements, filesToIgnore);
            }
        }
    }

    /**
     * This replaces all occurrences of the target Strings with the replacement Strings in the given file and saves the file. It assumes that the size of the lists is equal and the
     * order of the argument is the same
     *
     * @param filePath     the path where the file is located
     * @param replacements the replacements that should be applied
     */
    public static void replaceVariablesInFile(Path filePath, Map<String, String> replacements) {
        log.debug("Replace {} in file {}", replacements, filePath);
        if (isBinaryFile(filePath.toString())) {
            // do not try to read binary files with 'readString'
            return;
        }
        try {
            // Note: Java does not offer a good way to check if a file is binary or not. If the basic check above fails (e.g. due to a custom binary file from an instructor),
            // but the file is still binary, we try to read it. In case the method readString fails, we only log this below, but continue, because the exception should NOT
            // interrupt the ongoing process
            String fileContent = Files.readString(filePath, UTF_8);
            for (Map.Entry<String, String> replacement : replacements.entrySet()) {
                fileContent = fileContent.replace(replacement.getKey(), replacement.getValue());
            }
            FileUtils.writeStringToFile(filePath.toFile(), fileContent, UTF_8);
        }
        catch (IOException ex) {
            log.warn("Exception {} occurred when trying to replace {} in (binary) file {}", ex.getMessage(), replacements, filePath);
            // continue
        }
    }

    /**
     * This normalizes all line endings to UNIX-line-endings recursively from the startPath.
     * <p>
     * {@link #normalizeLineEndings(Path) normalizeLineEndings}
     *
     * @param startPath the path where the start directory is located
     * @throws IOException if an issue occurs on file access for the normalizing of the line endings.
     */
    public static void normalizeLineEndingsDirectory(Path startPath) throws IOException {
        log.debug("Normalizing file endings in directory {}", startPath);
        File directory = startPath.toFile();
        if (!directory.exists() || !directory.isDirectory()) {
            throw new RuntimeException("File endings in directory " + startPath + " should be normalized but the directory does not exist.");
        }

        // Ignore the .git repository
        IOFileFilter directoryFileFilter = FileFilterUtils.notFileFilter(FileFilterUtils.nameFileFilter(".git"));
        // Get all files in directory
        Collection<File> files = FileUtils.listFiles(directory, FileFilterUtils.trueFileFilter(), directoryFileFilter);

        for (File file : files) {
            normalizeLineEndings(file.toPath().toAbsolutePath());
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
    public static void normalizeLineEndings(Path filePath) throws IOException {
        log.debug("Normalizing line endings in file {}", filePath);
        if (isBinaryFile(filePath.toString())) {
            // do not try to read binary files with 'readString'
            return;
        }
        // https://stackoverflow.com/questions/3776923/how-can-i-normalize-the-eol-character-in-java
        String fileContent = Files.readString(filePath, UTF_8);
        fileContent = fileContent.replaceAll("\\r\\n?", "\n");
        FileUtils.writeStringToFile(filePath.toFile(), fileContent, UTF_8);
    }

    /**
     * This converts all files to the UTF-8 encoding recursively from the startPath.
     * <p>
     * {@link #convertToUTF8(Path) convertToUTF8}
     *
     * @param startPath the path where the start directory is located
     * @throws IOException if an issue occurs on file access when converting to UTF-8.
     */
    public static void convertFilesInDirectoryToUtf8(Path startPath) throws IOException {
        log.debug("Converting files in directory {} to UTF-8", startPath);
        File directory = startPath.toFile();
        if (!directory.exists() || !directory.isDirectory()) {
            throw new RuntimeException("Files in directory " + startPath + " should be converted to UTF-8 but the directory does not exist.");
        }

        // Ignore the .git repository
        IOFileFilter directoryFileFilter = FileFilterUtils.notFileFilter(FileFilterUtils.nameFileFilter(".git"));
        // Get all files in directory
        Collection<File> files = FileUtils.listFiles(directory, FileFilterUtils.trueFileFilter(), directoryFileFilter);

        for (File file : files) {
            convertToUTF8(file.toPath());
        }
    }

    /**
     * This converts a specific file to the UTF-8 encoding.
     * To determine the encoding of the file, the library com.ibm.icu.text is used.
     *
     * @param filePath the path where the file is located
     * @throws IOException if an issue occurs on file access when converting to UTF-8.
     */
    public static void convertToUTF8(Path filePath) throws IOException {
        log.debug("Converting file {} to UTF-8", filePath);
        byte[] contentArray = Files.readAllBytes(filePath);

        Charset charset = detectCharset(contentArray);
        log.debug("Detected charset for file {} is {}", filePath, charset.name());

        String fileContent = new String(contentArray, charset);

        FileUtils.writeStringToFile(filePath.toFile(), fileContent, UTF_8);
    }

    /**
     * Detect the charset of a byte array
     *
     * @param contentArray The content that should be checked
     * @return The detected charset
     */
    public static Charset detectCharset(byte[] contentArray) {
        CharsetDetector charsetDetector = new CharsetDetector();
        charsetDetector.setText(contentArray);
        String charsetName = charsetDetector.detect().getName();
        return Charset.forName(charsetName);
    }

    /**
     * create a unique path by appending a folder named with the current milliseconds (e.g. 1609579674868) of the system
     * Note: the method also tries to create the mentioned folder
     *
     * @param path the original path, e.g. /opt/artemis/repos-download
     * @return the unique path, e.g. /opt/artemis/repos-download/1609579674868
     */
    public static Path getUniqueSubfolderPath(Path path) {
        var uniquePath = path.resolve(String.valueOf(System.currentTimeMillis()));
        if (!Files.exists(uniquePath) && Files.isDirectory(path)) {
            try {
                return Files.createDirectories(uniquePath);
            }
            catch (IOException e) {
                log.warn("could not create the directories for the path {}", uniquePath);
            }
        }
        return uniquePath;
    }

    /**
     * create a directory at a given path
     *
     * @param path the original path, e.g. /opt/artemis/repos-download
     */
    public static void createDirectory(Path path) {
        try {
            Files.createDirectories(path);
        }
        catch (IOException e) {
            var error = "Failed to create temporary directory at path " + path + " : " + e.getMessage();
            log.info(error);
        }
    }

    /**
     * Serialize an object and write into file at a given path
     *
     * @param object       The object that is serialized and written into a file
     * @param objectMapper The objectMapper that is used for serialization
     * @param path         The path where the file will be written to
     * @return Path to the written file
     */
    public static Path writeObjectToJsonFile(Object object, ObjectMapper objectMapper, Path path) throws IOException {
        objectMapper.writeValue(path.toFile(), object);
        return path;
    }

    /**
     * Merge the PDF files located in the given paths.
     *
     * @param paths             list of paths to merge
     * @param mergedPdfFilename title of merged pdf file
     * @return byte array of the merged file
     */
    public static Optional<byte[]> mergePdfFiles(List<Path> paths, String mergedPdfFilename) {
        if (paths == null || paths.isEmpty()) {
            return Optional.empty();
        }
        PDFMergerUtility pdfMerger = new PDFMergerUtility();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            for (Path path : paths) {
                if (Files.exists(path)) {
                    pdfMerger.addSource(path.toFile());
                }
            }

            PDDocumentInformation pdDocumentInformation = new PDDocumentInformation();
            pdDocumentInformation.setTitle(mergedPdfFilename);
            pdfMerger.setDestinationDocumentInformation(pdDocumentInformation);

            pdfMerger.setDestinationStream(outputStream);
            pdfMerger.mergeDocuments(null);

        }
        catch (IOException e) {
            log.warn("Could not merge files");
            return Optional.empty();
        }

        return Optional.of(outputStream.toByteArray());
    }

    /**
     * Deletes all specified files.
     *
     * @param filePaths A list of all paths to the files that should be deleted
     */
    public static void deleteFiles(List<Path> filePaths) {
        for (Path filePath : filePaths) {
            try {
                Files.delete(filePath);
            }
            catch (Exception ex) {
                log.warn("Could not delete file {}. Error message: {}", filePath, ex.getMessage());
            }
        }
    }

    /**
     * Convert byte[] to MultipartFile by using CommonsMultipartFile
     *
     * @param filename        file name to set file name
     * @param extension       extension of the file (e.g .pdf or .png)
     * @param streamByteArray byte array to save to the temp file
     * @return multipartFile wrapper for the file stored on disk with a sanitized name
     */
    public static MultipartFile convertByteArrayToMultipart(String filename, String extension, byte[] streamByteArray) {
        try {
            String cleanFilename = sanitizeFilename(filename);
            Path tempPath = FilePathConverter.getTempFilePath().resolve(cleanFilename + extension);
            FileUtils.writeByteArrayToFile(tempPath.toFile(), streamByteArray);
            File outputFile = tempPath.toFile();
            FileItem fileItem = new DiskFileItem(cleanFilename, Files.probeContentType(tempPath), false, outputFile.getName(), (int) outputFile.length(),
                    outputFile.getParentFile());

            try (InputStream input = Files.newInputStream(outputFile.toPath()); OutputStream fileItemOutputStream = fileItem.getOutputStream()) {
                input.transferTo(fileItemOutputStream);
            }
            return new CommonsMultipartFile(fileItem);
        }
        catch (IOException e) {
            log.error("Could not convert file {}.", filename, e);
            throw new InternalServerErrorException("Error while converting byte[] to MultipartFile by using CommonsMultipartFile");
        }
    }

    /**
     * Counts the number of tokens in a file, stopping when the total token count reaches the minimum token size.
     * The method reads the file content, splits it into tokens based on whitespace and common programming symbols,
     * and counts the tokens until the minimum token size is reached.
     *
     * @param path             The path to the file to count tokens in
     * @param minimumTokenSize The minimum number of tokens to count before stopping
     * @param totalTokenCount  The initial token count (usually 0)
     * @return The total number of tokens counted, or minimumTokenSize if there's an error reading the file
     */
    public static int countTokensInFile(Path path, int minimumTokenSize, int totalTokenCount) {
        try {
            String content = Files.readString(path);
            // Split the content into tokens using a regex that matches whitespace and common programming symbols.
            // This set of delimiters is chosen because it covers the most common token boundaries in programming languages.
            String[] tokens = content.split("[\\s\\n\\r\\t{}();,=+\\-*/<>!&|\\[\\]]+");

            for (String token : tokens) {
                // Count non-empty tokens
                if (!token.trim().isEmpty()) {
                    totalTokenCount++;
                    if (totalTokenCount >= minimumTokenSize) {
                        // Return totalTokenCount as soon as the minimum token size is reached
                        break;
                    }
                }
            }
            return totalTokenCount;
        }
        catch (IOException e) {
            log.warn("Failed to read file {}: {}", path, e.getMessage());
            return minimumTokenSize;
        }
    }
}
