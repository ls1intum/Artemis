package de.tum.in.www1.artemis.web.websocket.repository;

/**
 * Class for marshalling and sending success messages for file updates received by websocket.
 */
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
