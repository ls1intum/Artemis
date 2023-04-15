package de.tum.in.www1.artemis.domain;

public enum LegalDocumentType {

    PRIVACY_STATEMENT, IMPRINT;

    public String getFileBaseName() {
        return switch (this) {
            case PRIVACY_STATEMENT -> "privacy_statement_";
            case IMPRINT -> "imprint_";
        };
    }
}
