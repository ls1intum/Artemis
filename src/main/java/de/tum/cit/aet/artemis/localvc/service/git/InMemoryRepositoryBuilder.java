package de.tum.cit.aet.artemis.localvc.service.git;

import static de.tum.cit.aet.artemis.localvc.service.git.InMemoryDirCache.EXECUTE_MODE;
import static de.tum.cit.aet.artemis.localvc.service.git.InMemoryDirCache.READ_WRITE_MODE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.io.output.CloseShieldOutputStream;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
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
 * Builds a checkout-ready Git repository straight from a bare repository on disk, either as a single ZIP archive or
 * as a directory.
 * <p>
 * The result contains the working tree at the root and a synthetic {@code .git/}
 * directory with minimal refs, config, and a packed object store. Nothing is checked out.
 * <p>
 * Thread-safety: all methods are stateless and thread-safe.
 */
public class InMemoryRepositoryBuilder {

    private static final Logger log = LoggerFactory.getLogger(InMemoryRepositoryBuilder.class);

    /**
     * A leading drive designator such as {@code C:}, which Windows reads as absolute or relative to that drive. This
     * also refuses a POSIX file whose name happens to start that way, which is a name Windows cannot represent at all,
     * so nothing that could be extracted anywhere is lost by turning it down.
     */
    private static final Pattern WINDOWS_DRIVE_PREFIX = Pattern.compile("^[A-Za-z]:");

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
        try (RepositoryContentSink sink = new ZipRepositoryContentSink(outputStream)) {
            write(bareRepository, sink);
        }
    }

    /**
     * Materializes the same repository into a directory on disk instead of a ZIP, so that a caller which has to hand
     * back a directory does not have to clone the repository and check it out first.
     *
     * @param bareRepository  the bare repository on disk to export
     * @param targetDirectory the directory to write the working tree and the synthetic {@code .git} into
     * @throws IOException if reading the repository or writing the directory fails
     */
    public static void writeToDirectory(Repository bareRepository, Path targetDirectory) throws IOException {
        try (RepositoryContentSink sink = new DirectoryRepositoryContentSink(targetDirectory)) {
            write(bareRepository, sink);
        }
    }

    private static void write(Repository bareRepository, RepositoryContentSink sink) throws IOException {
        String branch = bareRepository.getBranch();
        // Read the refs once, before anything is written. The working tree, the index, the pack and the serialized refs
        // all have to describe the same snapshot, and writing the working tree of a large repository takes long enough
        // for a push to land in between: sampling the ref database again afterwards would produce an archive that names
        // a tip it never exported, and after a force push one whose objects are not even in the pack the index needs.
        // Every branch and tag is taken, not just the exported one, because an archive that keeps only the checked out
        // branch would silently drop commits reachable from a secondary branch or a tag, which the clone this export
        // replaced did carry.
        List<Ref> refsToExport = collectBranchAndTagRefs(bareRepository, branch);
        if (refsToExport.isEmpty()) {
            writeUnbornRepository(sink, branch);
            return;
        }
        ObjectId commitId = exportedCommit(refsToExport, branch);

        try (RevWalk rw = new RevWalk(bareRepository)) {
            writeGitScaffold(sink);

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
                    requireContainedPath(bareRepository, path);
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
                        try (OutputStream fileStream = sink.openFile(path.replace('\\', '/'), executable ? EXECUTE_MODE : READ_WRITE_MODE)) {
                            bareRepository.open(treeWalk.getObjectId(0), Constants.OBJ_BLOB).copyTo(fileStream);
                        }
                    }
                    else if (mode == FileMode.SYMLINK) {
                        // Materialize symlink as a plain text file containing the link target.
                        // A ZIP has no symlink type and a checkout of this archive stores the target as text, which is
                        // what core.symlinks = false in the generated config describes.
                        try (OutputStream fileStream = sink.openFile(path.replace('\\', '/'), READ_WRITE_MODE)) {
                            bareRepository.open(treeWalk.getObjectId(0), Constants.OBJ_BLOB).copyTo(fileStream);
                        }
                    }
                }
                // NOW finalize and serialize the index once
                dirCacheBuilder.finish();
                try (ByteArrayOutputStream indexOut = new ByteArrayOutputStream()) {
                    dirCache.writeTo(indexOut);
                    putGitBytes(sink, "index", indexOut.toByteArray());
                }
            }

            createGitIndex(bareRepository, refsToExport, sink);

            // refs + HEAD + config
            putGitBytes(sink, "HEAD", ("ref: refs/heads/" + branch + "\n").getBytes(StandardCharsets.UTF_8));
            for (Ref ref : refsToExport) {
                putGitBytes(sink, ref.getName(), (ref.getObjectId().name() + "\n").getBytes(StandardCharsets.UTF_8));
            }

            writeGitConfig(sink);
        }
    }

    /**
     * Writes a repository whose branch does not have a commit yet.
     *
     * <p>
     * A participation repository that was created but never pushed to holds no objects at all. That is not a failure
     * the export should report: the caller still has to be handed a repository - the data export owes the student a
     * directory per participation either way - so it gets the scaffold, a {@code HEAD} naming the unborn branch and
     * the same config, which is what cloning the empty repository produced before this export stopped cloning.
     *
     * <p>
     * There is no pack and no index, because there is nothing to put in either, and git reads a missing index as an
     * empty one.
     *
     * @param sink   where the repository is being materialized
     * @param branch the branch {@code HEAD} points at, which has no commit yet
     * @throws IOException if the scaffold or one of the two files cannot be written
     */
    private static void writeUnbornRepository(RepositoryContentSink sink, String branch) throws IOException {
        writeGitScaffold(sink);
        putGitBytes(sink, "HEAD", ("ref: refs/heads/" + branch + "\n").getBytes(StandardCharsets.UTF_8));
        writeGitConfig(sink);
    }

    /**
     * Rejects a tree path that would leave the repository root.
     *
     * <p>
     * The names come from a git tree, and a tree carries whatever a pushing client wrote into it: JGit only refuses a
     * {@code ..} entry when {@code receive.fsckObjects} is set, which it is not by default. Such a name would become a
     * ZIP entry that escapes the directory it is extracted into, so it is refused here, once, for every destination -
     * rather than only in the sink that writes to disk and happens to resolve against a root it can compare against.
     *
     * @param bareRepository the repository being exported, named in the error so the offending one can be found
     * @param path           the path of the tree entry, as {@code TreeWalk} reports it
     * @throws IOException if the path is absolute, names a drive, or walks above the repository root
     */
    private static void requireContainedPath(Repository bareRepository, String path) throws IOException {
        // The sinks turn a backslash into a separator, so a name carrying one has to be judged the same way here.
        String normalized = path.replace('\\', '/');
        // A leading separator makes the path absolute, a drive letter makes it absolute or drive relative once the
        // archive is extracted on Windows, and a ".." segment walks above the root it is resolved against. All three
        // put the entry somewhere other than where the person extracting the archive asked for it.
        boolean leavesRoot = normalized.startsWith("/") || WINDOWS_DRIVE_PREFIX.matcher(normalized).find() || List.of(normalized.split("/")).contains("..");
        if (leavesRoot) {
            throw new IOException(
                    "Cannot export the repository " + bareRepository.getRemoteRepositoryUri() + " because it contains the path " + path + ", which leaves the repository root");
        }
    }

    /**
     * Collects the branches and tags the archive has to carry.
     *
     * <p>
     * {@code HEAD}'s branch is guaranteed to be in the result even for a detached head, because the archive's working
     * tree and index describe that commit and the extracted repository would otherwise have a {@code HEAD} pointing at
     * a ref that does not exist.
     *
     * @param bareRepository the bare repository to read the refs from
     * @param branch         the branch the working tree is taken from
     * @return the refs to pack and to serialize into the archive's {@code .git}, or an empty list for a repository
     *         that has no commit at all, which {@link #writeUnbornRepository} then exports
     * @throws IOException if the refs cannot be read, or the branch does not resolve although the repository does have
     *                         commits, which would mean exporting a snapshot of something that is not there
     */
    private static List<Ref> collectBranchAndTagRefs(Repository bareRepository, String branch) throws IOException {
        var refDatabase = bareRepository.getRefDatabase();
        List<Ref> refs = new ArrayList<>();
        for (Ref ref : refDatabase.getRefsByPrefix(Constants.R_HEADS, Constants.R_TAGS)) {
            if (ref.getObjectId() != null) {
                refs.add(ref);
            }
        }
        if (refs.stream().noneMatch(ref -> ref.getName().equals(Constants.R_HEADS + branch))) {
            // A detached HEAD has no branch ref to take the commit from, so resolve HEAD and give the snapshot an entry
            // of its own for it: the archive's working tree describes that commit, and it has to be both packed and
            // reachable through the HEAD the archive writes.
            ObjectId head = bareRepository.resolve(Constants.HEAD);
            if (head == null) {
                if (!refs.isEmpty()) {
                    // The repository has commits, just not where HEAD says. Exporting a working tree for that would
                    // mean inventing one, so this is reported rather than papered over.
                    throw new IOException("Cannot export the repository " + bareRepository.getRemoteRepositoryUri() + " because neither branch " + branch
                            + " nor HEAD resolves, although it has other refs");
                }
                // Nothing was ever pushed, so HEAD names a branch that does not exist yet. An empty repository is a
                // legitimate thing to export; the caller gets one back rather than an error.
                return List.of();
            }
            refs.add(new ObjectIdRef.PeeledNonTag(Ref.Storage.LOOSE, Constants.R_HEADS + branch, head));
        }
        return refs;
    }

    /**
     * Returns the commit whose tree the archive materializes, taken from the same ref snapshot that gets packed and
     * serialized, so the working tree, the index, the pack and {@code .git/refs} cannot end up describing different
     * commits.
     *
     * @param refs   the ref snapshot, which {@link #collectBranchAndTagRefs} guarantees contains the exported branch
     * @param branch the branch the working tree is taken from
     * @return the tip commit of that branch
     */
    private static ObjectId exportedCommit(List<Ref> refs, String branch) {
        return refs.stream().filter(ref -> ref.getName().equals(Constants.R_HEADS + branch)).findFirst()
                .orElseThrow(() -> new IllegalStateException("The exported branch " + branch + " is missing from the ref snapshot")).getObjectId();
    }

    /**
     * Writes a minimal {@code .git/config}.
     *
     * <p>
     * Deliberately without a remote: the only URL available here is the server's internal path to the bare repository,
     * which is both unusable on the machine that extracts the archive and something the archive should not disclose.
     *
     * @param sink where the repository is being materialized
     * @throws IOException if the ZIP entry cannot be written
     */
    private static void writeGitConfig(RepositoryContentSink sink) throws IOException {
        // symlinks = false because a zip has no symlink type: the working tree stores a symlink as a plain file holding
        // the link target, while the index still records mode 120000. Without this, git on a symlink-capable system
        // compares the two and reports a type change, so a freshly extracted archive would be dirty before it is
        // touched.
        String config = """
                [core]
                    repositoryformatversion = 0
                    filemode = true
                    bare = false
                    logallrefupdates = true
                    symlinks = false
                """;
        putGitBytes(sink, "config", config.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Packs everything reachable from the given refs into a single {@code pack-*.pack} with matching {@code .idx} and
     * writes both under {@code .git/objects/pack/}. The pack file name is computed as the SHA-1 of its content, as in
     * standard Git.
     *
     * <p>
     * Every branch and tag is a starting point, so an archive keeps the commits that are only reachable from a
     * secondary branch, and the tag objects themselves.
     *
     * <p>
     * Both files are written straight into the ZIP entry. Buffering them first would put two copies of the entire pack
     * on the heap, which matters because this runs for every instructor repository of every exercise while a course is
     * being archived. JGit can name the pack before writing it, since the name is derived from the object list that
     * {@code preparePack} produced rather than from the serialized bytes.
     *
     * @param bareRepository the bare repository the objects are read from
     * @param refs           the branches and tags that define reachability for the pack
     * @param sink           where the repository is being materialized
     * @throws IOException if pack/index creation or ZIP writes fail
     */
    private static void createGitIndex(Repository bareRepository, List<Ref> refs, RepositoryContentSink sink) throws IOException {
        try (ObjectReader reader = bareRepository.newObjectReader();
                ObjectWalk objectWalk = new ObjectWalk(reader);
                PackWriter packWriter = new PackWriter(new PackConfig(bareRepository), reader)) {

            // Mark every ref tip as a starting point. An annotated tag is marked as the tag object itself, so the tag
            // lands in the pack rather than only the commit it points at.
            Set<ObjectId> tips = new LinkedHashSet<>();
            for (Ref ref : refs) {
                objectWalk.markStart(objectWalk.parseAny(ref.getObjectId()));
                tips.add(ref.getObjectId());
            }

            // This drives the traversal from `objectWalk` and prevents null ids inside preparePack
            packWriter.preparePack(NullProgressMonitor.INSTANCE, objectWalk, tips, PackWriter.NONE, PackWriter.NONE);

            String packHashHex = packWriter.computeName().name();
            writeGitEntry(sink, "objects/pack/pack-" + packHashHex + ".pack", out -> packWriter.writePack(NullProgressMonitor.INSTANCE, NullProgressMonitor.INSTANCE, out));
            writeGitEntry(sink, "objects/pack/pack-" + packHashHex + ".idx", packWriter::writeIndex);
        }
    }

    /**
     * Opens a {@code .git/} entry and lets the writer stream its content straight into the ZIP, so nothing has to be
     * buffered on the heap first. The ZIP stream is close-shielded because the writers close what they are given.
     */
    private static void writeGitEntry(RepositoryContentSink sink, String relPath, GitEntryWriter writer) throws IOException {
        try (OutputStream entryStream = sink.openFile(".git/" + relPath, READ_WRITE_MODE)) {
            // The writers close what they are handed, which must end this entry rather than the whole sink.
            writer.writeTo(CloseShieldOutputStream.wrap(entryStream));
        }
    }

    @FunctionalInterface
    private interface GitEntryWriter {

        void writeTo(OutputStream outputStream) throws IOException;
    }

    // ---- Helpers ----------------------------------------------------------------

    /**
     * Adds the minimal directory skeleton for {@code .git/}.
     * Idempotent: the sink suppresses duplicate directories.
     *
     * @param sink where the repository is being materialized
     * @throws IOException if a directory cannot be created
     */
    private static void writeGitScaffold(RepositoryContentSink sink) throws IOException {
        sink.createDirectory(".git/");
        sink.createDirectory(".git/objects/");
        sink.createDirectory(".git/objects/pack/");
        sink.createDirectory(".git/refs/");
        sink.createDirectory(".git/refs/heads/");
    }

    /**
     * Writes a file under {@code .git/} at {@code relPath} with the given bytes.
     * Parent directories are created if missing.
     *
     * @param sink    where the repository is being materialized
     * @param relPath path relative to {@code .git/} (e.g., {@code refs/heads/main})
     * @param bytes   file content
     * @throws IOException if the ZIP entry cannot be written
     */
    private static void putGitBytes(RepositoryContentSink sink, String relPath, byte[] bytes) throws IOException {
        sink.writeFile(".git/" + relPath, bytes, READ_WRITE_MODE);
    }

}
