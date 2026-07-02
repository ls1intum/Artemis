package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

/**
 * A service class to create zip files
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ZipFileService {

    private static final Logger log = LoggerFactory.getLogger(ZipFileService.class);

    private final FileService fileService;

    /**
     * Set of file names that should be ignored when zipping.
     * This currently only includes the gc.log.lock (garbage collector) file created by JGit in programming repositories.
     */
    private static final Set<String> IGNORED_ZIP_FILE_NAMES = Set.of("gc.log.lock");

    public ZipFileService(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * Create a zip file of the given paths and save it in the zipFilePath
     *
     * @param zipFilePath path where the zip file should be saved
     * @param paths       multiple paths that should be zipped
     * @throws IOException if an error occurred while zipping
     */
    public void createZipFile(Path zipFilePath, List<Path> paths) throws IOException {
        log.debug("Creating zip file at {} for paths: {}", zipFilePath, paths);
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFilePath))) {
            for (var path : paths) {
                if (!Files.isReadable(path)) {
                    continue;
                }
                // A single file is stored at the zip root under its file name; a directory is stored recursively with
                // the directory name as the top-level entry. Both are achieved by walking the path and relativizing
                // each contained file against the path's parent.
                try (Stream<Path> files = Files.walk(path)) {
                    ZipStreamHelper.addPathsToZipStream(zipOutputStream, files, path.getParent(), null, Set.of());
                }
            }
        }
    }

    /**
     * Create a zip file of the given paths and save it in the zipFilePath. The zipFilePath will be deleted after the specified delay.
     *
     * @param zipFilePath          path where the zip file should be saved
     * @param paths                multiple paths that should be zipped
     * @param deleteDelayInMinutes delay in minutes after which the zip is deleted
     * @throws IOException if an error occurred while zipping
     */
    public void createTemporaryZipFile(Path zipFilePath, List<Path> paths, long deleteDelayInMinutes) throws IOException {
        createZipFile(zipFilePath, paths);
        fileService.schedulePathForDeletion(zipFilePath, deleteDelayInMinutes);
    }

    /**
     * Recursively include all files in contentRootPath and create a zip file 'zipFileName' in the folder 'zipFileFolderName'
     *
     * @param zipFilePath     path where the zip file should be saved
     * @param contentRootPath a path to a folder: all content in this folder (and in any subfolders) will be included in the zip file
     * @param contentFilter   a path filter to exclude some files, can be null to include everything
     * @return the path of the newly created zip file for further processing
     * @throws IOException if an error occurred while zipping
     */
    public Path createZipFileWithFolderContent(Path zipFilePath, Path contentRootPath, @Nullable Predicate<Path> contentFilter) throws IOException {
        try (var files = Files.walk(contentRootPath)) {
            createZipFileFromPathStream(zipFilePath, files, contentRootPath, contentFilter);
            return zipFilePath;
        }
    }

    /**
     * Recursively include all files in contentRootPath and create a zip file in memory.
     *
     * @param contentRootPath a path to a folder: all content in this folder (and in any subfolders) will be included in the zip file
     * @param filename        the filename for the zip (for metadata purposes)
     * @param contentFilter   a path filter to exclude some files, can be null to include everything
     * @return ByteArrayResource containing the zip file data
     * @throws IOException if an error occurred while zipping
     */
    public ByteArrayResource createZipFileWithFolderContentInMemory(Path contentRootPath, String filename, @Nullable Predicate<Path> contentFilter) throws IOException {
        try (var files = Files.walk(contentRootPath); ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {

            createZipFileFromPathStreamToMemory(byteArrayOutputStream, files, contentRootPath, contentFilter);

            byte[] zipData = byteArrayOutputStream.toByteArray();
            return new ByteArrayResource(zipData) {

                @Override
                public String getFilename() {
                    return filename;
                }
            };
        }
    }

    private void createZipFileFromPathStreamToMemory(ByteArrayOutputStream byteArrayOutputStream, Stream<Path> paths, Path pathsRoot, @Nullable Predicate<Path> extraFilter)
            throws IOException {
        ZipStreamHelper.createZipFileFromPathStreamToMemory(byteArrayOutputStream, paths, pathsRoot, extraFilter, IGNORED_ZIP_FILE_NAMES);
    }

    private void createZipFileFromPathStream(Path zipFilePath, Stream<Path> paths, Path pathsRoot, @Nullable Predicate<Path> extraFilter) throws IOException {
        ZipStreamHelper.createZipFileFromPathStream(zipFilePath, paths, pathsRoot, extraFilter, IGNORED_ZIP_FILE_NAMES);
    }

    /**
     * Extracts a zip file to a folder with the same name as the zip file
     *
     * @param zipPath path to the zip file
     * @throws IOException if an error occurred while extracting
     */
    public void extractZipFileRecursively(Path zipPath) throws IOException {
        var dirToUnzip = Files.createDirectory(zipPath.toAbsolutePath().getParent().resolve(FilenameUtils.getBaseName(zipPath.toString())));
        extractZipFile(zipPath, dirToUnzip);
        List<Path> zipFilesInDirList;
        try (var zipFilesInDir = Files.list(dirToUnzip).filter(path -> "zip".equalsIgnoreCase(FilenameUtils.getExtension(path.toString())))) {
            zipFilesInDirList = zipFilesInDir.toList();
        }
        for (Path path : zipFilesInDirList) {
            extractZipFileRecursively(path);
        }
    }

    /**
     * Extracts all entries of a zip file into the target directory, preserving the directory structure. Entries that
     * would resolve outside the target directory (zip slip / path traversal) are rejected.
     *
     * @param zipPath   the zip file to extract
     * @param targetDir the directory to extract into (must already exist)
     * @throws IOException if an entry escapes the target directory or extraction fails
     */
    private static void extractZipFile(Path zipPath, Path targetDir) throws IOException {
        Path normalizedTarget = targetDir.toAbsolutePath().normalize();
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            var entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path resolved = normalizedTarget.resolve(entry.getName()).normalize();
                if (!resolved.startsWith(normalizedTarget)) {
                    throw new IOException("Blocked zip entry outside the target directory (zip slip): " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                }
                else {
                    try (var inputStream = zipFile.getInputStream(entry)) {
                        // FileUtils.copyInputStreamToFile creates missing parent directories; java.nio Files.copy is
                        // forbidden by ArchitectureTest.testFileWriteUsage precisely because it does not.
                        FileUtils.copyInputStreamToFile(inputStream, resolved.toFile());
                    }
                }
            }
        }
    }
}
