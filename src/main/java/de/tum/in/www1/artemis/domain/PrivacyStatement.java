package de.tum.in.www1.artemis.domain;

public class PrivacyStatement {

    private String text;

    private final PrivacyStatementLanguage language;

    public PrivacyStatement(PrivacyStatementLanguage language) {
        this.language = language;
    }

    public PrivacyStatement(String privacyStatement, PrivacyStatementLanguage language) {
        this.text = privacyStatement;
        this.language = language;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public PrivacyStatementLanguage getLanguage() {
        return language;
    }
}
