package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import java.io.File;
import java.util.Arrays;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.programming.domain.Repository;

/**
 * Service for generating tree-format representations of repository structures.
 * Provides context to LLMs about the current state of the codebase.
 */
@Service
@Profile(PROFILE_HYPERION)
public class RepositoryStructureService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryStructureService.class);

    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(".git", ".idea", ".vscode", "target", "build", "out", "bin", "node_modules", ".gradle", ".mvn", "dist", ".next",
            "coverage");

    private static final Set<String> EXCLUDED_FILES = Set.of(".DS_Store", "Thumbs.db", ".gitkeep");

    /**
     * Generates a tree-format representation of the repository structure.
     * Reads the current state fresh each time to capture any changes.
     *
     * @param repository the repository to analyze
     * @return tree-format string representation of the repository structure
     */
    public String getRepositoryStructure(Repository repository) {
        try {
            File repositoryRoot = repository.getLocalPath().toFile();
            if (!repositoryRoot.exists() || !repositoryRoot.isDirectory()) {
                log.warn("Repository path does not exist or is not a directory: {}", repositoryRoot.getAbsolutePath());
                return "Repository structure could not be determined.";
            }

            StringBuilder structure = new StringBuilder();
            structure.append(repositoryRoot.getName()).append("/").append("\n");
            generateTreeStructure(repositoryRoot, structure, "", true);

            return structure.toString();

        }
        catch (Exception e) {
            log.error("Failed to generate repository structure for repository: {}", repository.getLocalPath(), e);
            return "Repository structure could not be determined due to an error.";
        }
    }

    /**
     * Recursively generates the tree structure representation.
     *
     * @param directory the current directory to process
     * @param structure the StringBuilder to append to
     * @param prefix    the current line prefix for proper tree formatting
     * @param isLast    whether this is the last item in its parent directory
     */
    private void generateTreeStructure(File directory, StringBuilder structure, String prefix, boolean isLast) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        // Filter out excluded directories and files
        File[] filteredFiles = Arrays.stream(files).filter(file -> !EXCLUDED_DIRECTORIES.contains(file.getName()) && !EXCLUDED_FILES.contains(file.getName())).sorted((a, b) -> {
            // Directories first, then files
            if (a.isDirectory() && !b.isDirectory()) {
                return -1;
            }
            else if (!a.isDirectory() && b.isDirectory()) {
                return 1;
            }
            return a.getName().compareToIgnoreCase(b.getName());
        }).toArray(File[]::new);

        for (int i = 0; i < filteredFiles.length; i++) {
            File file = filteredFiles[i];
            boolean isLastFile = (i == filteredFiles.length - 1);

            structure.append(prefix);
            structure.append(isLastFile ? "└── " : "├── ");
            structure.append(file.getName());

            if (file.isDirectory()) {
                structure.append("/");
            }
            structure.append("\n");

            // Recursively process subdirectories
            if (file.isDirectory()) {
                String newPrefix = prefix + (isLastFile ? "    " : "│   ");
                generateTreeStructure(file, structure, newPrefix, false);
            }
        }
    }
}
