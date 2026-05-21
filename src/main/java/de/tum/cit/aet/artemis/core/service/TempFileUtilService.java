package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Utility service for creating temporary files and directories.
 * <p>
 * All temporary files and directories should be created using this service to ensure
 * proper isolation within the configured temp path ({@code artemis.temp-path}).
 * <p>
 * <b>Important:</b> Code must NOT directly call {@link Files#createTempDirectory} or
 * {@link Files#createTempFile}. This is enforced by an architecture test.
 *
 * @see de.tum.cit.aet.artemis.core.architecture.TempFileArchitectureTest
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class TempFileUtilService {

    private Path tempPath;

    /**
     * Default constructor for Spring dependency injection.
     * The tempPath will be injected via {@code @Value}.
     */
    public TempFileUtilService() {
        // tempPath is set by Spring via @Value
    }

    /**
     * Constructor for manual instantiation (e.g., in unit tests without Spring context).
     *
     * @param tempPath the base path for temporary files
     */
    public TempFileUtilService(Path tempPath) {
        this.tempPath = tempPath;
    }

    @Value("${artemis.temp-path}")
    void setTempPath(Path tempPath) {
        this.tempPath = tempPath;
    }

    /**
     * Creates a temporary directory within the configured temp path.
     *
     * @param prefix the prefix string to be used in generating the directory's name
     * @return the path to the newly created directory
     * @throws IOException if an I/O error occurs or the temp directory does not exist
     */
    public Path createTempDirectory(String prefix) throws IOException {
        Files.createDirectories(tempPath);
        return Files.createTempDirectory(tempPath, prefix);
    }

    /**
     * Creates a temporary directory within the specified parent directory.
     * <p>
     * Use this method when you need to create subdirectories within an already-created
     * temp directory, or when using a different base path (e.g., data-export-path).
     *
     * @param parent the parent directory path
     * @param prefix the prefix string to be used in generating the directory's name
     * @return the path to the newly created directory
     * @throws IOException if an I/O error occurs or the parent directory does not exist
     */
    public Path createTempDirectory(Path parent, String prefix) throws IOException {
        Files.createDirectories(parent);
        return Files.createTempDirectory(parent, prefix);
    }

    /**
     * Creates a temporary file within the configured temp path.
     *
     * @param prefix the prefix string to be used in generating the file's name
     * @param suffix the suffix string to be used in generating the file's name (e.g., ".txt")
     * @return the path to the newly created file
     * @throws IOException if an I/O error occurs or the temp directory does not exist
     */
    public Path createTempFile(String prefix, String suffix) throws IOException {
        Files.createDirectories(tempPath);
        return Files.createTempFile(tempPath, prefix, suffix);
    }

    /**
     * Creates a temporary file within the specified parent directory.
     * <p>
     * Use this method when you need to create temp files within an already-created
     * temp directory, or when using a different base path.
     *
     * @param parent the parent directory path
     * @param prefix the prefix string to be used in generating the file's name
     * @param suffix the suffix string to be used in generating the file's name (e.g., ".txt")
     * @return the path to the newly created file
     * @throws IOException if an I/O error occurs or the parent directory does not exist
     */
    public Path createTempFile(Path parent, String prefix, String suffix) throws IOException {
        Files.createDirectories(parent);
        return Files.createTempFile(parent, prefix, suffix);
    }

    /**
     * Returns the configured temp path.
     *
     * @return the temp path
     */
    public Path getTempPath() {
        return tempPath;
    }
}
