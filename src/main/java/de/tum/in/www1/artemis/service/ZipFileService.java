package de.tum.in.www1.artemis.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ZipFileService {

    private final Logger log = LoggerFactory.getLogger(ZipFileService.class);

    /**
     * Create a zip file of the given paths and save it in the zipFilePath
     *
     * @param zipFilePath       path where the zip file should be saved
     * @param paths             multiple paths that should be zipped
     * @param createDirsInZipFile if set to true, the zip file will contain all directories included in the paths.
     * @throws IOException if an error occurred while zipping
     */
    public void createZipFile(Path zipFilePath, List<Path> paths, boolean createDirsInZipFile) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFilePath))) {
            paths.stream().filter(path -> !Files.isDirectory(path)).forEach(path -> {
                var zipPath = createDirsInZipFile ? path : path.getFileName();
                ZipEntry zipEntry = new ZipEntry(zipPath.toString());
                copyToZipFile(zipOutputStream, path, zipEntry);
            });
        }
    }

    /**
     * Create a zip file of the given paths and save it in the zipFilePath
     *
     * @param zipFilePath path where the zip file should be saved
     * @param paths       multiple paths that should be zipped
     * @param pathsRoot   the root path relative to <code>paths</code>
     * @throws IOException if an error occurred while zipping
     */
    public void createZipFile(Path zipFilePath, List<Path> paths, Path pathsRoot) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFilePath))) {
            paths.stream().filter(path -> !Files.isDirectory(path)).forEach(path -> {
                ZipEntry zipEntry = new ZipEntry(pathsRoot.relativize(path).toString());
                copyToZipFile(zipOutputStream, path, zipEntry);
            });
        }
    }

    /**
     * Recursively include all files in contentRootPath and create a zip file 'zipFileName' in the folder 'zipFileFolderName'
     *
     * @param zipFilePath     path where the zip file should be saved
     * @param contentRootPath a path to a folder: all content in this folder (and in any subfolders) will be included in the zip file
     * @return the path of the newly created zip file for further processing
     * @throws IOException if an error occurred while zipping
     */
    public Path createZipFileWithFolderContent(Path zipFilePath, Path contentRootPath) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFilePath))) {
            Files.walk(contentRootPath).filter(path -> !Files.isDirectory(path) && Files.exists(path)).forEach(path -> {
                ZipEntry zipEntry = new ZipEntry(contentRootPath.relativize(path).toString());
                copyToZipFile(zipOutputStream, path, zipEntry);
            });
        }
        return zipFilePath;
    }

    private void copyToZipFile(ZipOutputStream zipOutputStream, Path path, ZipEntry zipEntry) {
        try {
            zipOutputStream.putNextEntry(zipEntry);
            Files.copy(path, zipOutputStream);
            zipOutputStream.closeEntry();
        }
        catch (IOException e) {
            log.error("Create zip file error", e);
        }
    }
}
