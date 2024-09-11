package de.tum.cit.aet.artemis.web.rest.repository;

import java.io.Serializable;

/**
 * Class for unmarshalling file updates received by websocket.
 */
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
