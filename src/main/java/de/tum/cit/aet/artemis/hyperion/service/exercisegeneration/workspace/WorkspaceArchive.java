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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

/**
 * Builds and parses the tar archives used to move the whole workspace in and out of the sandbox in one operation.
 * <p>
 * Using a single archive (rather than a shell command per file) avoids two problems: output truncation that would silently corrupt files larger than the exec capture limit, and
 * shell quoting of model-controlled file paths. The repository trees are packed from the checked-out working copies on disk (not from a string map) so that binary files such as
 * the Gradle wrapper JAR and the executable bit on {@code gradlew} survive the round-trip — without that, a Gradle-based exercise could not be built inside the sandbox.
 */
public final class WorkspaceArchive {

    /** Default permissions: world-readable regular file, and executable for files that are executable in the working copy (e.g. {@code gradlew}). */
    private static final int MODE_FILE = 0644;

    private static final int MODE_EXECUTABLE = 0755;

    /** Per-file cap on read-back. Any single produced source file above this is pathological; 32 MiB matches {@link CollectedReports} and covers any legitimate source file. */
    static final long MAX_FILE_BYTES = 32L * 1024 * 1024;

    /**
     * Whole-archive cap on read-back, so a flood of files (each under the per-file cap) still cannot exhaust node memory when the whole copyOut tar is materialised into Strings.
     */
    static final long MAX_TOTAL_BYTES = 128L * 1024 * 1024;

    private WorkspaceArchive() {
    }

    /**
     * Signals that a {@code copyOut} workspace archive contained a rejected entry: one exceeding the read-back byte caps (a runaway agent writing a multi-GB file must not OOM the
     * node), a symbolic/hard link, or a path that escapes the archive root ({@code ..}/absolute — the produced map feeds a git commit, so an escaping path must never reach the
     * write). The caller treats this as a failed read-back and fails closed rather than materialising the archive.
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
        return new ByteArrayInputStream(build(textFiles, directoryTrees));
    }

    private static byte[] build(Map<String, String> textFiles, Map<String, Path> directoryTrees) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(out)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            long total = 0;
            for (Map.Entry<String, String> entry : textFiles.entrySet()) {
                byte[] content = entry.getValue().getBytes(StandardCharsets.UTF_8);
                total = addToSeedTotal(total, content.length, entry.getKey());
                writeFileEntry(tar, entry.getKey(), content, MODE_FILE);
            }
            for (Map.Entry<String, Path> tree : directoryTrees.entrySet()) {
                total = appendDirectory(tar, tree.getValue(), tree.getKey(), total);
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /**
     * Recursively adds all regular files under {@code root} to the archive under {@code prefix}, skipping the {@code .git} metadata and preserving the executable bit.
     */
    private static long appendDirectory(TarArchiveOutputStream tar, Path root, String prefix, long total) throws IOException {
        if (!Files.isDirectory(root)) {
            return total;
        }
        try (Stream<Path> files = Files.walk(root)) {
            for (Path path : (Iterable<Path>) files.filter(WorkspaceArchive::isRegularFileNoFollow)::iterator) {
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (relative.isEmpty() || relative.equals(".git") || relative.startsWith(".git/") || relative.contains("/.git/")) {
                    continue;
                }
                int mode = Files.isExecutable(path) ? MODE_EXECUTABLE : MODE_FILE;
                String entryName = prefix + "/" + relative;
                long size = Files.size(path);
                if (size > MAX_FILE_BYTES) {
                    throw new RejectedWorkspaceEntryException("Refusing an oversized workspace seed file (" + size + " bytes): " + entryName);
                }
                total = addToSeedTotal(total, size, entryName);
                writeFileEntry(tar, entryName, path, size, mode);
            }
        }
        return total;
    }

    private static boolean isRegularFileNoFollow(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static void writeFileEntry(TarArchiveOutputStream tar, String name, byte[] content, int mode) throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(content.length);
        entry.setMode(mode);
        tar.putArchiveEntry(entry);
        tar.write(content);
        tar.closeArchiveEntry();
    }

    private static void writeFileEntry(TarArchiveOutputStream tar, String name, Path file, long size, int mode) throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(size);
        entry.setMode(mode);
        tar.putArchiveEntry(entry);
        try (InputStream input = Files.newInputStream(file)) {
            input.transferTo(tar);
        }
        tar.closeArchiveEntry();
    }

    private static long addToSeedTotal(long total, long size, String name) {
        long nextTotal = total + size;
        if (nextTotal > MAX_TOTAL_BYTES) {
            throw new RejectedWorkspaceEntryException("Refusing to build the workspace archive: total seed size exceeds " + MAX_TOTAL_BYTES + " bytes at " + name);
        }
        return nextTotal;
    }

    /**
     * Reads the regular text files from a tar archive, returning their UTF-8 content keyed by path with the given prefix removed.
     * <p>
     * Binary files are excluded. A binary entry (a NUL byte or non-UTF-8 content in its leading window — e.g. the {@code gradle/wrapper/gradle-wrapper.jar} a Java
     * PLAIN_GRADLE / GRADLE_GRADLE exercise ships) cannot survive a lossless round-trip through a UTF-8 {@code String}: decoding it substitutes {@code U+FFFD} for every invalid
     * byte
     * sequence, and a downstream re-encode would write that mangled content back and break the build. The agent never edits these scaffolded binaries, so the persist step
     * preserves
     * them byte-exact from the scaffold (and the orphan-sweep never deletes them) — they must therefore not enter the produced text map here.
     *
     * @param tar           the archive to read (closed by the caller)
     * @param prefixToStrip a leading path segment to drop from each entry name (Docker prefixes copied-out entries with the source directory name); may be empty
     * @return the text file contents keyed by their path relative to {@code prefixToStrip} (binary files omitted)
     * @throws IOException if reading the archive fails
     */
    public static Map<String, String> readTar(TarArchiveInputStream tar, String prefixToStrip) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        TarArchiveEntry entry;
        String normalizedPrefix = prefixToStrip.isEmpty() || prefixToStrip.endsWith("/") ? prefixToStrip : prefixToStrip + "/";
        long total = 0;
        while ((entry = tar.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                continue;
            }
            // The copyOut tar is agent-controlled. A symbolic or hard link could redirect a read to a file outside the workspace; reject it (mirrors CollectedReports.read on the
            // verifier's report archive). commons-compress's isFile() is true for FIFO/device entries, so link entries are rejected explicitly by their link flags.
            if (entry.isSymbolicLink() || entry.isLink()) {
                throw new RejectedWorkspaceEntryException("Refusing a linked workspace entry from the copyOut archive: " + entry.getName());
            }
            String name = entry.getName();
            if (name.startsWith("./")) {
                name = name.substring(2);
            }
            if (!normalizedPrefix.isEmpty() && !name.startsWith(normalizedPrefix)) {
                throw new RejectedWorkspaceEntryException("Refusing a workspace entry outside the expected archive prefix: " + entry.getName());
            }
            if (!normalizedPrefix.isEmpty()) {
                name = name.substring(normalizedPrefix.length());
            }
            // No path escape: the produced map is keyed by this path and later written into a git repo, so an absolute or ..-traversing path must never reach the commit.
            if (name.startsWith("/") || name.equals("..") || name.startsWith("../") || name.endsWith("/..") || name.contains("/../")) {
                throw new RejectedWorkspaceEntryException("Refusing a workspace entry whose path escapes the archive root: " + entry.getName());
            }
            if (name.isEmpty() || name.contains(".git/")) {
                continue;
            }
            // The copyOut tar is agent-controlled: bound reads by the header-declared size BEFORE materialising the body so a multi-GB entry is refused, not read into memory and
            // OOM'd. The declared size can understate a hostile body, so re-check the actual byte count after reading and cap the running total across entries as well.
            long declaredSize = entry.getSize();
            if (declaredSize > MAX_FILE_BYTES) {
                throw new RejectedWorkspaceEntryException("Refusing an oversized workspace entry (" + declaredSize + " declared bytes): " + entry.getName());
            }
            byte[] bytes = readEntryBytes(tar, entry.getName());
            total += bytes.length;
            if (total > MAX_TOTAL_BYTES) {
                throw new RejectedWorkspaceEntryException("Refusing to read the workspace archive: total size exceeds " + MAX_TOTAL_BYTES + " bytes");
            }
            // A binary file cannot be represented losslessly as a String; drop it so persist preserves the scaffolded original byte-exact instead of writing a mangled re-encode.
            if (BinaryContent.isBinary(bytes)) {
                continue;
            }
            result.put(name, new String(bytes, StandardCharsets.UTF_8));
        }
        return result;
    }

    private static byte[] readEntryBytes(TarArchiveInputStream tar, String name) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = tar.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            if (out.size() > MAX_FILE_BYTES) {
                throw new RejectedWorkspaceEntryException("Refusing an oversized workspace entry (" + out.size() + " bytes): " + name);
            }
        }
        return out.toByteArray();
    }
}
