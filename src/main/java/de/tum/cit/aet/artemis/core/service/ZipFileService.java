package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import jakarta.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * A service class to create zip files
 */
@Profile(PROFILE_CORE)
@Service
public class ZipFileService {

    private static final Logger log = LoggerFactory.getLogger(ZipFileService.class);

    private final FileService fileService;

    /**
     * Set of file names that should be ignored when zipping.
     * This currently only includes the gc.log.lock (garbage collector) file created by JGit in programming repositories.
     */
    private static final Set<Path> IGNORED_ZIP_FILE_NAMES = Set.of(Path.of("gc.log.lock"));

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
        try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(zipFilePath.toFile()))) {
            for (var path : paths) {
                if (Files.isReadable(path)) {
                    if (Files.isDirectory(path)) {
                        Files.walk(path).filter(p -> !Files.isDirectory(p)).forEach(p -> {
                            try {
                                addFileToZip(zipOut, p, path);
                            }
                            catch (IOException e) {
                                throw new RuntimeException("Error adding file to ZIP", e);
                            }
                        });
                    }
                    else {
                        addFileToZip(zipOut, path, path.getParent());
                    }
                }
            }
        }
    }

    private void addFileToZip(ZipOutputStream zipOut, Path file, Path basePath) throws IOException {
        String zipEntryName = basePath.relativize(file).toString();
        zipOut.putNextEntry(new ZipEntry(zipEntryName));
        try (FileInputStream fis = new FileInputStream(file.toFile())) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) >= 0) {
                zipOut.write(buffer, 0, length);
            }
        }
        zipOut.closeEntry();
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
     * Extracts a zip file to a folder with the same name as the zip file
     *
     * @param zipPath path to the zip file
     * @throws IOException if an error occurred while extracting
     */

    public void extractZipFileRecursively(Path zipPath) throws IOException {
        Path parentDir = zipPath.toAbsolutePath().getParent();
        String baseName = zipPath.getFileName().toString().replaceFirst("\\.zip$", "");
        Path dirToUnzip = Files.createDirectory(parentDir.resolve(baseName));

        // Extract the zip file
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path filePath = dirToUnzip.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(filePath);
                }
                else {
                    Files.createDirectories(filePath.getParent()); // Ensure parent directories exist
                    Files.copy(zis, filePath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }

        // Check for nested zip files and extract them
        try (var filesInDirStream = Files.list(dirToUnzip)) {
            List<Path> zipFilesInDir = filesInDirStream.filter(path -> path.toString().toLowerCase().endsWith(".zip")).toList();

            for (Path nestedZip : zipFilesInDir) {
                extractZipFileRecursively(nestedZip);
            }
        }
    }

    private void createZipFileFromPathStream(Path zipFilePath, Stream<Path> paths, Path pathsRoot, @Nullable Predicate<Path> extraFilter) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFilePath))) {
            var filteredPaths = paths.filter(path -> Files.isReadable(path) && !Files.isDirectory(path));
            if (extraFilter != null) {
                filteredPaths = filteredPaths.filter(extraFilter);
            }
            filteredPaths.filter(path -> !IGNORED_ZIP_FILE_NAMES.contains(path)).forEach(path -> {
                ZipEntry zipEntry = new ZipEntry(pathsRoot.relativize(path).toString());
                copyToZipFile(zipOutputStream, path, zipEntry);
            });
        }
    }

    private void copyToZipFile(ZipOutputStream zipOutputStream, Path path, ZipEntry zipEntry) {
        try {
            if (Files.exists(path)) {
                zipOutputStream.putNextEntry(zipEntry);
                FileUtils.copyFile(path.toFile(), zipOutputStream);
                zipOutputStream.closeEntry();
            }
        }
        catch (IOException e) {
            log.error("Create zip file error", e);
        }
    }
}
