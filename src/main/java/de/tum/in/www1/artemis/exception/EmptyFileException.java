package de.tum.in.www1.artemis.exception;

public class EmptyFileException extends Exception {

    public EmptyFileException(String fileName) {
        super("The file " + fileName + " is empty.");
    }
}
