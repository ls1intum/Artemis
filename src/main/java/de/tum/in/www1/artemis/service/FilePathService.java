package de.tum.in.www1.artemis.service;

import java.io.File;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FilePathService {

    // Note: We use this static field as a kind of constant. In Spring, we cannot inject a value into a constant field, so we have to use this work-around.
    // This is also documented here: https://www.baeldung.com/spring-inject-static-field
    // We can not use a normal service here, as some classes (in the domain package) require this service (or depend on another service that depend on this service), were we cannot
    // use auto-injection
    // TODO: Rework this behaviour be removing the dependencies to services (like FileService) from the domain package
    private static String FILE_UPLOAD_PATH;

    @Value("${artemis.file-upload-path}")
    public void setFileUploadPathStatic(String fileUploadPath) {
        FilePathService.FILE_UPLOAD_PATH = fileUploadPath;
    }

    public static String getTempFilepath() {
        return FILE_UPLOAD_PATH + File.separator + "images" + File.separator + "temp" + File.separator;
    }

    public static String getDragAndDropBackgroundFilepath() {
        return FILE_UPLOAD_PATH + File.separator + "images" + File.separator + "drag-and-drop" + File.separator + "backgrounds" + File.separator;
    }

    public static String getDragItemFilepath() {
        return FILE_UPLOAD_PATH + File.separator + "images" + File.separator + "drag-and-drop" + File.separator + "drag-items" + File.separator;
    }

    public static String getCourseIconFilepath() {
        return FILE_UPLOAD_PATH + File.separator + "images" + File.separator + "course" + File.separator + "icons" + File.separator;
    }

    public static String getLectureAttachmentFilepath() {
        return FILE_UPLOAD_PATH + File.separator + "attachments" + File.separator + "lecture" + File.separator;
    }

    public static String getAttachmentUnitFilePath() {
        return FILE_UPLOAD_PATH + File.separator + "attachments" + File.separator + "attachment-unit" + File.separator;
    }

    public static String getFileUploadExercisesFilepath() {
        return FILE_UPLOAD_PATH + File.separator + "file-upload-exercises" + File.separator;
    }

    public static String getMarkdownFilepath() {
        return FILE_UPLOAD_PATH + File.separator + "markdown" + File.separator;
    }
}
