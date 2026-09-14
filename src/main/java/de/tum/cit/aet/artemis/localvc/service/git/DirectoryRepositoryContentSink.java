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

    private static final Set<PosixFilePermission> OWNER_ALL = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);

    private static final Set<StandardOpenOption> WRITE_OPTIONS = EnumSet.of(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

    private final Path root;

    DirectoryRepositoryContentSink(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        createOwnerOnlyDirectories(this.root);
    }

    @Override
    public OutputStream openFile(String relativePath, int unixMode) throws IOException {
        Path target = resolveSafely(relativePath);
        createOwnerOnlyDirectories(target.getParent());
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
        createOwnerOnlyDirectories(resolveSafely(relativePath));
    }

    /**
     * Creates the directory and any missing parent, readable only by the owner.
     *
     * <p>
     * Giving the files owner-only content is not enough on its own: {@link Files#createDirectories} applies the process
     * umask, so with the usual {@code 022} the export tree stays {@code rwxr-xr-x} and every other local account can
     * still walk it and read the file names - which spell out the structure of a student's repository. Directories that
     * already exist are tightened as well, because the attribute below only applies to the ones actually created.
     *
     * @param directory the directory to create
     * @throws IOException if the directory cannot be created
     */
    private static void createOwnerOnlyDirectories(Path directory) throws IOException {
        // createDirectories returns quietly for a directory that is already there, and the attribute below only applies
        // to the ones it actually creates - so an existing directory keeps whatever mode it was made with.
        boolean existed = Files.isDirectory(directory);
        try {
            Files.createDirectories(directory, PosixFilePermissions.asFileAttribute(OWNER_ALL));
        }
        catch (UnsupportedOperationException e) {
            // Windows has no POSIX view, so the creation attribute cannot be requested.
            log.debug("Could not create {} with owner-only permissions: {}", directory, e.getMessage());
            Files.createDirectories(directory);
            return;
        }
        if (existed) {
            setPermissionsQuietly(directory, OWNER_ALL);
        }
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
     * A creation attribute only applies when the file is created, so {@link #applyPermissions} still runs on close -
     * both to add the executable bit and to cover a target that already existed.
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
        Set<PosixFilePermission> permissions = EnumSet.copyOf(OWNER_READ_WRITE);
        if ((unixMode & 0100) != 0) {
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
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
