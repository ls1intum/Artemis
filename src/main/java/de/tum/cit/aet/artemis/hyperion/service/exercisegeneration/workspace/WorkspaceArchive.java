package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import de.tum.cit.aet.artemis.hyperion.service.HyperionSecretMaterialPolicy;

/**
 * Builds and parses the tar archives used to move the whole workspace in and out of the sandbox in one operation.
 * <p>
 * A single archive rather than a shell command per file avoids two problems: output truncation that silently corrupts files larger than the exec capture limit, and shell
 * quoting of model-controlled paths. The repository trees are packed from the checked-out working copies on disk rather than a string map so that binary files such as the
 * Gradle wrapper JAR, and the executable bit on {@code gradlew}, survive the round-trip; without that a Gradle-based exercise cannot be built inside the sandbox.
 */
public final class WorkspaceArchive {

    /** Default permissions: world-readable regular file, and executable for files that are executable in the working copy (e.g. {@code gradlew}). */
    private static final int MODE_FILE = 0644;

    private static final int MODE_EXECUTABLE = 0755;

    private static final long WORKSPACE_CONTENT_LIMIT_BYTES = 30L * 1024 * 1024;

    private static final int MAX_ARCHIVE_BYTES = 32 * 1024 * 1024;

    private static final int MAX_ARCHIVE_ENTRIES = 10_000;

    private static final HyperionSecretMaterialPolicy SECRET_MATERIAL_POLICY = new HyperionSecretMaterialPolicy();

    private static final Path ARCHIVE_ROOT = Path.of("/workspace-archive");

    /** Leaves room for tar headers below the relay's 32 MiB payload limit. */
    static final long MAX_FILE_BYTES = WORKSPACE_CONTENT_LIMIT_BYTES;

    /**
     * Whole-archive cap on read-back, so a flood of files (each under the per-file cap) still cannot exhaust node memory when the whole copyOut tar is materialised into Strings.
     */
    static final long MAX_TOTAL_BYTES = WORKSPACE_CONTENT_LIMIT_BYTES;

    private WorkspaceArchive() {
    }

    /**
     * Signals that a {@code copyOut} archive contained a rejected entry: over the read-back byte caps, a symbolic or hard link, or a path escaping the archive root. The
     * produced map feeds a git commit, so an escaping path must never reach the write, and a runaway agent's multi-GB file must not be materialised at all. The caller treats
     * this as a failed read-back and fails closed.
     */
    public static final class RejectedWorkspaceEntryException extends RuntimeException {

        RejectedWorkspaceEntryException(String message) {
            super(message);
        }
    }

    /**
     * Builds a single workspace archive combining literal text files (e.g. the problem statement and the {@code verify.sh} helper) with the on-disk repository working trees.
     *
     * @param textFiles      literal files keyed by archive-relative path (written as UTF-8)
     * @param directoryTrees working-copy directories keyed by the archive-relative prefix to place them under (e.g. {@code solution} -> the checked-out solution repo)
     * @return a stream over the resulting tar archive
     */
    public static InputStream buildWorkspaceTarStream(Map<String, String> textFiles, Map<String, Path> directoryTrees) {
        return buildWorkspaceTarStream(textFiles, directoryTrees, Set.of());
    }

    static InputStream buildWorkspaceTarStream(Map<String, String> textFiles, Map<String, Path> directoryTrees, Set<String> executableTextFiles) {
        return new ByteArrayInputStream(build(textFiles, directoryTrees, executableTextFiles));
    }

    static InputStream buildFilesTarStream(Map<String, String> textFiles, Map<String, byte[]> binaryFiles, Set<String> executableFiles) {
        BoundedByteArrayOutputStream out = new BoundedByteArrayOutputStream(MAX_ARCHIVE_BYTES);
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(out)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            long total = 0;
            int[] entryCount = { 0 };
            for (Map.Entry<String, String> entry : textFiles.entrySet()) {
                incrementEntryCount(entryCount);
                byte[] content = entry.getValue().getBytes(StandardCharsets.UTF_8);
                rejectSecretMaterial(entry.getKey(), content, HyperionSecretMaterialPolicy.Origin.WORKSPACE_ARCHIVE);
                total = addToSeedTotal(total, content.length, entry.getKey());
                writeFileEntry(tar, entry.getKey(), content, executableFiles.contains(entry.getKey()) ? MODE_EXECUTABLE : MODE_FILE);
            }
            for (Map.Entry<String, byte[]> entry : binaryFiles.entrySet()) {
                incrementEntryCount(entryCount);
                rejectSecretMaterial(entry.getKey(), entry.getValue(), HyperionSecretMaterialPolicy.Origin.WORKSPACE_ARCHIVE);
                total = addToSeedTotal(total, entry.getValue().length, entry.getKey());
                writeFileEntry(tar, entry.getKey(), entry.getValue(), executableFiles.contains(entry.getKey()) ? MODE_EXECUTABLE : MODE_FILE);
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ByteArrayInputStream(out.toByteArray());
    }

    private static byte[] build(Map<String, String> textFiles, Map<String, Path> directoryTrees, Set<String> executableTextFiles) {
        BoundedByteArrayOutputStream out = new BoundedByteArrayOutputStream(MAX_ARCHIVE_BYTES);
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(out)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            long total = 0;
            int[] entryCount = { 0 };
            for (Map.Entry<String, String> entry : textFiles.entrySet()) {
                incrementEntryCount(entryCount);
                byte[] content = entry.getValue().getBytes(StandardCharsets.UTF_8);
                rejectSecretMaterial(entry.getKey(), content, HyperionSecretMaterialPolicy.Origin.WORKSPACE_ARCHIVE);
                total = addToSeedTotal(total, content.length, entry.getKey());
                writeFileEntry(tar, entry.getKey(), content, executableTextFiles.contains(entry.getKey()) ? MODE_EXECUTABLE : MODE_FILE);
            }
            for (Map.Entry<String, Path> tree : directoryTrees.entrySet()) {
                total = appendDirectory(tar, tree.getValue(), tree.getKey(), total, entryCount);
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static long appendDirectory(TarArchiveOutputStream tar, Path root, String prefix, long total, int[] entryCount) throws IOException {
        if (!Files.isDirectory(root)) {
            return total;
        }
        try (Stream<Path> files = Files.walk(root)) {
            for (Path path : (Iterable<Path>) files.filter(WorkspaceArchive::isRegularFileNoFollow)::iterator) {
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (relative.isEmpty() || relative.equals(".git") || relative.startsWith(".git/") || relative.contains("/.git/")) {
                    continue;
                }
                incrementEntryCount(entryCount);
                int mode = Files.isExecutable(path) ? MODE_EXECUTABLE : MODE_FILE;
                String entryName = prefix + "/" + relative;
                byte[] content = Files.readAllBytes(path);
                if (content.length > MAX_FILE_BYTES) {
                    throw new RejectedWorkspaceEntryException("Refusing an oversized workspace seed file (" + content.length + " bytes): " + safePath(entryName));
                }
                rejectSecretMaterial(entryName, content, HyperionSecretMaterialPolicy.Origin.WORKSPACE_ARCHIVE);
                total = addToSeedTotal(total, content.length, entryName);
                writeFileEntry(tar, entryName, content, mode);
            }
        }
        return total;
    }

    private static void rejectSecretMaterial(String logicalPath, byte[] content, HyperionSecretMaterialPolicy.Origin origin) {
        HyperionSecretMaterialPolicy.Assessment assessment = SECRET_MATERIAL_POLICY.assess(logicalPath, content, origin);
        if (!assessment.isSafe()) {
            String description = assessment.category().orElseThrow() == HyperionSecretMaterialPolicy.Category.CREDENTIAL_FILE ? "a credential file" : "credential material";
            throw new RejectedWorkspaceEntryException("Refusing to send " + description + " [" + assessment.category().orElseThrow() + "] to Hyperion at " + assessment.safePath());
        }
    }

    private static void incrementEntryCount(int[] entryCount) {
        if (++entryCount[0] > MAX_ARCHIVE_ENTRIES) {
            throw new RejectedWorkspaceEntryException("Refusing a workspace archive with more than " + MAX_ARCHIVE_ENTRIES + " entries");
        }
    }

    private static boolean isRegularFileNoFollow(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static void writeFileEntry(TarArchiveOutputStream tar, String name, byte[] content, int mode) throws IOException {
        validateSeedEntryName(name);
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(content.length);
        entry.setMode(mode);
        tar.putArchiveEntry(entry);
        tar.write(content);
        tar.closeArchiveEntry();
    }

    private static void validateSeedEntryName(String name) {
        if (name == null || name.isEmpty() || name.contains("\\")) {
            throw new RejectedWorkspaceEntryException("Refusing a non-canonical workspace seed path: " + safePath(name));
        }
        Path resolvedPath = ARCHIVE_ROOT.resolve(name).normalize();
        String canonicalName = resolvedPath.startsWith(ARCHIVE_ROOT) ? ARCHIVE_ROOT.relativize(resolvedPath).toString().replace('\\', '/') : "";
        if (!resolvedPath.startsWith(ARCHIVE_ROOT) || !canonicalName.equals(name)) {
            throw new RejectedWorkspaceEntryException("Refusing a workspace seed path outside the archive root: " + safePath(name));
        }
        if (name.equals(".git") || name.startsWith(".git/") || name.endsWith("/.git") || name.contains("/.git/")) {
            throw new RejectedWorkspaceEntryException("Refusing workspace Git metadata: " + safePath(name));
        }
    }

    private static long addToSeedTotal(long total, long size, String name) {
        long nextTotal = total + size;
        if (nextTotal > MAX_TOTAL_BYTES) {
            throw new RejectedWorkspaceEntryException("Refusing to build the workspace archive: total seed size exceeds " + MAX_TOTAL_BYTES + " bytes at " + safePath(name));
        }
        return nextTotal;
    }

    /**
     * Reads the regular text files from a tar archive, returning their UTF-8 content keyed by path with the given prefix removed.
     * <p>
     * Binary files are excluded because a UTF-8 {@code String} round-trip would corrupt them. Persistence preserves scaffolded binaries byte-for-byte instead of placing them in
     * the produced text map.
     *
     * @param tar           the archive to read (closed by the caller)
     * @param prefixToStrip a leading path segment to drop from each entry name (Docker prefixes copied-out entries with the source directory name); may be empty
     * @return the text file contents keyed by their path relative to {@code prefixToStrip} (binary files omitted)
     * @throws IOException if reading the archive fails
     */
    public static Map<String, String> readTar(TarArchiveInputStream tar, String prefixToStrip) throws IOException {
        return readTarContents(tar, prefixToStrip).textFiles();
    }

    static ArchiveContents readTarContents(TarArchiveInputStream tar, String prefixToStrip) throws IOException {
        Map<String, String> textFiles = new LinkedHashMap<>();
        Map<String, String> binaryDigests = new LinkedHashMap<>();
        Set<String> executableFiles = new LinkedHashSet<>();
        TarArchiveEntry entry;
        String normalizedPrefix = prefixToStrip.isEmpty() || prefixToStrip.endsWith("/") ? prefixToStrip : prefixToStrip + "/";
        long total = 0;
        int[] entryCount = { 0 };
        while ((entry = tar.getNextEntry()) != null) {
            incrementEntryCount(entryCount);
            if (entry.isDirectory()) {
                continue;
            }
            if (!entry.isFile() || entry.isSymbolicLink() || entry.isLink() || entry.isFIFO() || entry.isCharacterDevice() || entry.isBlockDevice() || entry.isSparse()) {
                throw new RejectedWorkspaceEntryException("Refusing a non-regular workspace entry from the copyOut archive: " + safePath(entry.getName()));
            }
            String name = entry.getName();
            if (name.startsWith("./")) {
                name = name.substring(2);
            }
            if (!normalizedPrefix.isEmpty() && !name.startsWith(normalizedPrefix)) {
                throw new RejectedWorkspaceEntryException("Refusing a workspace entry outside the expected archive prefix: " + safePath(entry.getName()));
            }
            if (!normalizedPrefix.isEmpty()) {
                name = name.substring(normalizedPrefix.length());
            }
            Path resolvedPath = ARCHIVE_ROOT.resolve(name).normalize();
            if (!resolvedPath.startsWith(ARCHIVE_ROOT)) {
                throw new RejectedWorkspaceEntryException("Refusing a workspace entry whose path escapes the archive root: " + safePath(entry.getName()));
            }
            if (name.contains("\\")) {
                throw new RejectedWorkspaceEntryException("Refusing a workspace entry with a non-portable repository path: " + safePath(entry.getName()));
            }
            String canonicalName = ARCHIVE_ROOT.relativize(resolvedPath).toString().replace('\\', '/');
            if (!canonicalName.equals(name)) {
                throw new RejectedWorkspaceEntryException("Refusing a workspace entry with a non-canonical path: " + safePath(entry.getName()));
            }
            name = canonicalName;
            if (name.equals(".git") || name.startsWith(".git/") || name.endsWith("/.git") || name.contains("/.git/")) {
                throw new RejectedWorkspaceEntryException("Refusing workspace Git metadata: " + safePath(entry.getName()));
            }
            if (name.isEmpty()) {
                continue;
            }
            // The copyOut tar is agent-controlled: bound reads by the header-declared size BEFORE materialising the body so a multi-GB entry is refused, not read into memory and
            // OOM'd. The declared size can understate a hostile body, so re-check the actual byte count after reading and cap the running total across entries as well.
            long declaredSize = entry.getSize();
            if (declaredSize > MAX_FILE_BYTES) {
                throw new RejectedWorkspaceEntryException("Refusing an oversized workspace entry (" + declaredSize + " declared bytes): " + safePath(entry.getName()));
            }
            byte[] bytes = readEntryBytes(tar, entry.getName());
            total += bytes.length;
            if (total > MAX_TOTAL_BYTES) {
                throw new RejectedWorkspaceEntryException("Refusing to read the workspace archive: total size exceeds " + MAX_TOTAL_BYTES + " bytes");
            }
            rejectSecretMaterial(name, bytes, HyperionSecretMaterialPolicy.Origin.GENERATED_CANDIDATE);
            if (BinaryContent.isBinary(bytes)) {
                binaryDigests.put(name, sha256(bytes));
            }
            else {
                textFiles.put(name, new String(bytes, StandardCharsets.UTF_8));
            }
            if ((entry.getMode() & 0111) != 0) {
                executableFiles.add(name);
            }
        }
        return new ArchiveContents(textFiles, binaryDigests, executableFiles);
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    record ArchiveContents(Map<String, String> textFiles, Map<String, String> binaryDigests, Set<String> executableFiles) {
    }

    private static final class BoundedByteArrayOutputStream extends ByteArrayOutputStream {

        private final int maxBytes;

        private BoundedByteArrayOutputStream(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) {
            checkLimit(length);
            super.write(bytes, offset, length);
        }

        @Override
        public synchronized void write(int value) {
            checkLimit(1);
            super.write(value);
        }

        private void checkLimit(int bytesToAdd) {
            if (count > maxBytes - bytesToAdd) {
                throw new RejectedWorkspaceEntryException("Refusing a workspace archive larger than " + maxBytes + " bytes");
            }
        }
    }

    private static byte[] readEntryBytes(TarArchiveInputStream tar, String name) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = tar.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            if (out.size() > MAX_FILE_BYTES) {
                throw new RejectedWorkspaceEntryException("Refusing an oversized workspace entry (" + out.size() + " bytes): " + safePath(name));
            }
        }
        return out.toByteArray();
    }

    private static String safePath(String logicalPath) {
        return SECRET_MATERIAL_POLICY.assess(logicalPath, new byte[0], HyperionSecretMaterialPolicy.Origin.WORKSPACE_ARCHIVE).safePath();
    }
}
