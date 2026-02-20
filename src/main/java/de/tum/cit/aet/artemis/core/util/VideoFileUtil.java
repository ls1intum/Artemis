package de.tum.cit.aet.artemis.core.util;

import static de.tum.cit.aet.artemis.core.util.FileUtil.checkAndSanitizeFilename;
import static de.tum.cit.aet.artemis.core.util.FileUtil.generateFilename;
import static de.tum.cit.aet.artemis.core.util.FileUtil.generateTargetFilenameBase;
import static de.tum.cit.aet.artemis.core.util.FileUtil.saveFile;

import java.nio.file.Path;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

/**
 * Utility class for video file operations.
 * Contains methods for validating and saving video files.
 */
public class VideoFileUtil {

    /**
     * The list of video file extensions that are allowed to be uploaded.
     * Extensions must be lower-case without leading dots.
     * NOTE: Has to be kept in sync with the client-side definitions in file-extensions.constants.ts
     */
    private static final Set<String> allowedVideoFileExtensions = Set.of("mp4", "webm", "ogg", "mov", "avi", "mkv", "flv", "wmv", "m4v");

    private VideoFileUtil() {
        // Utility class, no instantiation
    }

    /**
     * Validates the file extension for video files.
     *
     * @param filename the filename to validate
     * @throws BadRequestAlertException if the file extension is not a valid video format
     */
    public static void validateVideoExtension(String filename) {
        final String fileExtension = FilenameUtils.getExtension(filename);
        if (allowedVideoFileExtensions.stream().noneMatch(fileExtension::equalsIgnoreCase)) {
            throw new BadRequestAlertException("Unsupported video file type! Allowed video file types: " + String.join(", ", allowedVideoFileExtensions), "file", null, true);
        }
    }

    /**
     * Saves a video file to the given path using a generated filename.
     * This method specifically validates video file extensions.
     *
     * @param file         the video file to save
     * @param basePath     the base path to save the file to
     * @param filePathType the type of the file path
     * @param keepFilename whether to keep the original filename or not
     * @return the path where the file was saved
     */
    @NonNull
    public static Path saveVideoFile(MultipartFile file, Path basePath, FilePathType filePathType, boolean keepFilename) {
        String sanitizedFilename = checkAndSanitizeFilename(file.getOriginalFilename());
        validateVideoExtension(sanitizedFilename);
        String generatedFilename = generateFilename(generateTargetFilenameBase(filePathType), sanitizedFilename, keepFilename);
        Path savePath = basePath.resolve(generatedFilename);
        return saveFile(file, savePath);
    }

    /**
     * Checks if the given filename has a video file extension.
     *
     * @param filename the filename to check
     * @return true if the file is a video file, false otherwise
     */
    public static boolean isVideoFile(String filename) {
        final String fileExtension = FilenameUtils.getExtension(filename);
        return allowedVideoFileExtensions.stream().anyMatch(fileExtension::equalsIgnoreCase);
    }

    /**
     * Validates that the file size does not exceed the maximum allowed size.
     * Throws a ResponseStatusException with HTTP 413 (Payload Too Large) if exceeded.
     *
     * @param file        the file to validate
     * @param maxFileSize the maximum allowed file size in bytes
     * @throws ResponseStatusException if the file size exceeds the maximum allowed size
     */
    public static void validateFileSize(MultipartFile file, long maxFileSize) {
        if (file != null && file.getSize() > maxFileSize) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "The file is too large. Maximum file size is " + (maxFileSize / (1024 * 1024)) + " MB.");
        }
    }

    /**
     * Validates the file size based on whether it's a video file or not.
     * Video files are allowed up to the specified maxVideoFileSize, other files up to MAX_FILE_SIZE.
     *
     * @param file             the file to validate
     * @param maxVideoFileSize the maximum allowed video file size in bytes
     * @throws ResponseStatusException if the file size exceeds the maximum allowed size
     */
    public static void validateFileSizeWithVideoLimit(MultipartFile file, long maxVideoFileSize) {
        if (file == null) {
            return;
        }
        var originalFilename = file.getOriginalFilename();
        // If filename is missing, default to the stricter non-video limit.
        long maxSize = (originalFilename != null && isVideoFile(originalFilename)) ? maxVideoFileSize : Constants.MAX_FILE_SIZE;
        validateFileSize(file, maxSize);
    }
}
