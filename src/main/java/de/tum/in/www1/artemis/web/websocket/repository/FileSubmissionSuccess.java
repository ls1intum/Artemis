package de.tum.in.www1.artemis.web.websocket.repository;

public class FileSubmissionSuccess {
    private String fileName;

    FileSubmissionSuccess(String fileName) {
        this.fileName = fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    public String getFileName() {
        return fileName;
    }
}
