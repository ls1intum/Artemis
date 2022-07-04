package de.tum.in.www1.artemis.service;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FilePathService {

    // Note: We use this static field as a kind of constant. In Spring, we cannot inject a value into a constant field, so we have to use this work-around.
    // This is also documented here: https://www.baeldung.com/spring-inject-static-field
    // We can not use a normal service here, as some classes (in the domain package) require this service (or depend on another service that depend on this service), were we cannot
    // use auto-injection
    // TODO: Rework this behaviour be removing the dependencies to services (like FileService) from the domain package
    private static String fileUploadPath;

    @Value("${artemis.file-upload-path}")
    public void setFileUploadPathStatic(String fileUploadPath) {
        FilePathService.fileUploadPath = fileUploadPath;
    }

    public static String getTempFilePath() {
        return Path.of(fileUploadPath, "images", "temp").toString();
    }

    public static String getDragAndDropBackgroundFilePath() {
        return Path.of(fileUploadPath, "images", "drag-and-drop", "backgrounds").toString();
    }

    public static String getDragItemFilePath() {
        return Path.of(fileUploadPath, "images", "drag-and-drop", "drag-items").toString();
    }

    public static String getCourseIconFilePath() {
        return Path.of(fileUploadPath, "images", "course", "icons").toString();
    }

    public static String getLectureAttachmentFilePath() {
        return Path.of(fileUploadPath, "attachments", "lecture").toString();
    }

    public static String getAttachmentUnitFilePath() {
        return Path.of(fileUploadPath, "attachments", "attachment-unit").toString();
    }

    public static String getFileUploadExercisesFilePath() {
        return Path.of(fileUploadPath, "file-upload-exercises").toString();
    }

    public static String getMarkdownFilePath() {
        return Path.of(fileUploadPath, "markdown").toString();
    }
}
