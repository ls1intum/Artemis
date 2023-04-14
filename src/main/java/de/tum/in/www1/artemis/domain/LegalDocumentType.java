package de.tum.in.www1.artemis.domain;

public enum LegalDocumentType {

    PRIVACY_STATEMENT, IMPRINT;

    public String getFileBaseName() {
        if (this == PRIVACY_STATEMENT) {
            return "privacy_statement_";
        }
        else if (this == IMPRINT) {
            return "imprint_";
        }
        else {
            throw new IllegalArgumentException("Legal document type not supported");
        }
    }
}
