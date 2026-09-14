package de.tum.cit.aet.artemis.localvc.service.git;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes the materialized repository into a directory on disk.
 *
 * <p>
 * This is what lets an export that has to hand back a directory - the personal data export does - skip cloning and
 * checking the repository out: the working tree and the synthetic {@code .git/} are written straight from the bare
 * repository's objects.
 */
class DirectoryRepositoryContentSink implements RepositoryContentSink {

    private static final Logger log = LoggerFactory.getLogger(DirectoryRepositoryContentSink.class);

    private final Path root;

    DirectoryRepositoryContentSink(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    @Override
    public OutputStream openFile(String relativePath, int unixMode) throws IOException {
        Path target = resolveSafely(relativePath);
        Files.createDirectories(target.getParent());
        OutputStream outputStream = Files.newOutputStream(target);
        return new OutputStream() {

            @Override
            public void write(int singleByte) throws IOException {
                outputStream.write(singleByte);
            }

            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
                outputStream.write(bytes, offset, length);
            }

            @Override
            public void flush() throws IOException {
                // Without this, OutputStream.flush() would silently do nothing, and a writer that flushes instead of
                // closing would lose whatever the file stream still holds.
                outputStream.flush();
            }

            @Override
            public void close() throws IOException {
                outputStream.close();
                applyPermissions(target, unixMode);
            }
        };
    }

    @Override
    public void createDirectory(String relativePath) throws IOException {
        Files.createDirectories(resolveSafely(relativePath));
    }

    /**
     * Resolves a path from the repository against the target directory, rejecting anything that would escape it. Names
     * come from a git tree, which can hold whatever a pushing client put there, so they are treated as untrusted.
     */
    private Path resolveSafely(String relativePath) throws IOException {
        Path resolved = root.resolve(relativePath.replace('\\', '/')).normalize();
        if (!resolved.startsWith(root)) {
            throw new IOException("Refusing to write " + relativePath + " because it escapes the target directory " + root);
        }
        return resolved;
    }

    /**
     * Gives the file to its owner alone, carrying over the executable bit.
     *
     * <p>
     * Git stores exactly two blob modes, {@code 100644} and {@code 100755}, and those are the only two values the
     * builder passes in. The group and world bits of a mode are therefore a constant rather than something the
     * repository expressed, and expanding them here would publish student code to every other account on the host for
     * no gain - a materialized repository is read back by this process alone, on its way into the personal data
     * export. The executable bit is the one bit that does carry information, so it is the one that is kept.
     *
     * <p>
     * This governs the export directory on disk only. {@link ZipRepositoryContentSink} records the original mode on
     * its ZIP entries, so what a student downloads is unchanged.
     */
    private static void applyPermissions(Path path, int unixMode) {
        Set<PosixFilePermission> permissions = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
        if ((unixMode & 0100) != 0) {
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
        }
        try {
            Files.setPosixFilePermissions(path, permissions);
        }
        catch (IOException | UnsupportedOperationException e) {
            // Windows has no POSIX view. The content is written either way, and only the executable bit is lost.
            log.debug("Could not set the permissions of {}: {}", path, e.getMessage());
        }
    }

    @Override
    public void close() {
        // Nothing to release: every file is closed as it is written.
    }
}
