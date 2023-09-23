package de.tum.in.www1.artemis.service;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.commons.CommonsMultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.icu.text.CharsetDetector;

import de.tum.in.www1.artemis.exception.FilePathParsingException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

@Service
public class FileService implements DisposableBean {

    private final Logger log = LoggerFactory.getLogger(FileService.class);

    private final Map<Path, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    /**
     * A list of common binary file extensions.
     * Extensions must be lower-case without leading dots.
     */
    private static final Set<String> BINARY_FILE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "heic", "gif", "tiff", "psd", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "pages", "numbers", "key", "odt", "zip", "rar", "7z", "tar", "iso", "mdb", "sqlite", "exe", "jar", "bin", "so", "dll");

    /**
     * The list of file extensions that are allowed to be uploaded in a Markdown editor.
     * Extensions must be lower-case without leading dots.
     * NOTE: Has to be kept in sync with the client-side definitions in file-extensions.constants.ts
     */
    private final Set<String> allowedMarkdownFileExtensions = Set.of("png", "jpg", "jpeg", "gif", "svg", "pdf");

    /**
     * The global list of file extensions that are allowed to be uploaded.
     * Extensions must be lower-case without leading dots.
     * NOTE: Has to be kept in sync with the client-side definitions in file-extensions.constants.ts
     */
    private final Set<String> allowedFileExtensions = Set.of("png", "jpg", "jpeg", "gif", "svg", "pdf", "zip", "tar", "txt", "rtf", "md", "htm", "html", "json", "doc", "docx",
            "csv", "xls", "xlsx", "ppt", "pptx", "pages", "pages-tef", "numbers", "key", "odt", "ods", "odp", "odg", "odc", "odi", "odf");

    public static final String MARKDOWN_FILE_SUBPATH = "/api/files/markdown/";

    public static final String DEFAULT_FILE_SUBPATH = "/api/files/temp/";

    /**
     * Filenames for which the template filename differs from the filename it should have in the repository.
     */
    // @formatter:off
    private static final Map<String, String> FILENAME_REPLACEMENTS = Map.ofEntries(
        Map.entry("git.ignore.file", ".gitignore"),
        Map.entry("git.attributes.file", ".gitattributes"),
        Map.entry("Makefile.file", "Makefile"),
        Map.entry("dune.file", "dune"),
        Map.entry("Fast.file", "Fastfile"),
        Map.entry("App.file", "Appfile"),
        Map.entry("Scan.file", "Scanfile"),
        Map.entry("gradlew.file", "gradlew"));
    // @formatter:on

    /**
     * These directories get falsely marked as files and should be ignored during copying.
     */
    private static final List<String> IGNORED_DIRECTORY_SUFFIXES = List.of(".xcassets", ".colorset", ".appiconset", ".xcworkspace", ".xcodeproj", ".swiftpm", ".tests");

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
    public byte[] getFileForPath(Path path) throws IOException {
        if (Files.exists(path)) {
            return Files.readAllBytes(path);
        }
        return null;
    }

    /**
     * Evict the cache for the given path
     *
     * @param path the path for the file to evict from cache
     */
    @CacheEvict(value = "files", key = "#path")
    public void evictCacheForPath(Path path) {
        log.info("Invalidate files cache for {}", path);
        // Intentionally blank
    }

    /**
     * Sanitize the filename
     * <ul>
     * <li>replace all invalid characters with an underscore</li>
     * <li>replace multiple . with a single one</li>
     * </ul>
     *
     * @param filename the filename to sanitize
     * @return the sanitized filename
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
     * @return The API path of the file
     */
    @NotNull
    public URI handleSaveFile(MultipartFile file, boolean keepFilename, boolean markdown) {
        // check for file type
        String filename = checkAndSanitizeFilename(file.getOriginalFilename());

        // Check the allowed file extensions
        final String fileExtension = FilenameUtils.getExtension(filename);
        final Set<String> allowedExtensions = markdown ? allowedMarkdownFileExtensions : allowedFileExtensions;

        if (allowedExtensions.stream().noneMatch(fileExtension::equalsIgnoreCase)) {
            throw new BadRequestAlertException("Unsupported file type! Allowed file types: " + String.join(", ", allowedExtensions), "file", null, true);
        }

        final String filenamePrefix = markdown ? "Markdown_" : "Temp_";
        final Path path = markdown ? FilePathService.getMarkdownFilePath() : FilePathService.getTempFilePath();

        Path filePath;
        if (keepFilename) {
            filePath = path.resolve(filename);
        }
        else {
            filePath = generateFilePath(filenamePrefix, fileExtension, path);
        }
        try {
            FileUtils.copyToFile(file.getInputStream(), filePath.toFile());

            return generateResponsePath(filePath, markdown);
        }
        catch (IOException e) {
            log.error("Could not save file {}", filename, e);
            throw new InternalServerErrorException("Could not create file");
        }
    }

    private String checkAndSanitizeFilename(String filename) {
        if (filename == null) {
            throw new IllegalArgumentException("Filename cannot be null");
        }

        return sanitizeFilename(filename);
    }

    /**
     * Generates the API path getting returned to the client
     *
     * @param filePath the file system path of the file
     * @param markdown boolean which is set to true, when we are uploading a file in the Markdown format
     * @return the API path of the file
     */
    private URI generateResponsePath(Path filePath, boolean markdown) {
        String filename = filePath.getFileName().toString();
        if (markdown) {
            return URI.create(MARKDOWN_FILE_SUBPATH).resolve(filename);
        }
        return URI.create(DEFAULT_FILE_SUBPATH).resolve(filename);
    }

    /**
     * Generates the path for the file to be saved to with a random file name based on the parameters.
     *
     * @param filenamePrefix the prefix of the filename
     * @param fileExtension  the extension of the file
     * @param folder         the folder to save the file to
     * @return the path to save the file to
     */
    public Path generateFilePath(String filenamePrefix, String fileExtension, Path folder) {
        // append a timestamp and some randomness to the filename to avoid conflicts
        String generatedFilename = filenamePrefix + ZonedDateTime.now().toString().substring(0, 23).replaceAll("[:.]", "-") + "_" + UUID.randomUUID().toString().substring(0, 8)
                + "." + fileExtension;
        return folder.resolve(generatedFilename);
    }

    /**
     * Copies an existing non-temporary file to a target location.
     *
     * @param oldFilePath  the old file path
     * @param targetFolder the folder that a file should be copied to
     * @return the resulting file path or null on error
     */
    public Path copyExistingFileToTarget(Path oldFilePath, Path targetFolder) {
        if (oldFilePath != null && !pathContains(oldFilePath, Path.of(("files/temp")))) {
            String filename = oldFilePath.getFileName().toString();
            try {
                Path target = generateFilePath(generateTargetFilenameBase(targetFolder), FilenameUtils.getExtension(filename), targetFolder);
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
     * Generates a prefix for the filename based on the target folder
     *
     * @param targetFolder the target folder
     * @return the prefix ending with an underscore character as a separator
     */
    public String generateTargetFilenameBase(Path targetFolder) {
        if (targetFolder.equals(FilePathService.getDragAndDropBackgroundFilePath())) {
            return "DragAndDropBackground_";
        }
        if (targetFolder.equals(FilePathService.getDragItemFilePath())) {
            return "DragItem_";
        }
        if (targetFolder.equals(FilePathService.getCourseIconFilePath())) {
            return "CourseIcon_";
        }
        if (targetFolder.equals(FilePathService.getExamUserSignatureFilePath())) {
            return "ExamUserSignature_";
        }
        if (targetFolder.equals(FilePathService.getStudentImageFilePath())) {
            return "ExamUserImage_";
        }
        if (pathContains(targetFolder, FilePathService.getLectureAttachmentFilePath())) {
            return "LectureAttachment_";
        }
        if (pathContains(targetFolder, FilePathService.getAttachmentUnitFilePath())) {
            return "AttachmentUnit_";
        }
        if (pathContains(targetFolder, FilePathService.getAttachmentUnitFilePath()) && pathContains(targetFolder, Path.of("/slide"))) {
            return "AttachmentUnitSlide_";
        }
        return "Unspecified_";
    }

    private boolean pathContains(Path path, Path subPath) {
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
    public void copyResources(final Resource[] resources, final Path prefix, final Path targetDirectory, final boolean keepParentDirectories) throws IOException {
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
    public void copyResource(final Resource resource, final Path prefix, final Path targetDirectory, final boolean keepParentDirectories) throws IOException {
        final Path targetPath = generateTargetPath(resource, prefix, targetDirectory, keepParentDirectories);

        if (isIgnoredDirectory(targetPath)) {
            return;
        }

        FileUtils.copyToFile(resource.getInputStream(), targetPath.toFile());

        if (targetPath.endsWith("gradlew")) {
            targetPath.toFile().setExecutable(true);
        }
    }

    private Path generateTargetPath(final Resource resource, final Path prefix, final Path targetDirectory, final boolean keepParentDirectory) throws IOException {
        final Path filePath;
        if (resource.isFile()) {
            filePath = resource.getFile().toPath();
        }
        else {
            final String url = URLDecoder.decode(resource.getURL().toString(), UTF_8);
            filePath = Path.of(url);
        }

        final Path targetPath = generateTargetPath(filePath, prefix, targetDirectory, keepParentDirectory);
        return applyFilenameReplacements(targetPath);
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
    private Path generateTargetPath(final Path source, final Path prefix, final Path targetDirectory, final boolean keepParentDirectory) {
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

    private List<Path> getPathElements(final Path path) {
        final List<Path> elements = new ArrayList<>();

        for (final Path value : path) {
            elements.add(value);
        }

        return elements;
    }

    /**
     * Replaces filenames where the template name differs from the name the file should have in the repository.
     *
     * @param originalTargetPath The path to a file.
     * @return The path with replacements applied where necessary.
     */
    private Path applyFilenameReplacements(final Path originalTargetPath) {
        final Path filename = originalTargetPath.getFileName();

        final String newFilename = FILENAME_REPLACEMENTS.getOrDefault(filename.toString(), filename.toString());
        return originalTargetPath.getParent().resolve(newFilename);
    }

    /**
     * Checks if the given path has been identified as a file, but it actually points to a directory.
     *
     * @param filePath The path to a file/directory.
     * @return True, if the path is assumed to be a file but actually points to a directory.
     */
    private boolean isIgnoredDirectory(final Path filePath) {
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
    public void renameDirectory(Path oldDirectoryPath, Path targetDirectoryPath) throws IOException {
        File oldDirectory = oldDirectoryPath.toFile();
        if (!oldDirectory.exists()) {
            log.error("Directory {} should be renamed but does not exist.", oldDirectoryPath);
            throw new RuntimeException("Directory " + oldDirectoryPath + " should be renamed but does not exist.");
        }

        File targetDirectory = targetDirectoryPath.toFile();

        FileUtils.moveDirectory(oldDirectory, targetDirectory);
    }

    /**
     * Look for sections that start and end with a section marker (e.g. %section-start% and %section-end%). Overrides the given file in filePath with a new file!
     *
     * @param filePath of file to look for replaceable sections in.
     * @param sections of structure String (section name) / Boolean (keep content in section or remove it).
     */
    public void replacePlaceholderSections(Path filePath, Map<String, Boolean> sections) {
        Map<Pattern, Boolean> patternBooleanMap = sections.entrySet().stream().collect(Collectors.toMap(e -> Pattern.compile(".*%" + e.getKey() + ".*%.*"), Map.Entry::getValue));
        File file = filePath.toFile();
        File tempFile = new File(filePath + "_temp");
        if (!file.exists()) {
            throw new FilePathParsingException("File " + filePath + " should be updated but does not exist.");
        }

        try (var reader = new BufferedReader(new FileReader(file, UTF_8)); var writer = new BufferedWriter(new FileWriter(tempFile, UTF_8))) {
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
    public void replaceVariablesInDirectoryName(Path startPath, String targetString, String replacementString) throws IOException {
        log.debug("Replacing {} with {} in directory {}", targetString, replacementString, startPath);
        File directory = startPath.toFile();
        if (!directory.exists() || !directory.isDirectory()) {
            throw new RuntimeException("Directory " + startPath + " should be replaced but does not exist.");
        }
        String pathString = startPath.toString();
        if (pathString.contains(targetString)) {
            log.debug("Target String found, replacing..");
            String targetPath = pathString.replace(targetString, replacementString);
            renameDirectory(startPath, Path.of(targetPath));
            directory = new File(targetPath);
        }

        // Get all subdirectories
        final var subDirectories = directory.list((current, name) -> new File(current, name).isDirectory());

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
    public void replaceVariablesInFilename(Path startPath, String targetString, String replacementString) throws IOException {
        log.debug("Replacing {} with {} in directory {}", targetString, replacementString, startPath);
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
                    FileUtils.moveFile(filePath.toFile(), new File(cleanFileName));
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
    public void replaceVariablesInFileRecursive(Path startPath, Map<String, String> replacements) {
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
    public void replaceVariablesInFileRecursive(Path startPath, Map<String, String> replacements, List<String> filesToIgnore) {
        log.debug("Replacing {} in files in directory {}", replacements, startPath);
        File directory = startPath.toFile();
        if (!directory.exists() || !directory.isDirectory()) {
            throw new RuntimeException("Files in directory " + startPath + " should be replaced but the directory does not exist.");
        }

        // Get all files in directory
        String[] files = directory.list((current, name) -> new File(current, name).isFile());
        if (files != null) {
            // filter out files that should be ignored
            files = Arrays.stream(files).filter(Predicate.not(filesToIgnore::contains)).toArray(String[]::new);
            for (String file : files) {
                replaceVariablesInFile(directory.toPath().toAbsolutePath().resolve(file), replacements);
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
    public void replaceVariablesInFile(Path filePath, Map<String, String> replacements) {
        log.debug("Replacing {} in file {}", replacements, filePath);
        if (isBinaryFile(filePath)) {
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
     * very simple and non-exhaustive check for the most common binary files such as images
     * Unfortunately, Java cannot determine this correctly, so we need to provide typical file endings here
     *
     * @param filePath the path of the file
     * @return whether the simple check for file endings determines the underlying file to be binary (true) or not (false)
     */
    private static boolean isBinaryFile(Path filePath) {
        final String fileExtension = FilenameUtils.getExtension(filePath.getFileName().toString());
        return BINARY_FILE_EXTENSIONS.stream().anyMatch(fileExtension::equalsIgnoreCase);
    }

    /**
     * This normalizes all line endings to UNIX-line-endings recursively from the startPath.
     * <p>
     * {@link #normalizeLineEndings(Path) normalizeLineEndings}
     *
     * @param startPath the path where the start directory is located
     * @throws IOException if an issue occurs on file access for the normalizing of the line endings.
     */
    public void normalizeLineEndingsDirectory(Path startPath) throws IOException {
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
    public void normalizeLineEndings(Path filePath) throws IOException {
        log.debug("Normalizing line endings in file {}", filePath);
        if (isBinaryFile(filePath)) {
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
    public void convertFilesInDirectoryToUtf8(Path startPath) throws IOException {
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
    public void convertToUTF8(Path filePath) throws IOException {
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
    public Charset detectCharset(byte[] contentArray) {
        CharsetDetector charsetDetector = new CharsetDetector();
        charsetDetector.setText(contentArray);
        String charsetName = charsetDetector.detect().getName();
        return Charset.forName(charsetName);
    }

    /**
     * Schedule the deletion of the given nullsafe path with a given delay
     *
     * @param path           The path that should be deleted
     * @param delayInMinutes The delay in minutes after which the path should be deleted
     */
    public void schedulePathForDeletion(@Nullable Path path, long delayInMinutes) {
        if (path == null) {
            return;
        }
        ScheduledFuture<?> future = executor.schedule(() -> {
            try {
                if (Files.exists(path)) {
                    log.info("Delete file {}", path);
                    Files.delete(path);
                }
                futures.remove(path);
            }
            catch (IOException e) {
                log.error("Deleting the file {} did not work", path, e);
            }
        }, delayInMinutes, TimeUnit.MINUTES);

        futures.put(path, future);
    }

    /**
     * Schedule the recursive deletion of the given nullsafe directory with a given delay.
     *
     * @param path           The path to the directory that should be deleted
     * @param delayInMinutes The delay in minutes after which the path should be deleted
     */
    public void scheduleDirectoryPathForRecursiveDeletion(@Nullable Path path, long delayInMinutes) {
        if (path == null) {
            return;
        }
        ScheduledFuture<?> future = executor.schedule(() -> {
            try {
                if (Files.exists(path) && Files.isDirectory(path)) {
                    log.debug("Delete directory {}", path);
                    FileUtils.deleteDirectory(path.toFile());
                }
                futures.remove(path);
            }
            catch (IOException e) {
                log.error("Deleting the directory {} did not work", path, e);
            }
        }, delayInMinutes, TimeUnit.MINUTES);

        futures.put(path, future);
    }

    /**
     * create a unique path by appending a folder named with the current milliseconds (e.g. 1609579674868) of the system
     * Note: the method also tries to create the mentioned folder
     *
     * @param path the original path, e.g. /opt/artemis/repos-download
     * @return the unique path, e.g. /opt/artemis/repos-download/1609579674868
     */
    public Path getUniqueSubfolderPath(Path path) {
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
     * create a unique path by appending a folder named with the current milliseconds (e.g. 1609579674868) of the system and schedules it for deletion.
     * See {@link #getUniqueSubfolderPath(Path)} for more information.
     *
     * @param path                 the original path, e.g. /opt/artemis/repos-download
     * @param deleteDelayInMinutes the delay in minutes after which the path should be deleted
     * @return the unique path, e.g. /opt/artemis/repos-download/1609579674868
     */
    public Path getTemporaryUniqueSubfolderPath(Path path, long deleteDelayInMinutes) {
        var temporaryPath = getUniqueSubfolderPath(path);
        scheduleDirectoryPathForRecursiveDeletion(temporaryPath, deleteDelayInMinutes);
        return temporaryPath;
    }

    /**
     * Create a unique path by appending a folder named with the current milliseconds (e.g. 1609579674868) of the system but does not create the folder.
     * This is used when cloning the programming exercises into a new temporary directory because if we already create the directory, the git clone does not work anymore.
     * The directory will be scheduled for deletion.
     *
     * @param path                 the original path, e.g. /opt/artemis/repos-download
     * @param deleteDelayInMinutes the delay in minutes after which the path should be deleted
     * @return the unique path, e.g. /opt/artemis/repos-download/1609579674868
     */
    public Path getTemporaryUniquePathWithoutPathCreation(Path path, long deleteDelayInMinutes) {
        var temporaryPath = path.resolve(String.valueOf(System.currentTimeMillis()));
        scheduleDirectoryPathForRecursiveDeletion(temporaryPath, deleteDelayInMinutes);
        return temporaryPath;
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
     * Serialize an object and write into file at a given path
     *
     * @param object       The object that is serialized and written into a file
     * @param objectMapper The objectMapper that is used for serialization
     * @param path         The path where the file will be written to
     * @return Path to the written file
     */
    public Path writeObjectToJsonFile(Object object, ObjectMapper objectMapper, Path path) throws IOException {
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
    public Optional<byte[]> mergePdfFiles(List<String> paths, String mergedPdfFilename) {
        if (paths == null || paths.isEmpty()) {
            return Optional.empty();
        }
        PDFMergerUtility pdfMerger = new PDFMergerUtility();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            for (String path : paths) {
                File file = new File(path);
                if (file.exists()) {
                    pdfMerger.addSource(file);
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

    /**
     * Convert byte[] to MultipartFile by using CommonsMultipartFile
     *
     * @param filename        file name to set file name
     * @param extension       extension of the file (e.g .pdf or .png)
     * @param streamByteArray byte array to save to the temp file
     * @return multipartFile wrapper for the file stored on disk with a sanitized name
     */
    public MultipartFile convertByteArrayToMultipart(String filename, String extension, byte[] streamByteArray) {
        try {
            String cleanFilename = sanitizeFilename(filename);
            Path tempPath = FilePathService.getTempFilePath().resolve(cleanFilename + extension);
            FileUtils.writeByteArrayToFile(tempPath.toFile(), streamByteArray);
            File outputFile = tempPath.toFile();
            FileItem fileItem = new DiskFileItem(cleanFilename, Files.probeContentType(tempPath), false, outputFile.getName(), (int) outputFile.length(),
                    outputFile.getParentFile());

            try (InputStream input = new FileInputStream(outputFile); OutputStream fileItemOutputStream = fileItem.getOutputStream()) {
                IOUtils.copy(input, fileItemOutputStream);
            }
            return new CommonsMultipartFile(fileItem);
        }
        catch (IOException e) {
            log.error("Could not convert file {}.", filename, e);
            throw new InternalServerErrorException("Error while converting byte[] to MultipartFile by using CommonsMultipartFile");
        }
    }
}
