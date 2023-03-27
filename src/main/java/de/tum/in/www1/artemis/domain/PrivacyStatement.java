package de.tum.in.www1.artemis.domain;

public class PrivacyStatement {

    private String privacyStatementText;

    private final PrivacyStatementLanguage language;

    public PrivacyStatement(PrivacyStatementLanguage language) {
        this.language = language;
    }

    public PrivacyStatement(String privacyStatement, PrivacyStatementLanguage language) {
        this.privacyStatementText = privacyStatement;
        this.language = language;
    }

    public void setPrivacyStatementText(String privacyStatementText) {
        this.privacyStatementText = privacyStatementText;
    }

    public String getPrivacyStatementText() {
        return privacyStatementText;
    }

    public PrivacyStatementLanguage getLanguage() {
        return language;
    }
}
