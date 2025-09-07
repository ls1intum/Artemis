package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.programming.domain.Repository;

@Profile(PROFILE_CORE)
@Lazy
@Component
public class GitArchiveHelper {

    private static final Logger log = LoggerFactory.getLogger(GitArchiveHelper.class);

    private static final Set<String> IGNORED_ZIP_FILE_NAMES = Set.of("gc.log.lock");

    InputStreamResource exportRepositoryWithFullHistoryToMemory(Repository sourceRepo, String filename) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {

            ObjectId headCommitId = sourceRepo.resolve("HEAD^{commit}");
            if (headCommitId == null) {
                log.debug("Source repository has no HEAD commit; exporting empty archive");
                zipOutputStream.finish();
                return createInputStreamResource(outputStream.toByteArray(), filename);
            }

            writeTreeSnapshotToZip(sourceRepo, headCommitId, zipOutputStream);

            synthesizeGitDir(sourceRepo, headCommitId, zipOutputStream);

            zipOutputStream.finish();
            return createInputStreamResource(outputStream.toByteArray(), filename);
        }
    }

    @SuppressWarnings("resource")
    byte[] createJGitArchive(Repository repository) throws GitAPIException, IOException {
        ObjectId treeId = repository.resolve("HEAD");
        if (treeId == null) {
            log.debug("Could not resolve tree for HEAD");
            return new byte[0];
        }

        ByteArrayOutputStream archiveData = new ByteArrayOutputStream();
        try (Git git = new Git(repository)) {
            git.archive().setFormat("zip").setTree(treeId).setOutputStream(archiveData).call();
        }
        return archiveData.toByteArray();
    }

    private InputStreamResource createInputStreamResource(byte[] zipData, String filename) {
        return new InputStreamResource(new ByteArrayInputStream(zipData)) {

            @Override
            public String getFilename() {
                return filename + ".zip";
            }

            @Override
            public long contentLength() {
                return zipData.length;
            }
        };
    }

    void addDirectoryToZip(ZipOutputStream zipOutputStream, Path rootPath, Path pathToAdd) throws IOException {
        try (var paths = Files.walk(pathToAdd)) {
            paths.forEach(path -> {
                try {
                    String relativePath = rootPath.relativize(path).toString().replace("\\", "/");
                    String zipEntryName = ".git" + "/" + relativePath;

                    // Skip ignored files like ephemeral lock files
                    String fileName = path.getFileName().toString();
                    if (IGNORED_ZIP_FILE_NAMES.contains(fileName)) {
                        return;
                    }

                    if (Files.isDirectory(path)) {
                        if (!zipEntryName.endsWith("/")) {
                            zipEntryName += "/";
                        }
                        zipOutputStream.putNextEntry(new ZipEntry(zipEntryName));
                    }
                    else if (Files.isRegularFile(path)) {
                        zipOutputStream.putNextEntry(new ZipEntry(zipEntryName));
                        FileUtils.copyFile(path.toFile(), zipOutputStream);
                    }
                    zipOutputStream.closeEntry();
                }
                catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private void writeTreeSnapshotToZip(Repository repo, ObjectId commitId, ZipOutputStream zipOutputStream) throws IOException {
        try (RevWalk revWalk = new RevWalk(repo)) {
            RevCommit revCommit = revWalk.parseCommit(commitId);
            RevTree revTree = revWalk.parseTree(revCommit.getTree());

            try (TreeWalk treeWalk = new TreeWalk(repo)) {
                treeWalk.addTree(revTree);
                treeWalk.setRecursive(true);

                while (treeWalk.next()) {
                    ObjectId objectId = treeWalk.getObjectId(0);
                    ObjectLoader loader = repo.open(objectId);

                    String path = treeWalk.getPathString();
                    zipOutputStream.putNextEntry(new ZipEntry(path));
                    try (var in = loader.openStream()) {
                        in.transferTo(zipOutputStream);
                    }
                    zipOutputStream.closeEntry();
                }
            }
        }
    }

    private void synthesizeGitDir(Repository sourceRepo, ObjectId headCommitId, ZipOutputStream zos) throws IOException {
        // Ensure base directories exist in ZIP
        mkdir(zos, ".git/");
        mkdir(zos, ".git/objects/");
        mkdir(zos, ".git/objects/pack/");
        mkdir(zos, ".git/refs/");
        mkdir(zos, ".git/refs/heads/");

        // Create a pack containing all reachable objects from headCommitId using the source repository
        try (RevWalk rw = new RevWalk(sourceRepo)) {
            java.util.Set<ObjectId> wants = new java.util.HashSet<>();
            wants.add(headCommitId);

            try (PackWriter pw = new PackWriter(sourceRepo)) {
                pw.preparePack(NullProgressMonitor.INSTANCE, wants, java.util.Collections.emptySet());

                // Compute SHA-1 of the packed content to derive the filename
                java.security.MessageDigest sha1;
                try {
                    sha1 = java.security.MessageDigest.getInstance("SHA-1");
                }
                catch (java.security.NoSuchAlgorithmException e) {
                    throw new RuntimeException("SHA-1 algorithm not available", e);
                }
                ByteArrayOutputStream packBaos = new ByteArrayOutputStream();
                try (java.security.DigestOutputStream digestOut = new java.security.DigestOutputStream(packBaos, sha1)) {
                    pw.writePack(NullProgressMonitor.INSTANCE, NullProgressMonitor.INSTANCE, digestOut);
                }
                String packHash = toHex(sha1.digest());
                String packPath = "objects/pack/pack-" + packHash + ".pack";

                // Write .pack
                putGitEntry(zos, packPath, packBaos.toByteArray());

                // Write .idx
                ByteArrayOutputStream idxBaos = new ByteArrayOutputStream();
                pw.writeIndex(idxBaos);
                String idxPath = "objects/pack/pack-" + packHash + ".idx";
                putGitEntry(zos, idxPath, idxBaos.toByteArray());
            }

            // Determine HEAD content and refs
            String fullHead = sourceRepo.getFullBranch(); // might be symbolic ref name or ObjectId for detached HEAD
            String headFileContent;
            String branchRefName = null;

            Ref headRef = sourceRepo.exactRef(Constants.HEAD);
            if (headRef != null && headRef.isSymbolic() && headRef.getTarget() != null && headRef.getTarget().getName() != null
                    && headRef.getTarget().getName().startsWith(Constants.R_HEADS)) {
                branchRefName = headRef.getTarget().getName();
                headFileContent = "ref: " + branchRefName + "\n";
            }
            else if (fullHead != null && fullHead.startsWith("refs/")) {
                // Fallback if exactRef did not yield a symbolic ref
                branchRefName = fullHead;
                headFileContent = "ref: " + branchRefName + "\n";
            }
            else {
                // Detached HEAD; write commit id directly
                headFileContent = headCommitId.name() + "\n";
            }

            putGitEntry(zos, "HEAD", headFileContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (branchRefName != null) {
                putGitEntry(zos, branchRefName, (headCommitId.name() + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            // Minimal config
            String config = "[core]\n" + "\trepositoryformatversion = 0\n" + "\tfilemode = true\n" + "\tbare = false\n" + "\tlogallrefupdates = true\n";
            putGitEntry(zos, "config", config.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static void ensureParentDirs(ZipOutputStream zos, String path) throws IOException {
        int idx = 0;
        while ((idx = path.indexOf('/', idx)) != -1) {
            String dir = path.substring(0, idx + 1);
            mkdir(zos, dir);
            idx += 1;
        }
    }

    private static void mkdir(ZipOutputStream zos, String dir) throws IOException {
        if (!dir.endsWith("/")) {
            dir = dir + "/";
        }
        ZipEntry e = new ZipEntry(dir);
        e.setTime(System.currentTimeMillis());
        try {
            zos.putNextEntry(e);
            zos.closeEntry();
        }
        catch (java.util.zip.ZipException ignored) {
            // directory already exists in zip entries
        }
    }

    private static void putGitEntry(ZipOutputStream zos, String relPath, byte[] content) throws IOException {
        String full = ".git/" + relPath;
        ensureParentDirs(zos, full);
        ZipEntry e = new ZipEntry(full);
        zos.putNextEntry(e);
        zos.write(content);
        zos.closeEntry();
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
