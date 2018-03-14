package de.tum.in.www1.artemis.exception;

/**
 * Created by muenchdo on 22/06/16.
 */
public class BitbucketException extends RuntimeException {

    public BitbucketException() {
    }

    public BitbucketException(String message) {
        super(message);
    }

    public BitbucketException(Throwable cause) {
        super(cause);
    }

    public BitbucketException(String message, Throwable cause) {
        super(message, cause);
    }

}
