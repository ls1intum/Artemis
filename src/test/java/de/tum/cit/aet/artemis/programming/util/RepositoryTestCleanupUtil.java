package de.tum.cit.aet.artemis.programming.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;

/**
 * Small helper for cleaning up transient files and local repositories in tests.
 */
public final class RepositoryTestCleanupUtil {

    private RepositoryTestCleanupUtil() {
    }

    public static void deleteFileSilently(File file) {
        if (file != null && file.exists()) {
            try {
                Files.deleteIfExists(file.toPath());
            }
            catch (IOException ignored) {
                // ignore
            }
        }
    }

    public static void deleteDirectoryIfExists(Path dir) {
        if (dir != null && Files.exists(dir)) {
            try {
                FileUtils.deleteDirectory(dir.toFile());
            }
            catch (IOException ignored) {
                // ignore
            }
        }
    }

    public static void resetRepos(LocalRepository... repos) {
        if (repos == null) {
            return;
        }
        for (LocalRepository repo : repos) {
            if (repo != null) {
                try {
                    repo.resetLocalRepo();
                }
                catch (IOException ignored) {
                    // ignore
                }
            }
        }
    }
}
