package de.tum.cit.aet.artemis.core.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import jakarta.annotation.Nullable;

import org.apache.commons.io.FileUtils;

final class ZipStreamHelper {

    private ZipStreamHelper() {
    }

    static void createZipFileFromPathStreamToMemory(ByteArrayOutputStream byteArrayOutputStream, Stream<Path> paths, Path pathsRoot, @Nullable Predicate<Path> extraFilter,
            Set<Path> ignoredNames) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
            addPathsToZipStream(zipOutputStream, paths, pathsRoot, extraFilter, ignoredNames);
        }
    }

    static void createZipFileFromPathStream(Path zipFilePath, Stream<Path> paths, Path pathsRoot, @Nullable Predicate<Path> extraFilter, Set<Path> ignoredNames)
            throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFilePath))) {
            addPathsToZipStream(zipOutputStream, paths, pathsRoot, extraFilter, ignoredNames);
        }
    }

    static void addPathsToZipStream(ZipOutputStream zipOutputStream, Stream<Path> paths, Path pathsRoot, @Nullable Predicate<Path> extraFilter, Set<Path> ignoredNames) {
        paths.filter(path -> Files.isReadable(path) && !Files.isDirectory(path)).filter(path -> extraFilter == null || extraFilter.test(path))
                .filter(path -> !ignoredNames.contains(path.getFileName())).forEach(path -> {
                    ZipEntry zipEntry = new ZipEntry(pathsRoot.relativize(path).toString());
                    copyToZipFile(zipOutputStream, path, zipEntry);
                });
    }

    private static void copyToZipFile(ZipOutputStream zipOutputStream, Path path, ZipEntry zipEntry) {
        try {
            if (Files.exists(path)) {
                zipOutputStream.putNextEntry(zipEntry);
                FileUtils.copyFile(path.toFile(), zipOutputStream);
                zipOutputStream.closeEntry();
            }
        }
        catch (IOException e) {
            // Reduce logging here to keep helper minimal; ZipFileService logs if needed
        }
    }
}
