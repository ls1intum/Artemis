package de.tum.in.www1.artemis.service;

import java.nio.file.Paths;

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
        return Paths.get(fileUploadPath, "images", "temp").toString();
    }

    public static String getDragAndDropBackgroundFilePath() {
        return Paths.get(fileUploadPath, "images", "drag-and-drop", "backgrounds").toString();
    }

    public static String getDragItemFilePath() {
        return Paths.get(fileUploadPath, "images", "drag-and-drop", "drag-items").toString();
    }

    public static String getCourseIconFilePath() {
        return Paths.get(fileUploadPath, "images", "course", "icons").toString();
    }

    public static String getLectureAttachmentFilePath() {
        return Paths.get(fileUploadPath, "attachments", "lecture").toString();
    }

    public static String getAttachmentUnitFilePath() {
        return Paths.get(fileUploadPath, "attachments", "attachment-unit").toString();
    }

    public static String getFileUploadExercisesFilePath() {
        return Paths.get(fileUploadPath, "file-upload-exercises").toString();
    }

    public static String getMarkdownFilePath() {
        return Paths.get(fileUploadPath, "markdown").toString();
    }
}
