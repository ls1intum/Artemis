package de.tum.in.www1.artemis.service;

import java.nio.file.Path;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.LegalDocumentLanguage;
import de.tum.in.www1.artemis.domain.LegalDocumentType;
import de.tum.in.www1.artemis.domain.PrivacyStatement;

@Service
public class PrivacyStatementService extends LegalDocumentService {

    private static final String BASE_PATH = "privacy_statements";

    private static final String PRIVACY_STATEMENT_FILE_NAME = "privacy_statement_";

    private static final String PRIVACY_STATEMENT_FILE_EXTENSION = ".md";

    /**
     * Returns the privacy statement if you want to update it.
     * If it currently doesn't exist an empty string is returned as it will be created once the user saves it.
     *
     * @param language the language of the PrivacyStatement
     * @return the privacy statement with the given language
     */
    public PrivacyStatement getPrivacyStatementForUpdate(LegalDocumentLanguage language) {
        return (PrivacyStatement) getLegalDocumentForUpdate(language, LegalDocumentType.PRIVACY_STATEMENT);

    }

    /**
     * Returns the privacy statement if you want to view it
     * If it currently doesn't exist in the given language, the other language is returned.
     * If it currently doesn't exist in any language, an exception is thrown.
     *
     * @param language the language of the privacy statement
     * @return the privacy statement with the given language
     */

    public PrivacyStatement getPrivacyStatement(LegalDocumentLanguage language) {
        return (PrivacyStatement) getLegalDocument(language, LegalDocumentType.PRIVACY_STATEMENT);

    }

    /**
     * Updates the privacy statement and saves it in the file system
     *
     * @param privacyStatement the privacy statement to update
     * @return the updated privacy statement
     */
    public PrivacyStatement updatePrivacyStatement(PrivacyStatement privacyStatement) {
        return (PrivacyStatement) updateLegalDocument(privacyStatement);
    }

    @Override
    protected Path getLegalDocumentPathForType(LegalDocumentLanguage language) {
        return Path.of(BASE_PATH, PRIVACY_STATEMENT_FILE_NAME + language.getShortName() + PRIVACY_STATEMENT_FILE_EXTENSION);

    }

}
