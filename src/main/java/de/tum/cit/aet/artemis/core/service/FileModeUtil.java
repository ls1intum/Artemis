package de.tum.cit.aet.artemis.core.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Copies a file's POSIX permissions onto the ZIP entry that carries it.
 *
 * <p>
 * A ZIP written without them extracts everything as non-executable. That matters for exported programming
 * repositories: git tracks the executable bit, so an extracted repository whose {@code gradlew} lost it reports a
 * modification before anyone has touched the working tree, and the script cannot be run without a {@code chmod}.
 */
final class FileModeUtil {

    private static final Logger log = LoggerFactory.getLogger(FileModeUtil.class);

    /** Permissions to record when the file system cannot report any, matching a plain {@code rw-r--r--} file. */
    private static final int DEFAULT_FILE_MODE = 0644;

    /** Permissions to record for a directory when the file system cannot report any. */
    private static final int DEFAULT_DIRECTORY_MODE = 0755;

    private static final Map<PosixFilePermission, Integer> PERMISSION_BITS = Map.of(PosixFilePermission.OWNER_READ, 0400, PosixFilePermission.OWNER_WRITE, 0200,
            PosixFilePermission.OWNER_EXECUTE, 0100, PosixFilePermission.GROUP_READ, 0040, PosixFilePermission.GROUP_WRITE, 0020, PosixFilePermission.GROUP_EXECUTE, 0010,
            PosixFilePermission.OTHERS_READ, 0004, PosixFilePermission.OTHERS_WRITE, 0002, PosixFilePermission.OTHERS_EXECUTE, 0001);

    private FileModeUtil() {
    }

    /**
     * Records {@code path}'s POSIX permissions on {@code entry}.
     *
     * <p>
     * Windows has no POSIX view, and a file can vanish between being listed and being zipped, so a failure to read the
     * permissions falls back to a sensible default rather than aborting the export.
     *
     * @param entry the ZIP entry to annotate
     * @param path  the file or directory the entry was created from
     */
    static void applyUnixMode(ZipArchiveEntry entry, Path path) {
        try {
            entry.setUnixMode(toUnixMode(Files.getPosixFilePermissions(path)));
        }
        catch (IOException | UnsupportedOperationException e) {
            log.debug("Could not read the POSIX permissions of {}, storing the default mode instead: {}", path, e.getMessage());
            entry.setUnixMode(entry.isDirectory() ? DEFAULT_DIRECTORY_MODE : DEFAULT_FILE_MODE);
        }
    }

    private static int toUnixMode(Iterable<PosixFilePermission> permissions) {
        int mode = 0;
        for (PosixFilePermission permission : permissions) {
            mode |= PERMISSION_BITS.get(permission);
        }
        return mode;
    }
}
