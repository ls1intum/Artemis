package de.tum.cit.aet.artemis.programming.service.git;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.stream.IntStream;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.FS;

/**
 * Simple in-memory {@link DirCache} capable of serializing itself to the Git index
 * file format (v2). Used to embed a coherent index into exported repositories without
 * touching the filesystem.
 */
public class InMemoryDirCache extends DirCache {

    public static final int READ_WRITE_MODE = 0100644;

    public static final int EXECUTE_MODE = 0100755;

    public static final int DIRECTORY_EXECUTE_MODE = 040755;

    /**
     * Create a new in-core index representation.
     * <p>
     * The new index will be empty. Callers may wish to read from the on disk
     * file first with {@link #read()}.
     *
     * @param indexLocation location of the index file on disk.
     * @param fileSystem    the file system abstraction which will be necessary to perform
     *                          certain file system operations.
     */
    public InMemoryDirCache(File indexLocation, FS fileSystem) {
        super(indexLocation, fileSystem);
    }

    /**
     * Writes this in-memory index to the given output stream in Git index format (v2),
     * including the trailing SHA-1 checksum.
     *
     * @param outputStream the destination output stream
     * @throws IOException if writing fails
     */
    public void writeTo(OutputStream outputStream) throws IOException {
        // Collect and sort entries by path (byte-order, as Git expects)
        final int entryCount = getEntryCount();
        DirCacheEntry[] entries = new DirCacheEntry[entryCount];
        for (int i = 0; i < entryCount; i++) {
            entries[i] = getEntry(i);
        }
        Arrays.sort(entries, (cacheEntryA, cacheEntryB) -> {
            byte[] pathABytes = cacheEntryA.getPathString().getBytes(StandardCharsets.UTF_8);
            byte[] pathBBytes = cacheEntryB.getPathString().getBytes(StandardCharsets.UTF_8);
            int minLength = Math.min(pathABytes.length, pathBBytes.length);
            for (int i = 0; i < minLength; i++) {
                int diff = (pathABytes[i] & 0xff) - (pathBBytes[i] & 0xff);
                if (diff != 0) {
                    return diff;
                }
            }
            return pathABytes.length - pathBBytes.length;
        });

        // Buffer all content to compute trailing SHA-1 checksum
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.max(8192, entryCount * 128));
        ByteBuffer intBuffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);

        // Header: "DIRC" + version(2) + entry count
        buffer.write(new byte[] { 'D', 'I', 'R', 'C' });
        writeInt(buffer, intBuffer, 2);        // version 2 index
        writeInt(buffer, intBuffer, entryCount);        // number of entries

        // Each entry
        for (DirCacheEntry entry : entries) {
            // --- stat fields (32-bit each) ---

            // ctime seconds, ctime nanoseconds
            writeInt(buffer, intBuffer, 0);     // ctime sec (0 if unknown)
            writeInt(buffer, intBuffer, 0);     // ctime nsec (0 if unknown)

            // mtime seconds, mtime nanoseconds
            writeInt(buffer, intBuffer, 0);     // mtime sec (reuse if you don't track separately)
            writeInt(buffer, intBuffer, 0);     // mtime nsec

            // dev, ino
            writeInt(buffer, intBuffer, 0);
            writeInt(buffer, intBuffer, 0);

            // mode (file type + perms). Prefer entry.getFileMode().getBits()
            int mode = (entry.getFileMode() != null) ? entry.getFileMode().getBits() : READ_WRITE_MODE;
            writeInt(buffer, intBuffer, mode);

            // uid, gid
            writeInt(buffer, intBuffer, 0);
            writeInt(buffer, intBuffer, 0);

            // size (uint32). If unknown/negative -> 0
            long fileLength = entry.getLength();
            writeInt(buffer, intBuffer, (int) (fileLength > 0 ? fileLength : 0));

            // object id (20 bytes, SHA-1)
            ObjectId objectId = entry.getObjectId();
            if (objectId == null) {
                throw new IOException("DirCacheEntry missing ObjectId for " + entry.getPathString());
            }
            byte[] objectIdRaw = new byte[Constants.OBJECT_ID_LENGTH];
            objectId.copyRawTo(objectIdRaw, 0);
            buffer.write(objectIdRaw);

            // flags: [15..12]=stage, [11..0]=name length (or 0xFFF if >= 0xFFF)
            int stage = entry.getStage() & 0x3;
            byte[] pathBytes = entry.getPathString().getBytes(StandardCharsets.UTF_8);
            int nameLength = Math.min(pathBytes.length, 0xFFF);
            int flags = (stage << 12) | (nameLength == 0xFFF ? 0x0FFF : nameLength);
            buffer.write((flags >>> 8) & 0xFF);
            buffer.write(flags & 0xFF);

            buffer.write(pathBytes);
            buffer.write(0); // NUL
            // The entry (from ctime sec to NUL) must make the total entry size a multiple of 8
            int entryLength = 62 /* fixed */ + pathBytes.length + 1; // 62 = 10*4 + 20 + 2
            int padding = (8 - (entryLength % 8)) % 8;
            IntStream.range(0, padding).map(i -> 0).forEach(buffer::write);
        }

        writeTrailingChecksum(outputStream, buffer);
    }

    /**
     * Write the buffered index content to the output stream, followed by the SHA-1 checksum of all preceding bytes.
     *
     * @param outputStream the destination output stream
     * @param buffer       the buffer containing the index content
     * @throws IOException if writing fails
     */
    private static void writeTrailingChecksum(OutputStream outputStream, ByteArrayOutputStream buffer) throws IOException {
        // Trailing checksum (SHA-1 of all preceding bytes)
        byte[] data = buffer.toByteArray();
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            byte[] checksum = messageDigest.digest(data);
            outputStream.write(data);
            outputStream.write(checksum);
            outputStream.flush();
        }
        catch (NoSuchAlgorithmException exception) {
            throw new RuntimeException(exception);
        }
    }

    /**
     * Write a 32-bit integer to the output stream in big-endian byte order.
     *
     * @param stream    the destination output stream
     * @param intBuffer a reusable ByteBuffer for integer conversion
     * @param value     the integer value to write
     * @throws IOException if writing fails
     */
    private static void writeInt(OutputStream stream, ByteBuffer intBuffer, int value) throws IOException {
        intBuffer.clear();
        intBuffer.putInt(value);
        stream.write(intBuffer.array(), 0, 4);
    }
}
