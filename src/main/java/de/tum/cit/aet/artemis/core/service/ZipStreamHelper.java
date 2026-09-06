package de.tum.cit.aet.artemis.core.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Internal helper for streaming ZIP creation from a {@link java.nio.file.Path} stream.
 * Not part of the public API; used by {@link ZipFileService} to implement file- and
 * memory-based ZIP assembly with minimal buffering.
 */
final class ZipStreamHelper {

    private static final Logger log = LoggerFactory.getLogger(ZipStreamHelper.class);

    private ZipStreamHelper() {
    }

    static void createZipFileFromPathStreamToMemory(ByteArrayOutputStream byteArrayOutputStream, Stream<Path> paths, Path pathsRoot, @Nullable Predicate<Path> extraFilter,
            Set<String> ignoredFileNames) throws IOException {
        try (ZipArchiveOutputStream zipOutputStream = new ZipArchiveOutputStream(byteArrayOutputStream)) {
            addPathsToZipStream(zipOutputStream, paths, pathsRoot, extraFilter, ignoredFileNames, false);
        }
    }

    static void createZipFileFromPathStream(Path zipFilePath, Stream<Path> paths, Path pathsRoot, @Nullable Predicate<Path> extraFilter, Set<String> ignoredFileNames)
            throws IOException {
        try (ZipArchiveOutputStream zipOutputStream = new ZipArchiveOutputStream(zipFilePath.toFile())) {
            addPathsToZipStream(zipOutputStream, paths, pathsRoot, extraFilter, ignoredFileNames, true);
        }
    }

    /**
     * @param storeAlreadyCompressed whether an already compressed file may be stored instead of deflated again. Only a
     *                                   seekable destination allows it, because Commons Compress has to back-patch the
     *                                   size and CRC that a STORED entry records.
     */
    static void addPathsToZipStream(ZipArchiveOutputStream zipOutputStream, Stream<Path> paths, Path pathsRoot, @Nullable Predicate<Path> extraFilter, Set<String> ignoredFileNames,
            boolean storeAlreadyCompressed) {
        try (Stream<Path> pathsStream = paths) {
            pathsStream.filter(path -> Files.isReadable(path) && !Files.isDirectory(path)).filter(path -> extraFilter == null || extraFilter.test(path))
                    .filter(path -> !ignoredFileNames.contains(path.getFileName().toString())).forEach(path -> {
                        String relativePath = pathsRoot.relativize(path).toString().replace('\\', '/');
                        ZipArchiveEntry zipEntry = new ZipArchiveEntry(relativePath);
                        FileModeUtil.applyUnixMode(zipEntry, path);
                        if (storeAlreadyCompressed && AlreadyCompressedFiles.matches(path)) {
                            zipEntry.setMethod(ZipEntry.STORED);
                        }
                        copyToZipFile(zipOutputStream, path, zipEntry);
                    });
        }
    }

    private static void copyToZipFile(ZipArchiveOutputStream zipOutputStream, Path path, ZipArchiveEntry zipEntry) {
        try {
            if (Files.exists(path)) {
                zipOutputStream.putArchiveEntry(zipEntry);
                FileUtils.copyFile(path.toFile(), zipOutputStream);
                zipOutputStream.closeArchiveEntry();
            }
        }
        catch (IOException e) {
            log.debug("Failed to copy file {} to zip: {}", path, e.getMessage());
            throw new java.io.UncheckedIOException("Failed to copy file to zip", e);
        }
    }
}
