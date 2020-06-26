package de.tum.in.www1.artemis.service;

import java.io.File;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FilePathService {

    @Value("${artemis.file-upload-path}")
    private String fileUploadPath;

    public String getTempFilepath() {
        return fileUploadPath + File.separator + "images" + File.separator + "temp" + File.separator;
    }

    public String getDragAndDropBackgroundFilepath() {
        return fileUploadPath + File.separator + "images" + File.separator + "drag-and-drop" + File.separator + "backgrounds" + File.separator;
    }

    public String getDragItemFilepath() {
        return fileUploadPath + File.separator + "images" + File.separator + "drag-and-drop" + File.separator + "drag-items" + File.separator;
    }

    public String getCourseIconFilepath() {
        return fileUploadPath + File.separator + "images" + File.separator + "course" + File.separator + "icons" + File.separator;
    }

    public String getLectureAttachmentFilepath() {
        return fileUploadPath + File.separator + "attachments" + File.separator + "lecture" + File.separator;
    }

    public String getFileUploadExercisesFilepath() {
        return fileUploadPath + File.separator + "file-upload-exercises" + File.separator;
    }
}
