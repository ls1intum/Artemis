package de.tum.cit.aet.artemis.localvc.service.git;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
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

    private static final Set<PosixFilePermission> OWNER_READ_WRITE = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private static final Set<StandardOpenOption> WRITE_OPTIONS = EnumSet.of(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

    private final Path root;

    DirectoryRepositoryContentSink(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    @Override
    public OutputStream openFile(String relativePath, int unixMode) throws IOException {
        Path target = resolveSafely(relativePath);
        Files.createDirectories(target.getParent());
        OutputStream outputStream = openOwnerOnly(target);
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
     * Opens the file for writing, owner-only from the instant it exists.
     *
     * <p>
     * Tightening the permissions after the content is written would still leave a window: {@link Files#newOutputStream}
     * creates the file under the process umask, so on a host with a permissive one the repository is readable by every
     * other local account for as long as the write takes. Passing the permissions as a creation attribute closes that
     * window, because the file never exists in a wider mode.
     *
     * <p>
     * The file does not stay owner-only: {@link #applyPermissions} widens it to the mode git recorded once the content
     * is there, because that mode is what the archive built from this directory hands to the student. The point of
     * creating it narrow is only that nothing can read it in the meantime.
     *
     * @param target the file to open
     * @return the stream to write the file content to
     * @throws IOException if the file cannot be opened
     */
    private static OutputStream openOwnerOnly(Path target) throws IOException {
        try {
            return Channels.newOutputStream(Files.newByteChannel(target, WRITE_OPTIONS, PosixFilePermissions.asFileAttribute(OWNER_READ_WRITE)));
        }
        catch (UnsupportedOperationException e) {
            // Windows has no POSIX view, so the creation attribute cannot be requested. The content is written either way.
            log.debug("Could not create {} with owner-only permissions: {}", target, e.getMessage());
            return Files.newOutputStream(target);
        }
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
     * Applies the mode git recorded for the file: {@code 0644}, or {@code 0755} when the executable bit is set.
     *
     * <p>
     * These permissions are not an internal detail of a scratch directory. The caller archives this directory from
     * disk, so whatever is set here is what the ZIP records and what the student ends up with after extracting. Losing
     * the executable bit is a real defect rather than a cosmetic one: git tracks it, so a {@code gradlew} that arrives
     * without it makes git report a modification in a working tree nobody has touched. Narrowing these to owner-only
     * to satisfy a static-analysis finding changed the delivered artifact and was caught by the export E2E tests.
     *
     * <p>
     * What is deliberately not derived from the mode is the group and world <em>write</em> bit. Git stores only
     * {@code 100644} and {@code 100755}, so no input can ask for it, and writing it out is what the finding was really
     * about. The exposure while the file is being written is handled separately, by {@link #openOwnerOnly}.
     *
     * <p>
     * The tempting alternative - keep the disk owner-only and let the archiver widen the mode on the entry - would have
     * to change {@code FileModeUtil.applyUnixMode}, which copies the mode off disk for <em>every</em> Artemis export
     * (course archives, exam exports, repository exports). Teaching that shared helper to reinterpret owner-only as
     * {@code 0755} would widen the permissions of every other export that narrows a file on purpose, to buy nothing
     * here: what is left on disk in the meantime is a plain {@code git checkout} of the student's own repository.
     */
    private static void applyPermissions(Path path, int unixMode) {
        Set<PosixFilePermission> permissions = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ,
                PosixFilePermission.OTHERS_READ);
        if ((unixMode & 0100) != 0) {
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            permissions.add(PosixFilePermission.GROUP_EXECUTE);
            permissions.add(PosixFilePermission.OTHERS_EXECUTE);
        }
        setPermissionsQuietly(path, permissions);
    }

    /**
     * Applies the permissions, tolerating a file system that has no POSIX view.
     *
     * @param path        the file or directory to change
     * @param permissions the permissions to set
     */
    private static void setPermissionsQuietly(Path path, Set<PosixFilePermission> permissions) {
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
