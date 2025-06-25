package de.tum.cit.aet.artemis.programming.util;

import java.io.File;
import java.nio.file.Path;

public class LocalRepositoryUriUtil {

    public static String convertToLocalVcUriString(File repoFile, Path localVCRepoPath) {
        try {
            // we basically add a "git" in the middle of the URI to make it a valid LocalVC URI to avoid exceptions due to strict checks
            Path originalAbsolutePath = repoFile.toPath().toAbsolutePath().normalize();
            Path prefixAbsolutePath = localVCRepoPath.toAbsolutePath().normalize();

            if (!originalAbsolutePath.startsWith(prefixAbsolutePath)) {
                throw new IllegalArgumentException("Path '" + repoFile.getPath() + "' does not start with configured localVCRepoPath '" + prefixAbsolutePath + "'");
            }

            // Relative path after the configured prefix
            Path relativePath = prefixAbsolutePath.relativize(originalAbsolutePath);

            // Construct modified path with inserted 'git' segment
            Path modifiedPath = prefixAbsolutePath.resolve("git").resolve(relativePath);

            return modifiedPath.toUri().toURL().toString();
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert file to URI string: " + repoFile.getPath(), e);
        }
    }
}
