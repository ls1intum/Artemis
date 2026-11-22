package de.tum.cit.aet.artemis.localvc.service.git;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.eclipse.jgit.api.ArchiveCommand;
import org.eclipse.jgit.archive.ZipFormat;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;

/**
 * The zip format of the snapshot export. It writes what the JGit zip format writes, but leaves out every entry whose path contains the name of a repository metadata directory,
 * so that the extracted snapshot never contains one, even for a tree that was stored before such names were refused on push.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public final class SnapshotZipFormat implements ArchiveCommand.Format<ArchiveOutputStream> {

    private final ZipFormat delegate = new ZipFormat();

    @Override
    public ArchiveOutputStream createArchiveOutputStream(OutputStream target) throws IOException {
        return delegate.createArchiveOutputStream(target);
    }

    @Override
    public ArchiveOutputStream createArchiveOutputStream(OutputStream target, Map<String, Object> options) throws IOException {
        return delegate.createArchiveOutputStream(target, options);
    }

    @Override
    public void putEntry(ArchiveOutputStream out, ObjectId tree, String path, FileMode mode, ObjectLoader loader) throws IOException {
        if (GitPathNames.containsGitMetadataName(path)) {
            return;
        }
        delegate.putEntry(out, tree, path, mode, loader);
    }

    @Override
    public Iterable<String> suffixes() {
        return delegate.suffixes();
    }

    /**
     * Two instances write the same format. JGit compares registered formats with {@code equals}, so that a second registration of the same format, for example by another
     * application context in the same JVM, is recognized instead of reported as a conflict.
     *
     * @param other the object to compare with
     * @return true if the other object is a snapshot zip format
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof SnapshotZipFormat;
    }

    @Override
    public int hashCode() {
        return SnapshotZipFormat.class.hashCode();
    }
}
