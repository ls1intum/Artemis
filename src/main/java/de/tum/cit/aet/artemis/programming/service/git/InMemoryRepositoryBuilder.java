package de.tum.cit.aet.artemis.programming.service.git;

import static de.tum.cit.aet.artemis.programming.service.git.InMemoryDirCache.DIRECTORY_EXECUTE_MODE;
import static de.tum.cit.aet.artemis.programming.service.git.InMemoryDirCache.EXECUTE_MODE;
import static de.tum.cit.aet.artemis.programming.service.git.InMemoryDirCache.READ_WRITE_MODE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.programming.domain.Repository;

/**
 * Builds an in-memory, checkout-ready Git repository as a single ZIP archive.
 * <p>
 * The ZIP contains the working tree at the root and a synthetic {@code .git/}
 * directory with minimal refs, config, and a packed object store. No disk IO is used.
 * <p>
 * Thread-safety: all methods are stateless and thread-safe.
 */
public class InMemoryRepositoryBuilder {

    private static final Logger log = LoggerFactory.getLogger(InMemoryRepositoryBuilder.class);

    /**
     * Creates an in-memory ZIP that, once extracted, is a usable non-bare Git repo.
     * The archive contains:
     * <ul>
     * <li>All working tree files of {@code repository.branch} at {@code origin}</li>
     * <li>{@code .git/HEAD}, {@code .git/refs/...}, {@code .git/config}</li>
     * <li>{@code .git/objects/pack/pack-*.pack} and matching {@code .idx}</li>
     * <li>A serialized Git index matching the working tree</li>
     * </ul>
     *
     * @param repository logical repository descriptor providing remote URI and branch
     * @return ZIP file bytes
     * @throws IllegalArgumentException if the requested branch does not exist on the remote
     * @throws IOException              if fetching or ZIP serialization fails
     */
    public static byte[] buildZip(Repository repository) throws IOException {

        URI remoteUri = repository.getLocalPath().toUri();

        // 1) Create an in-memory bare repository and fetch from the remote
        InMemoryRepository repo;

        try {
            repo = new InMemoryRepository.Builder().setRepositoryDescription(new DfsRepositoryDescription("inmem")).setFS(FS.DETECTED).build();
        }
        catch (IOException e) {
            throw new IOException("Failed to build in-memory repository", e);
        }

        // Configure "origin" (so we can also write it into .git/config later)
        StoredConfig storedConfig = repo.getConfig();
        storedConfig.setString("remote", "origin", "url", remoteUri.toString());
        storedConfig.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
        storedConfig.save();

        log.debug("Fetching from {}", remoteUri);
        // Fetch: all branches
        try (Transport transport = Transport.open(repo, remoteUri.toString())) {
            transport.fetch(NullProgressMonitor.INSTANCE, List.of(new RefSpec("+refs/heads/*:refs/remotes/origin/*")));
        }
        catch (Exception e) {
            throw new IOException("Failed to fetch from remote URI: " + remoteUri, e);
        }

        // Resolve the commit for the requested branch (default "main")
        String branch = repository.getBranch();
        String fullHead = "refs/remotes/origin/" + branch;
        ObjectId commitId = repo.resolve(fullHead);
        if (commitId == null) {
            throw new IllegalArgumentException("Branch not found on remote: " + branch + " (looked for " + fullHead + ")");
        }

        // 2) Create ZIP in memory: working files + synthetic .git
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zipOutputStream = new ZipArchiveOutputStream(outputStream); RevWalk rw = new RevWalk(repo)) {

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
            try (TreeWalk treeWalk = new TreeWalk(repo)) {
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
                        repo.open(treeWalk.getObjectId(0), Constants.OBJ_BLOB).copyTo(zipOutputStream);
                        zipOutputStream.closeArchiveEntry();
                    }
                    else if (mode == FileMode.SYMLINK) {
                        // Materialize symlink as a plain text file containing the link target.
                        ensureParentDirs(zipOutputStream, createdDirs, path);
                        ZipArchiveEntry zipEntry = new ZipArchiveEntry(path.replace('\\', '/'));
                        // Zip has no native symlink type; write target as text (or skip)
                        zipEntry.setUnixMode(READ_WRITE_MODE);
                        zipOutputStream.putArchiveEntry(zipEntry);
                        repo.open(treeWalk.getObjectId(0), Constants.OBJ_BLOB).copyTo(zipOutputStream);
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

            createGitIndex(repo, commitId, zipOutputStream, createdDirs);

            // refs + HEAD + config
            putGitBytes(zipOutputStream, createdDirs, "HEAD", ("ref: refs/heads/" + branch + "\n").getBytes(StandardCharsets.UTF_8));
            putGitBytes(zipOutputStream, createdDirs, "refs/heads/" + branch, (commitId.name() + "\n").getBytes(StandardCharsets.UTF_8));
            putGitBytes(zipOutputStream, createdDirs, "refs/remotes/origin/" + branch, (commitId.name() + "\n").getBytes(StandardCharsets.UTF_8));

            writeGitConfig(remoteUri, branch, zipOutputStream, createdDirs);

            zipOutputStream.finish(); // finalize central directory
        }
        return outputStream.toByteArray();
    }

    /**
     * Writes a minimal {@code .git/config} that defines {@code origin} and
     * associates the current branch with {@code refs/heads/&lt;branch&gt;}.
     *
     * @param remoteUri       remote URL to store under {@code remote.origin.url}
     * @param branch          branch name to bind under {@code [branch "&lt;name&gt;"]}
     * @param zipOutputStream open ZIP stream to receive the entry
     * @param createdDirs     set used to deduplicate directory entries
     * @throws IOException if the ZIP entry cannot be written
     */
    private static void writeGitConfig(URI remoteUri, String branch, ZipArchiveOutputStream zipOutputStream, Set<String> createdDirs) throws IOException {
        // Minimal config with origin
        String config = """
                [core]
                    repositoryformatversion = 0
                    filemode = true
                    bare = false
                    logallrefupdates = true
                [remote "origin"]
                    url = %s
                    fetch = +refs/heads/*:refs/remotes/origin/*
                [branch "%s"]
                    remote = origin
                    merge = refs/heads/%s
                """.formatted(remoteUri.toString(), branch, branch);
        putGitBytes(zipOutputStream, createdDirs, "config", config.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Packs all reachable objects from {@code commitId} into a single {@code pack-*.pack}
     * with matching {@code .idx} and writes both under {@code .git/objects/pack/}.
     * The pack file name is computed as the SHA-1 of its content, as in standard Git.
     *
     * @param repo            in-memory repository containing fetched objects
     * @param commitId        tip commit that defines reachability for the pack
     * @param zipOutputStream open ZIP stream to receive pack and index entries
     * @param createdDirs     set used to deduplicate directory entries
     * @throws IOException if pack/index creation or ZIP writes fail
     */
    private static void createGitIndex(InMemoryRepository repo, ObjectId commitId, ZipArchiveOutputStream zipOutputStream, Set<String> createdDirs) throws IOException {
        // Create pack + index
        byte[] packBytes;
        byte[] idxBytes;
        String packHashHex;
        try (ObjectReader reader = repo.newObjectReader(); ObjectWalk objectWalk = new ObjectWalk(reader); PackWriter packWriter = new PackWriter(new PackConfig(repo), reader)) {

            // Mark the tip as the starting point for traversal
            RevCommit tip = objectWalk.parseCommit(commitId);
            objectWalk.markStart(tip);

            // This drives the traversal from `objectWalk` and prevents null ids inside preparePack
            packWriter.preparePack(NullProgressMonitor.INSTANCE, objectWalk, Collections.singleton(commitId), PackWriter.NONE, PackWriter.NONE);

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
            stringBuilder.append(String.format("%02x", b));
        }
        return stringBuilder.toString();
    }

}
