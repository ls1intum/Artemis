package de.tum.cit.aet.artemis.localvc.service.git;

import static de.tum.cit.aet.artemis.localvc.service.git.InMemoryDirCache.DIRECTORY_EXECUTE_MODE;
import static de.tum.cit.aet.artemis.localvc.service.git.InMemoryDirCache.EXECUTE_MODE;
import static de.tum.cit.aet.artemis.localvc.service.git.InMemoryDirCache.READ_WRITE_MODE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.programming.domain.Repository;

/**
 * Builds a checkout-ready Git repository as a single ZIP archive, straight from a bare repository on disk.
 * <p>
 * The ZIP contains the working tree at the root and a synthetic {@code .git/}
 * directory with minimal refs, config, and a packed object store. Nothing is checked out.
 * <p>
 * Thread-safety: all methods are stateless and thread-safe.
 */
public class InMemoryRepositoryBuilder {

    private static final Logger log = LoggerFactory.getLogger(InMemoryRepositoryBuilder.class);

    /**
     * Creates an in-memory ZIP that, once extracted, is a usable non-bare Git repo.
     * The archive contains:
     * <ul>
     * <li>All working tree files of the repository's branch</li>
     * <li>{@code .git/HEAD}, {@code .git/refs/...}, {@code .git/config}</li>
     * <li>{@code .git/objects/pack/pack-*.pack} and matching {@code .idx}</li>
     * <li>A serialized Git index matching the working tree</li>
     * </ul>
     *
     * @param bareRepository the bare repository on disk to export
     * @return ZIP file bytes
     * @throws IOException if reading the repository or ZIP serialization fails
     */
    public static byte[] buildZip(Repository bareRepository) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        writeZip(bareRepository, outputStream);
        return outputStream.toByteArray();
    }

    /**
     * Writes the same archive as {@link #buildZip(Repository)} to the given stream, so callers with a destination in hand
     * (a file in an export directory, an HTTP response) do not have to hold the whole archive in memory first.
     *
     * <p>
     * Objects are read straight from the bare repository: it is a local repository on the same file system, so there is
     * nothing to be gained from copying it into an in-memory clone before packing it.
     *
     * <p>
     * The given stream stays open, so the caller keeps ownership of it.
     *
     * @param bareRepository the bare repository on disk to export
     * @param outputStream   the stream the ZIP is written to
     * @throws IOException if reading the repository or ZIP serialization fails
     */
    public static void writeZip(Repository bareRepository, OutputStream outputStream) throws IOException {
        String branch = bareRepository.getBranch();
        ObjectId commitId = resolveExportedCommit(bareRepository, branch);

        // Close-shielded so that closing the ZIP stream releases its deflater without also closing the caller's stream.
        try (ZipArchiveOutputStream zipOutputStream = new ZipArchiveOutputStream(CloseShieldOutputStream.wrap(outputStream)); RevWalk rw = new RevWalk(bareRepository)) {

            zipOutputStream.setMethod(ZipEntry.DEFLATED);
            zipOutputStream.setLevel(Deflater.BEST_COMPRESSION);

            // keep a set of created directory entries to avoid duplicates
            Set<String> createdDirs = new HashSet<>();
            // Create .git scaffolding FIRST (deduped)
            writeGitScaffold(zipOutputStream, createdDirs);

            RevCommit commit = rw.parseCommit(commitId);
            RevTree tree = commit.getTree();

            // In-memory index
            InMemoryDirCache dirCache = new InMemoryDirCache(null, null);         // no filesystem
            DirCacheBuilder dirCacheBuilder = dirCache.builder();

            // 2a) Materialize working tree files directly from objects (no checkout to disk)
            try (TreeWalk treeWalk = new TreeWalk(bareRepository)) {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);
                while (treeWalk.next()) {
                    FileMode mode = treeWalk.getFileMode(0);
                    String path = treeWalk.getPathString();
                    log.debug("Write file: {} ({})", path, mode);
                    ObjectId blobId = treeWalk.getObjectId(0);

                    if (mode == FileMode.GITLINK) {
                        continue; // skip submodules in index
                    }

                    DirCacheEntry dirCacheEntry = new DirCacheEntry(path);
                    dirCacheEntry.setFileMode(mode);              // preserves executable bit
                    dirCacheEntry.setObjectId(blobId);
                    dirCacheEntry.setLength(-1);                  // let Git verify later
                    dirCacheBuilder.add(dirCacheEntry);

                    if (mode == FileMode.REGULAR_FILE || mode == FileMode.EXECUTABLE_FILE) {
                        boolean executable = mode == FileMode.EXECUTABLE_FILE;
                        ensureParentDirs(zipOutputStream, createdDirs, path);
                        ZipArchiveEntry zipEntry = new ZipArchiveEntry(path.replace('\\', '/'));
                        zipEntry.setUnixMode(executable ? EXECUTE_MODE : READ_WRITE_MODE); // -rwxr-xr-x or -rw-r--r--
                        zipOutputStream.putArchiveEntry(zipEntry);
                        bareRepository.open(treeWalk.getObjectId(0), Constants.OBJ_BLOB).copyTo(zipOutputStream);
                        zipOutputStream.closeArchiveEntry();
                    }
                    else if (mode == FileMode.SYMLINK) {
                        // Materialize symlink as a plain text file containing the link target.
                        ensureParentDirs(zipOutputStream, createdDirs, path);
                        ZipArchiveEntry zipEntry = new ZipArchiveEntry(path.replace('\\', '/'));
                        // Zip has no native symlink type; write target as text (or skip)
                        zipEntry.setUnixMode(READ_WRITE_MODE);
                        zipOutputStream.putArchiveEntry(zipEntry);
                        bareRepository.open(treeWalk.getObjectId(0), Constants.OBJ_BLOB).copyTo(zipOutputStream);
                        zipOutputStream.closeArchiveEntry();
                    }
                }
                // NOW finalize and serialize the index once
                dirCacheBuilder.finish();
                try (ByteArrayOutputStream indexOut = new ByteArrayOutputStream()) {
                    dirCache.writeTo(indexOut);
                    putGitBytes(zipOutputStream, createdDirs, "index", indexOut.toByteArray());
                }
            }

            createGitIndex(bareRepository, commitId, zipOutputStream, createdDirs);

            // refs + HEAD + config
            putGitBytes(zipOutputStream, createdDirs, "HEAD", ("ref: refs/heads/" + branch + "\n").getBytes(StandardCharsets.UTF_8));
            putGitBytes(zipOutputStream, createdDirs, "refs/heads/" + branch, (commitId.name() + "\n").getBytes(StandardCharsets.UTF_8));

            writeGitConfig(zipOutputStream, createdDirs);

            zipOutputStream.finish(); // finalize central directory
        }
    }

    /**
     * Resolves the commit to export, preferring the repository's own branch over {@code HEAD} so that an exercise using a
     * branch other than the configured default is exported correctly.
     *
     * @param bareRepository the bare repository to read from
     * @param branch         the branch reported by the repository
     * @return the tip commit of that branch
     * @throws IOException if the repository cannot be read, or has no commits at all because neither the branch nor
     *                         {@code HEAD} resolves
     */
    private static ObjectId resolveExportedCommit(Repository bareRepository, String branch) throws IOException {
        ObjectId commitId = bareRepository.resolve(Constants.R_HEADS + branch);
        if (commitId == null) {
            commitId = bareRepository.resolve(Constants.HEAD);
        }
        if (commitId == null) {
            throw new IOException("Cannot export the repository " + bareRepository.getRemoteRepositoryUri() + " because neither branch " + branch + " nor HEAD resolves");
        }
        return commitId;
    }

    /**
     * Writes a minimal {@code .git/config}.
     *
     * <p>
     * Deliberately without a remote: the only URL available here is the server's internal path to the bare repository,
     * which is both unusable on the machine that extracts the archive and something the archive should not disclose.
     *
     * @param zipOutputStream open ZIP stream to receive the entry
     * @param createdDirs     set used to deduplicate directory entries
     * @throws IOException if the ZIP entry cannot be written
     */
    private static void writeGitConfig(ZipArchiveOutputStream zipOutputStream, Set<String> createdDirs) throws IOException {
        String config = """
                [core]
                    repositoryformatversion = 0
                    filemode = true
                    bare = false
                    logallrefupdates = true
                """;
        putGitBytes(zipOutputStream, createdDirs, "config", config.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Packs all reachable objects from {@code commitId} into a single {@code pack-*.pack}
     * with matching {@code .idx} and writes both under {@code .git/objects/pack/}.
     * The pack file name is computed as the SHA-1 of its content, as in standard Git.
     *
     * @param bareRepository  the bare repository the objects are read from
     * @param commitId        tip commit that defines reachability for the pack
     * @param zipOutputStream open ZIP stream to receive pack and index entries
     * @param createdDirs     set used to deduplicate directory entries
     * @throws IOException if pack/index creation or ZIP writes fail
     */
    private static void createGitIndex(Repository bareRepository, ObjectId commitId, ZipArchiveOutputStream zipOutputStream, Set<String> createdDirs) throws IOException {
        // Create pack + index
        byte[] packBytes;
        byte[] idxBytes;
        String packHashHex;
        try (ObjectReader reader = bareRepository.newObjectReader();
                ObjectWalk objectWalk = new ObjectWalk(reader);
                PackWriter packWriter = new PackWriter(new PackConfig(bareRepository), reader)) {

            // Mark the tip as the starting point for traversal
            RevCommit tip = objectWalk.parseCommit(commitId);
            objectWalk.markStart(tip);

            // This drives the traversal from `objectWalk` and prevents null ids inside preparePack
            packWriter.preparePack(NullProgressMonitor.INSTANCE, objectWalk, Set.of(commitId), PackWriter.NONE, PackWriter.NONE);

            // Write .pack and derive its canonical name from JGit
            ByteArrayOutputStream packOut = new ByteArrayOutputStream();
            packWriter.writePack(NullProgressMonitor.INSTANCE, NullProgressMonitor.INSTANCE, packOut);
            packBytes = packOut.toByteArray();
            packHashHex = packWriter.computeName().name();

            // Write .idx
            ByteArrayOutputStream idxOut = new ByteArrayOutputStream();
            packWriter.writeIndex(idxOut);
            idxBytes = idxOut.toByteArray();
        }

        // Place pack + idx into .git/objects/pack/
        putGitBytes(zipOutputStream, createdDirs, "objects/pack/pack-" + packHashHex + ".pack", packBytes);
        putGitBytes(zipOutputStream, createdDirs, "objects/pack/pack-" + packHashHex + ".idx", idxBytes);
    }

    // ---- Helpers ----------------------------------------------------------------

    /**
     * Adds the minimal directory skeleton for {@code .git/} into the ZIP.
     * Idempotent: duplicate directories are suppressed via {@code createdDirs}.
     *
     * @param zipOutputStream open ZIP stream
     * @param createdDirs     directory de-duplication set
     */
    private static void writeGitScaffold(ZipArchiveOutputStream zipOutputStream, Set<String> createdDirs) {
        mkdir(zipOutputStream, createdDirs, ".git/");
        mkdir(zipOutputStream, createdDirs, ".git/objects/");
        mkdir(zipOutputStream, createdDirs, ".git/objects/pack/");
        mkdir(zipOutputStream, createdDirs, ".git/refs/");
        mkdir(zipOutputStream, createdDirs, ".git/refs/heads/");
    }

    /**
     * Ensures that all parent directories of {@code path} exist as ZIP directory
     * entries (normalized with forward slashes). Safe to call repeatedly.
     *
     * @param zipOutputStream open ZIP stream
     * @param createdDirs     directory de-duplication set
     * @param path            file path whose parent directories should be materialized
     */
    private static void ensureParentDirs(ZipArchiveOutputStream zipOutputStream, Set<String> createdDirs, String path) {
        String norm = path.replace('\\', '/');
        int last = norm.lastIndexOf('/');
        if (last < 0) {
            return; // no parent
        }
        String parent = norm.substring(0, last); // up to (but not including) the leaf
        int i = 0;
        while ((i = parent.indexOf('/', i)) != -1) {
            String dir = parent.substring(0, i + 1);
            mkdir(zipOutputStream, createdDirs, dir);  // writes "dir/" only
            i++;
        }
        // also ensure the full parent dir itself (if not already ending with '/')
        mkdir(zipOutputStream, createdDirs, parent.endsWith("/") ? parent : parent + "/");
    }

    /**
     * Adds a ZIP directory entry with POSIX execute bits (drwxr-xr-x) if it does not
     * already exist in {@code createdDirs}. Logs and skips on IO errors.
     *
     * @param zipOutputStream open ZIP stream
     * @param createdDirs     directory de-duplication set
     * @param dir             directory path (with or without trailing slash)
     */
    private static void mkdir(ZipArchiveOutputStream zipOutputStream, Set<String> createdDirs, String dir) {
        if (!dir.endsWith("/")) {
            dir = dir + "/";
        }
        if (!createdDirs.add(dir)) { // skip duplicates
            return;
        }
        // avoid duplicate entries
        try {
            ZipArchiveEntry zipEntry = new ZipArchiveEntry(dir);
            log.debug("Add dir to zip: {}", dir);
            zipEntry.setUnixMode(DIRECTORY_EXECUTE_MODE);         // drwxr-xr-x
            zipOutputStream.putArchiveEntry(zipEntry);
            zipOutputStream.closeArchiveEntry();
        }
        catch (IOException ex) {
            log.warn("Could not add directory to zip: {}", dir, ex);
        }
    }

    /**
     * Writes a file under {@code .git/} at {@code relPath} with the given bytes.
     * Parent directories are created if missing.
     *
     * @param zipOutputStream open ZIP stream
     * @param createdDirs     directory de-duplication set
     * @param relPath         path relative to {@code .git/} (e.g., {@code refs/heads/main})
     * @param bytes           file content
     * @throws IOException if the ZIP entry cannot be written
     */
    private static void putGitBytes(ZipArchiveOutputStream zipOutputStream, Set<String> createdDirs, String relPath, byte[] bytes) throws IOException {
        String full = ".git/" + relPath;
        ensureParentDirs(zipOutputStream, createdDirs, full);  // parent dirs only
        ZipArchiveEntry zipEntry = new ZipArchiveEntry(full);
        zipOutputStream.putArchiveEntry(zipEntry);
        zipOutputStream.write(bytes);
        zipOutputStream.closeArchiveEntry();
    }

    /**
     * Converts a byte array to lowercase hexadecimal without separators.
     *
     * @param bytes input bytes
     * @return hex string, two characters per byte
     */
    private static String toHex(byte[] bytes) {
        StringBuilder stringBuilder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            stringBuilder.append("%02x".formatted(b));
        }
        return stringBuilder.toString();
    }

}
