package de.tum.in.www1.artemis.web.rest;

public class FileMove {
   private String currentFilename;
   private String newFilename;

    public String getCurrentFilename() {
        return currentFilename;
    }

    public void setCurrentFilename(String currentFilename) {
        this.currentFilename = currentFilename;
    }

    public String getNewFilename() {
        return newFilename;
    }

    public void setNewFilename(String newFilename) {
        this.newFilename = newFilename;
    }
}
