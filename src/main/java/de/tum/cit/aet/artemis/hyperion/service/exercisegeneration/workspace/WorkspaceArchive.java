package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
     * Signals that a {@code copyOut} workspace archive contained an entry (or a total) exceeding the read-back byte caps. The caller treats this as a failed read-back and fails
     * closed rather than materialising an unbounded String — a compromised or runaway agent writing a multi-GB file must not OOM the node.
     */
    public static final class OversizedWorkspaceEntryException extends RuntimeException {

        OversizedWorkspaceEntryException(String message) {
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
            for (Map.Entry<String, String> entry : textFiles.entrySet()) {
                writeFileEntry(tar, entry.getKey(), entry.getValue().getBytes(StandardCharsets.UTF_8), MODE_FILE);
            }
            for (Map.Entry<String, Path> tree : directoryTrees.entrySet()) {
                appendDirectory(tar, tree.getValue(), tree.getKey());
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
    private static void appendDirectory(TarArchiveOutputStream tar, Path root, String prefix) throws IOException {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> files = Files.walk(root)) {
            for (Path path : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (relative.isEmpty() || relative.equals(".git") || relative.startsWith(".git/") || relative.contains("/.git/")) {
                    continue;
                }
                int mode = Files.isExecutable(path) ? MODE_EXECUTABLE : MODE_FILE;
                writeFileEntry(tar, prefix + "/" + relative, Files.readAllBytes(path), mode);
            }
        }
    }

    private static void writeFileEntry(TarArchiveOutputStream tar, String name, byte[] content, int mode) throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(content.length);
        entry.setMode(mode);
        tar.putArchiveEntry(entry);
        tar.write(content);
        tar.closeArchiveEntry();
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
    static Map<String, String> readTar(TarArchiveInputStream tar, String prefixToStrip) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        TarArchiveEntry entry;
        String normalizedPrefix = prefixToStrip.isEmpty() || prefixToStrip.endsWith("/") ? prefixToStrip : prefixToStrip + "/";
        long total = 0;
        while ((entry = tar.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                continue;
            }
            String name = entry.getName();
            if (name.startsWith("./")) {
                name = name.substring(2);
            }
            if (!normalizedPrefix.isEmpty() && name.startsWith(normalizedPrefix)) {
                name = name.substring(normalizedPrefix.length());
            }
            if (name.isEmpty() || name.contains(".git/")) {
                continue;
            }
            // The copyOut tar is agent-controlled: bound reads by the header-declared size BEFORE materialising the body so a multi-GB entry is refused, not read into memory and
            // OOM'd. The declared size can understate a hostile body, so re-check the actual byte count after reading and cap the running total across entries as well.
            long declaredSize = entry.getSize();
            if (declaredSize > MAX_FILE_BYTES) {
                throw new OversizedWorkspaceEntryException("Refusing an oversized workspace entry (" + declaredSize + " declared bytes): " + entry.getName());
            }
            byte[] bytes = tar.readAllBytes();
            if (bytes.length > MAX_FILE_BYTES) {
                throw new OversizedWorkspaceEntryException("Refusing an oversized workspace entry (" + bytes.length + " bytes): " + entry.getName());
            }
            total += bytes.length;
            if (total > MAX_TOTAL_BYTES) {
                throw new OversizedWorkspaceEntryException("Refusing to read the workspace archive: total size exceeds " + MAX_TOTAL_BYTES + " bytes");
            }
            // A binary file cannot be represented losslessly as a String; drop it so persist preserves the scaffolded original byte-exact instead of writing a mangled re-encode.
            if (BinaryContent.isBinary(bytes)) {
                continue;
            }
            result.put(name, new String(bytes, StandardCharsets.UTF_8));
        }
        return result;
    }
}
