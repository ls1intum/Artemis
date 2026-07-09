package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

/** Validates and reads the verifier reports tar returned by the sandbox. */
public final class CollectedReports {

    static final long MAX_FILE_BYTES = 32L * 1024 * 1024;

    static final long MAX_TOTAL_BYTES = 128L * 1024 * 1024;

    private CollectedReports() {
    }

    public static final class RejectedReportException extends RuntimeException {

        RejectedReportException(String message) {
            super(message);
        }
    }

    /**
     * Reads the flat verifier report directory from a tar stream while rejecting path escapes, links, special files, duplicate names, and oversized entries.
     *
     * @param tar            the report archive stream
     * @param expectedPrefix the directory prefix the sandbox wrote the reports under
     * @return report file contents keyed by file name
     * @throws IOException if the tar stream cannot be read
     */
    public static Map<String, byte[]> read(TarArchiveInputStream tar, String expectedPrefix) throws IOException {
        Map<String, byte[]> result = new LinkedHashMap<>();
        String normalizedPrefix = expectedPrefix.isEmpty() || expectedPrefix.endsWith("/") ? expectedPrefix : expectedPrefix + "/";
        long total = 0;
        TarArchiveEntry entry;
        while ((entry = tar.getNextEntry()) != null) {
            // commons-compress's isFile() returns true for FIFO/character/block devices — their link flags are not the recognised non-file ones — so they are rejected explicitly.
            if (entry.isSymbolicLink() || entry.isLink()) {
                throw new RejectedReportException("Refusing to read a linked report entry from the verifier reports archive: " + entry.getName());
            }
            if (entry.isDirectory()) {
                continue;
            }
            if (entry.isFIFO() || entry.isCharacterDevice() || entry.isBlockDevice() || !entry.isFile()) {
                throw new RejectedReportException("Refusing to read a non-regular report entry from the verifier reports archive: " + entry.getName());
            }
            String name = stripPrefix(entry.getName(), normalizedPrefix);
            if (name.isEmpty()) {
                continue;
            }
            if (name.startsWith("/") || name.contains("/") || name.equals("..") || name.startsWith("../") || name.endsWith("/..") || name.contains("/../")) {
                throw new RejectedReportException("Refusing a report entry outside the flat reports directory: " + entry.getName());
            }
            long declaredSize = entry.getSize();
            if (declaredSize > MAX_FILE_BYTES) {
                throw new RejectedReportException("Refusing an oversized report entry (" + declaredSize + " bytes): " + entry.getName());
            }
            byte[] bytes = readEntryBytes(tar, entry.getName());
            total += bytes.length;
            if (total > MAX_TOTAL_BYTES) {
                throw new RejectedReportException("Refusing to read the verifier reports archive: total report size exceeds " + MAX_TOTAL_BYTES + " bytes");
            }
            if (result.putIfAbsent(name, bytes) != null) {
                throw new RejectedReportException("Refusing duplicate report entry: " + name);
            }
        }
        return result;
    }

    public static String asString(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] readEntryBytes(TarArchiveInputStream tar, String name) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = tar.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            if (out.size() > MAX_FILE_BYTES) {
                throw new RejectedReportException("Refusing an oversized report entry (" + out.size() + " bytes): " + name);
            }
        }
        return out.toByteArray();
    }

    private static String stripPrefix(String rawName, String normalizedPrefix) {
        String name = rawName;
        while (name.startsWith("./")) {
            name = name.substring(2);
        }
        if (normalizedPrefix.isEmpty()) {
            return name;
        }
        if (!name.startsWith(normalizedPrefix)) {
            throw new RejectedReportException("Refusing a report entry outside the expected reports directory: " + rawName);
        }
        return name.substring(normalizedPrefix.length());
    }
}
