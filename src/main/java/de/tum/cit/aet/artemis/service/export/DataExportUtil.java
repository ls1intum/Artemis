package de.tum.cit.aet.artemis.service.export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import de.tum.cit.aet.artemis.core.domain.Course;

/**
 * A utility class for data export containing helper methods that are frequently used in the different services responsible for creating data exports.
 */
public final class DataExportUtil {

    private static final String COURSE_DIRECTORY_PREFIX = "course_";

    private DataExportUtil() {
        // Utility class
    }

    /**
     * Creates the given directory if it does not exist yet.
     *
     * @param directory the directory to create
     * @throws IOException if an error occurs while accessing the file system
     */
    public static void createDirectoryIfNotExistent(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }
    }

    /**
     * Retrieves the path to the directory for the given course within the data export.
     *
     * @param workingDirectory the working directory where the data export is created
     * @param course           the course for which the directory should be retrieved
     * @return the path to the directory for the given course
     */
    static Path retrieveCourseDirPath(Path workingDirectory, Course course) {
        return workingDirectory.resolve(COURSE_DIRECTORY_PREFIX + course.getShortName());
    }
}
