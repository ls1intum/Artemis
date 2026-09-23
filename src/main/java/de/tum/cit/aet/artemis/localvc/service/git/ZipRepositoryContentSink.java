package de.tum.cit.aet.artemis.localvc.service.git;

import static de.tum.cit.aet.artemis.localvc.service.git.InMemoryDirCache.DIRECTORY_EXECUTE_MODE;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.output.CloseShieldOutputStream;

/**
 * Writes the materialized repository into a ZIP stream.
 *
 * <p>
 * The caller keeps ownership of the stream it passes in: closing this sink finishes the ZIP's central directory and
 * releases the deflater without closing the stream underneath.
 */
class ZipRepositoryContentSink implements RepositoryContentSink {

    private final ZipArchiveOutputStream zipOutputStream;

    private final Set<String> createdDirectories = new HashSet<>();

    ZipRepositoryContentSink(OutputStream outputStream) {
        this.zipOutputStream = new ZipArchiveOutputStream(CloseShieldOutputStream.wrap(outputStream));
        zipOutputStream.setMethod(ZipEntry.DEFLATED);
        // Level 6 rather than 9: on export payloads the extra level buys a fraction of a percent for measurably more
        // CPU, and most of the bytes here are the pack file, which git has already compressed.
        zipOutputStream.setLevel(Deflater.DEFAULT_COMPRESSION);
    }

    @Override
    public OutputStream openFile(String relativePath, int unixMode) throws IOException {
        createParentDirectories(relativePath);
        ZipArchiveEntry entry = new ZipArchiveEntry(relativePath);
        entry.setUnixMode(unixMode);
        zipOutputStream.putArchiveEntry(entry);
        // Callers close what they are handed, and that has to end the entry rather than the archive.
        return new FilterOutputStream(zipOutputStream) {

            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
                // FilterOutputStream would otherwise forward this a byte at a time.
                out.write(bytes, offset, length);
            }

            @Override
            public void close() throws IOException {
                flush();
                zipOutputStream.closeArchiveEntry();
            }
        };
    }

    @Override
    public void createDirectory(String relativePath) throws IOException {
        String directory = relativePath.endsWith("/") ? relativePath : relativePath + "/";
        if (!createdDirectories.add(directory)) {
            return;
        }
        ZipArchiveEntry entry = new ZipArchiveEntry(directory);
        entry.setUnixMode(DIRECTORY_EXECUTE_MODE);
        zipOutputStream.putArchiveEntry(entry);
        zipOutputStream.closeArchiveEntry();
    }

    private void createParentDirectories(String path) throws IOException {
        String normalized = path.replace('\\', '/');
        int lastSeparator = normalized.lastIndexOf('/');
        if (lastSeparator < 0) {
            return;
        }
        String parent = normalized.substring(0, lastSeparator);
        int index = 0;
        while ((index = parent.indexOf('/', index)) != -1) {
            createDirectory(parent.substring(0, index + 1));
            index++;
        }
        createDirectory(parent);
    }

    @Override
    public void close() throws IOException {
        zipOutputStream.close();
    }
}
