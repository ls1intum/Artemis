package de.tum.in.www1.artemis.web.rest;

public class FileMove {

    private String currentFilePath;

    private String newFilename;

    public String getCurrentFilePath() {
        return currentFilePath;
    }

    public void setCurrentFilePath(String currentFilename) {
        this.currentFilePath = currentFilename;
    }

    public String getNewFilename() {
        return newFilename;
    }

    public void setNewFilename(String newFilename) {
        this.newFilename = newFilename;
    }
}
