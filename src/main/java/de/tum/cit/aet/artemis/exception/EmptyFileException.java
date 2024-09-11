package de.tum.cit.aet.artemis.exception;

public class EmptyFileException extends Exception {

    public EmptyFileException(String fileName) {
        super("The file " + fileName + " is empty.");
    }
}
