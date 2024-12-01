package de.tum.cit.aet.artemis.core.domain;

/**
 * An enum representing the type of legal document, currently the imprint and the privacy statement.
 */
public enum LegalDocumentType {

    PRIVACY_STATEMENT, IMPRINT;

    /**
     * The base file name is combined by with the language short name and the file extension to build the complete file name.
     *
     * @return the file base name for the legal document type
     */
    public String getFileBaseName() {
        return switch (this) {
            case PRIVACY_STATEMENT -> "privacy_statement_";
            case IMPRINT -> "imprint_";
        };
    }
}
