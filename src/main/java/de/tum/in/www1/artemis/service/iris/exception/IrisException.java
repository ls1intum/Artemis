package de.tum.in.www1.artemis.service.iris.exception;

public class IrisException extends Exception {

    protected String translationKey;

    public IrisException(String translationKey) {
        this.translationKey = translationKey;
    }

    public IrisException(String message, String translationKey) {
        super(message);
        this.translationKey = translationKey;
    }

    public IrisException(String message, Throwable cause, String translationKey) {
        super(message, cause);
        this.translationKey = translationKey;
    }

    public IrisException(Throwable cause, String translationKey) {
        super(cause);
        this.translationKey = translationKey;
    }

    public IrisException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, String translationKey) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.translationKey = translationKey;
    }

    public String getTranslationKey() {
        return translationKey;
    }
}
