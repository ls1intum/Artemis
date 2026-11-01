package de.tum.cit.aet.artemis.deimos.service;

import static de.tum.cit.aet.artemis.core.config.BinaryFileExtensionConfiguration.isBinaryFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.BinaryFileExtensionConfiguration;
import de.tum.cit.aet.artemis.deimos.config.DeimosEnabled;

/**
 * Reads the files of one commit as evidence for the Deimos analysis.
 * <p>
 * The repository exports drop files by extension alone, and {@link BinaryFileExtensionConfiguration} counts shell and
 * batch scripts as binary, although they are exactly where an attack on the build infrastructure would be written. This
 * reader therefore leaves a file out only when both its extension and the beginning of its content mark it as binary,
 * and never leaves out a script. The paths of all entries it does not return as text are reported, so that they can
 * still be checked.
 */
@Conditional(DeimosEnabled.class)
@Lazy
@Service
public class DeimosSnapshotReader {

    /**
     * Script types that {@link BinaryFileExtensionConfiguration} counts as binary although they are text. A shell still
     * executes such a script when it contains a NUL byte, so its content must not be classified away.
     */
    private static final List<String> TEXT_SCRIPT_EXTENSIONS = List.of(".sh", ".bat");

    /**
     * The files of one commit, split into text files and everything else.
     *
     * @param textFiles  the text files, repository-relative path to UTF-8 content
     * @param otherPaths the paths of all other entries: binary files and symbolic links
     */
    public record TextSnapshot(Map<String, String> textFiles, Set<String> otherPaths) {
    }

    /**
     * Reads every entry of a commit.
     * <p>
     * A file without a binary extension is always returned as text. For a file with such an extension only the
     * beginning is read to classify it, so an archive or image is recognised without loading it; a file whose beginning
     * is text is loaded completely, like every other text file.
     *
     * @param repository the repository
     * @param commitHash the commit to read
     * @return the text files and the remaining paths of that commit, both empty if the commit cannot be resolved
     * @throws IOException if the repository cannot be read
     */
    public TextSnapshot read(Repository repository, String commitHash) throws IOException {
        ObjectId commitId = repository.resolve(commitHash + "^{commit}");
        if (commitId == null) {
            return new TextSnapshot(Map.of(), Set.of());
        }

        Map<String, String> textFiles = new HashMap<>();
        Set<String> otherPaths = new HashSet<>();
        try (RevWalk revWalk = new RevWalk(repository); TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(revWalk.parseCommit(commitId).getTree());
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
                String path = treeWalk.getPathString();
                FileMode fileMode = treeWalk.getFileMode(0);
                if (fileMode == FileMode.SYMLINK || fileMode.getObjectType() != Constants.OBJ_BLOB) {
                    otherPaths.add(path);
                    continue;
                }
                ObjectLoader blobLoader = repository.open(treeWalk.getObjectId(0));
                if (isBinaryFile(path) && !isTextScript(path) && startsWithBinaryContent(blobLoader)) {
                    otherPaths.add(path);
                    continue;
                }
                try (InputStream inputStream = blobLoader.openStream()) {
                    textFiles.put(path, new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        return new TextSnapshot(textFiles, otherPaths);
    }

    private static boolean isTextScript(String path) {
        String lowerCasePath = path.toLowerCase(Locale.ROOT);
        return TEXT_SCRIPT_EXTENSIONS.stream().anyMatch(lowerCasePath::endsWith);
    }

    /**
     * Classifies a blob as binary from its beginning alone, without loading the rest of it.
     *
     * @param blobLoader the loader of the blob
     * @return whether the beginning of the blob is binary
     * @throws IOException if the blob cannot be read
     */
    private static boolean startsWithBinaryContent(ObjectLoader blobLoader) throws IOException {
        try (InputStream inputStream = blobLoader.openStream()) {
            return RawText.isBinary(inputStream);
        }
    }
}
