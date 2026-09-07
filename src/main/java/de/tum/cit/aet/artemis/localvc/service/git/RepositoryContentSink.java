package de.tum.cit.aet.artemis.localvc.service.git;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Where {@link InMemoryRepositoryBuilder} puts the repository it materializes.
 *
 * <p>
 * The builder assembles a working tree next to a synthetic {@code .git/} directory, and both destinations it needs -
 * a ZIP a caller downloads, and a directory on disk - come down to the same two operations. Keeping them behind this
 * interface means the traversal, the pack and the index are written by exactly one implementation regardless of where
 * they end up.
 */
public interface RepositoryContentSink extends Closeable {

    /**
     * Opens the file at {@code relativePath} for writing. The caller writes the content and closes the stream; closing
     * the stream must not close the sink.
     *
     * @param relativePath path relative to the repository root, using {@code /} as separator
     * @param unixMode     POSIX permission bits to give the file
     * @return the stream to write the file content to
     * @throws IOException if the file cannot be opened
     */
    OutputStream openFile(String relativePath, int unixMode) throws IOException;

    /**
     * Ensures the directory at {@code relativePath} exists. Repeated calls for the same directory are ignored.
     *
     * @param relativePath path relative to the repository root, using {@code /} as separator
     * @throws IOException if the directory cannot be created
     */
    void createDirectory(String relativePath) throws IOException;

    /**
     * Writes a complete small file, for the handful of {@code .git} files whose content is already in memory.
     *
     * @param relativePath path relative to the repository root
     * @param content      the file content
     * @param unixMode     POSIX permission bits to give the file
     * @throws IOException if the file cannot be written
     */
    default void writeFile(String relativePath, byte[] content, int unixMode) throws IOException {
        try (OutputStream outputStream = openFile(relativePath, unixMode)) {
            outputStream.write(content);
        }
    }
}
