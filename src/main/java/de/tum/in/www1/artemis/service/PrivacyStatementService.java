package de.tum.in.www1.artemis.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.PrivacyStatement;
import de.tum.in.www1.artemis.domain.PrivacyStatementLanguage;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

@Service
public class PrivacyStatementService {

    private final Logger log = LoggerFactory.getLogger(PrivacyStatementService.class);

    @Value("${artemis.legal-path}")
    private String legalDirBasePath;

    private static final String PRIVACY_STATEMENT_FILE_NAME = "privacy_statement_";

    private static final String PRIVACY_STATEMENT_FILE_EXTENSION = ".md";

    /**
     * Returns the privacy statement if you want to update it.
     * If it currently doesn't exist an empty string is returned as it will be created once the user saves it.
     *
     * @param language the language of the privacy statement
     * @return the privacy statement with the given language
     */
    public PrivacyStatement getPrivacyStatementForUpdate(PrivacyStatementLanguage language) {
        String privacyStatementText = "";
        if (getPrivacyStatementPath(language).isEmpty()) {
            return new PrivacyStatement(privacyStatementText, language);
        }
        try {
            privacyStatementText = Files.readString(getPrivacyStatementPath(language).get());
        }
        catch (IOException e) {
            log.error("Could not read privacy statement file for language {}", language);
            throw new InternalServerErrorException("Could not read privacy statement file for language " + language);
        }
        return new

        PrivacyStatement(privacyStatementText, language);

    }

    /**
     * Returns the privacy statement if you want to view it
     * If it currently doesn't exist in the given language, the other language is returned.
     * If it currently doesn't exist in any language, an exception is thrown.
     *
     * @param language the language of the privacy statement
     * @return the privacy statement with the given language
     */

    public PrivacyStatement getPrivacyStatement(PrivacyStatementLanguage language) {
        String privacyStatementText;
        // if it doesn't exist for one language, try to return the other language, and only throw an exception if it doesn't exist for both languages
        if (getPrivacyStatementPath(PrivacyStatementLanguage.GERMAN).isEmpty() && getPrivacyStatementPath(PrivacyStatementLanguage.ENGLISH).isEmpty()) {
            throw new BadRequestAlertException("Could not find privacy statement file for any language", "privacyStatement", "noPrivacyStatementFile");
        }
        else if (language == PrivacyStatementLanguage.GERMAN && getPrivacyStatementPath(language).isEmpty()) {
            language = PrivacyStatementLanguage.ENGLISH;
        }
        else if (language == PrivacyStatementLanguage.ENGLISH && getPrivacyStatementPath(language).isEmpty()) {
            language = PrivacyStatementLanguage.GERMAN;
        }

        try {
            privacyStatementText = Files.readString(getPrivacyStatementPath(language).get());
        }
        catch (IOException e) {
            log.error("Could not read privacy statement file for language {}", language);
            throw new InternalServerErrorException("Could not read privacy statement file for language " + language);
        }
        return new PrivacyStatement(privacyStatementText, language);

    }

    /**
     * Updates the privacy statement and saves it on the file system. Creates the file if it doesn't exist.
     *
     * @param privacyStatement the privacy statement to update
     * @return the update privacy statement
     */

    public PrivacyStatement updatePrivacyStatement(PrivacyStatement privacyStatement) {
        if (privacyStatement.getText().isBlank()) {
            throw new BadRequestAlertException("Privacy statement text cannot be empty", "privacyStatement", "emptyPrivacyStatement");
        }
        try {
            var baseDirAsPath = Path.of(legalDirBasePath);
            // if we do not create the directory, if it doesn't exist, the file cannot be created
            if (!Files.exists(baseDirAsPath)) {
                Files.createDirectory(baseDirAsPath);
            }
            Files.writeString(getPrivacyStatementPath(privacyStatement.getLanguage(), true).get(), privacyStatement.getText(), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        }
        catch (IOException e) {
            log.error("Could not update privacy statement file for language {} {}", privacyStatement.getLanguage(), e.getMessage());
            throw new InternalServerErrorException("Could not update privacy statement file for language " + privacyStatement.getLanguage());
        }
        return new PrivacyStatement(privacyStatement.getText(), privacyStatement.getLanguage());
    }

    private Optional<Path> getPrivacyStatementPath(PrivacyStatementLanguage language) {
        return getPrivacyStatementPath(language, false);
    }

    private Optional<Path> getPrivacyStatementPath(PrivacyStatementLanguage language, boolean isUpdate) {
        var path = Path.of(legalDirBasePath, PRIVACY_STATEMENT_FILE_NAME + language.getShortName() + PRIVACY_STATEMENT_FILE_EXTENSION);
        if (Files.exists(path)) {
            return Optional.of(path);
        }
        // if it is an update, we need the path to create the file
        return isUpdate ? Optional.of(path) : Optional.empty();
    }

}
