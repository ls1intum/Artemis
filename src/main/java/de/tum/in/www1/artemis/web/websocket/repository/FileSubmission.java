package de.tum.in.www1.artemis.web.websocket.repository;

import java.io.Serializable;

public class FileSubmission implements Serializable {
    private String fileName;
    private String fileContent;

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    public void setFileContent(String fileContent) {
        this.fileContent = fileContent;
    }
    public String getFileName() {
        return fileName;
    }
    public String getFileContent() {
        return fileContent;
    }
}

