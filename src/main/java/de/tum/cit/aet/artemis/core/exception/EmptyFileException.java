package de.tum.cit.aet.artemis.core.exception;

public class EmptyFileException extends Exception {

    public EmptyFileException(String fileName) {
        super("The file " + fileName + " is empty.");
    }
}
