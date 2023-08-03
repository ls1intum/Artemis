package de.tum.in.www1.artemis.service.dataexport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import de.tum.in.www1.artemis.domain.Course;

/**
 * A utility class for data export containing helper methods that are frequently used in the different services responsible for creating data exports.
 */
final class DataExportUtil {

    private static final String COURSE_DIRECTORY_PREFIX = "course_";

    private DataExportUtil() {
        // Utility class
    }

    static void createDirectoryIfNotExistent(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            Files.createDirectory(directory);
        }
    }

    static Path retrieveCourseDirPath(Path workingDirectory, Course course) {
        return workingDirectory.resolve(COURSE_DIRECTORY_PREFIX + course.getShortName());
    }
}
