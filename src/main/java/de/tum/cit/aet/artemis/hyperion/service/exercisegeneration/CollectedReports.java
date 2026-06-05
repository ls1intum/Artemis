package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

/**
 * Hardened reader for the {@code copyOut} tar of the verifier-owned reports directory. The directory is written by the pristine {@code verify.sh} (collecting only build-fresh
 * regular files), but the tar stream the container hands back is still untrusted: a compromised or buggy collection — or a Docker quirk — must not let an entry escape the expected
 * directory, dereference a planted symlink, or exhaust memory. So before any byte is handed to a production parser, every entry is validated:
 * <ul>
 * <li><strong>regular files only</strong> — a symbolic link ({@link TarArchiveEntry#isSymbolicLink()}), a hard link ({@link TarArchiveEntry#isLink()}), a directory, or any other
 * non-regular entry ({@link TarArchiveEntry#isFile()} is {@code false}) is REJECTED, so a planted link cannot redirect the verifier to read a file outside the reports dir;</li>
 * <li><strong>no path escape</strong> — the entry's normalized, prefix-stripped path must not be absolute and must not contain a {@code ..} segment, so it cannot point above the
 * reports directory;</li>
 * <li><strong>bounded size</strong> — each entry is capped at {@link #MAX_FILE_BYTES} and the whole archive at {@link #MAX_TOTAL_BYTES}, so a hostile report cannot exhaust
 * memory.</li>
 * </ul>
 * Only the surviving entries' bytes are returned, keyed by their flat collected file name (the verifier routes each name to the JUnit or the SCA parser). Any violation throws
 * {@link RejectedReportException}; the verifier treats that as a failed (rejected) verification rather than parsing partial, possibly-forged input.
 */
final class CollectedReports {

    /** Per-file cap. A single test/SCA report far larger than this is pathological; 32 MiB comfortably covers a verbose multi-suite JUnit XML or a large SARIF file. */
    static final long MAX_FILE_BYTES = 32L * 1024 * 1024;

    /** Whole-archive cap, so a flood of many files cannot exhaust memory even if each is individually under the per-file cap. */
    static final long MAX_TOTAL_BYTES = 128L * 1024 * 1024;

    private CollectedReports() {
    }

    /** Signals that a {@code copyOut} reports archive contained an entry that failed validation (symlink, hardlink, non-regular, path escape, or oversize). */
    static final class RejectedReportException extends RuntimeException {

        RejectedReportException(String message) {
            super(message);
        }
    }

    /**
     * Reads and validates the reports tar, returning each surviving regular file's bytes keyed by its flat collected name (the segment after {@code expectedPrefix}).
     *
     * @param tar            the copyOut archive (closed by the caller)
     * @param expectedPrefix the directory-name prefix Docker prepends to every entry (the reports subdir name, e.g. {@code solution}); entries are required to sit directly under
     *                           it
     * @return the validated regular files keyed by their flat name within the reports directory
     * @throws IOException             if reading the archive fails
     * @throws RejectedReportException if any entry is a symlink/hardlink/non-regular/path-escaping/oversize entry
     */
    static Map<String, byte[]> read(TarArchiveInputStream tar, String expectedPrefix) throws IOException {
        Map<String, byte[]> result = new LinkedHashMap<>();
        String normalizedPrefix = expectedPrefix.isEmpty() || expectedPrefix.endsWith("/") ? expectedPrefix : expectedPrefix + "/";
        long total = 0;
        TarArchiveEntry entry;
        while ((entry = tar.getNextEntry()) != null) {
            // Reject any non-regular entry outright: a symlink or hardlink could redirect a read outside the reports dir, and a device/fifo has no place in a reports tar.
            // (commons-compress's isFile() returns true for FIFO/character/block devices — their link flags are not the recognised non-file ones — so they are rejected
            // explicitly.)
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
            // The collected files are flat (one level under the reports dir). Reject anything absolute or containing a parent-dir hop, so a crafted name cannot escape the prefix.
            if (name.startsWith("/") || name.equals("..") || name.startsWith("../") || name.endsWith("/..") || name.contains("/../")) {
                throw new RejectedReportException("Refusing a report entry whose path escapes the reports directory: " + entry.getName());
            }
            long declaredSize = entry.getSize();
            if (declaredSize > MAX_FILE_BYTES) {
                throw new RejectedReportException("Refusing an oversized report entry (" + declaredSize + " bytes): " + entry.getName());
            }
            byte[] bytes = tar.readAllBytes();
            if (bytes.length > MAX_FILE_BYTES) {
                throw new RejectedReportException("Refusing an oversized report entry (" + bytes.length + " bytes): " + entry.getName());
            }
            total += bytes.length;
            if (total > MAX_TOTAL_BYTES) {
                throw new RejectedReportException("Refusing to read the verifier reports archive: total report size exceeds " + MAX_TOTAL_BYTES + " bytes");
            }
            // A later entry with the same flat name would only happen on a buggy/forged collection; keep the first deterministically.
            result.putIfAbsent(name, bytes);
        }
        return result;
    }

    /** Decodes a collected report's bytes as UTF-8 (the production parsers consume the report content as a String). */
    static String asString(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** Drops a leading {@code ./} and the expected directory prefix Docker prepends, leaving the flat collected name. */
    private static String stripPrefix(String rawName, String normalizedPrefix) {
        String name = rawName;
        while (name.startsWith("./")) {
            name = name.substring(2);
        }
        if (!normalizedPrefix.isEmpty() && name.startsWith(normalizedPrefix)) {
            name = name.substring(normalizedPrefix.length());
        }
        return name;
    }
}
