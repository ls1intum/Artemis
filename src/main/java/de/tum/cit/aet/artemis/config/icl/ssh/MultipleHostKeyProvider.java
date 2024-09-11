package de.tum.cit.aet.artemis.config.icl.ssh;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.common.util.GenericUtils;
import org.apache.sshd.common.util.io.IoUtils;
import org.apache.sshd.server.keyprovider.AbstractGeneratorHostKeyProvider;

import com.google.common.collect.Lists;

/**
 * A host key provider that can load multiple host keys from a directory.
 * This is needed for loading multiple host keys for different algorithms.
 */
public class MultipleHostKeyProvider extends AbstractGeneratorHostKeyProvider {

    public MultipleHostKeyProvider(Path path) {
        super();
        setPath(path);
    }

    /**
     * Load the keys from the configured path. If the path is a directory, all keys in the directory
     *
     * @param session The {@link SessionContext} for invoking this load command - may be {@code null}
     *                    if not invoked within a session context (e.g., offline tool or session unknown).
     * @return A list of {@link KeyPair} instances loaded from the configured path
     */
    @Override
    public synchronized List<KeyPair> loadKeys(SessionContext session) {
        var path = getPath();
        // If only a single key is provided
        if (!Files.isDirectory(path)) {
            return super.loadKeys(session);
        }

        var keys = new ArrayList<KeyPair>();

        try (var stream = Files.list(path)) {
            stream.filter(Objects::nonNull).forEach(file -> {
                try {
                    // Read a single key pair in the directory
                    Iterable<KeyPair> ids = readKeyPairs(session, file, IoUtils.EMPTY_OPEN_OPTIONS);
                    KeyPair kp = GenericUtils.head(ids);
                    if (kp != null) {
                        keys.addAll(Lists.newArrayList(ids));
                    }
                }
                catch (Exception e) {
                    warn("resolveKeyPair({}) Failed ({}) to load: {}", file, e.getClass().getSimpleName(), e.getMessage(), e);
                }
            });
        }
        catch (IOException e) {
            warn("loadKeys({}) Failed ({}) to list keys: {}", path, e.getClass().getSimpleName(), e.getMessage(), e);
        }

        return keys;
    }

    @Override
    protected void doWriteKeyPair(NamedResource resourceKey, KeyPair kp, OutputStream outputStream) {
        // Not implemented
    }
}
