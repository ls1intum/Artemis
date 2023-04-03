package de.tum.in.www1.artemis.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.PrivacyStatement;
import de.tum.in.www1.artemis.domain.PrivacyStatementLanguage;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

@Service
public class PrivacyStatementService {

    private final Logger log = LoggerFactory.getLogger(PrivacyStatementService.class);

    private static final String BASE_PATH = "src/main/resources/public/content/";

    private static final String PRIVACY_STATEMENT_FILE_NAME = "privacy_statement_";

    private static final String PRIVACY_STATEMENT_FILE_EXTENSION = ".md";

    public PrivacyStatement getPrivacyStatementForUpdate(PrivacyStatementLanguage language) throws IOException {
        String privacyStatementText = "";
        if (!Files.exists(getPrivacyStatementPath(language))) {
            return new PrivacyStatement(privacyStatementText, language);
        }
        try {
            privacyStatementText = Files.readString(getPrivacyStatementPath(language));
        }
        catch (IOException e) {
            log.error("Could not read privacy statement file for language {}", language);
            throw new InternalServerErrorException("Could not read privacy statement file for language " + language);
        }
        return new PrivacyStatement(privacyStatementText, language);

    }

    public PrivacyStatement getPrivacyStatement(PrivacyStatementLanguage language) throws IOException {
        String privacyStatementText = "";
        // if it doesn't exist for one language, try to return the other language
        if (!Files.exists(getPrivacyStatementPath(PrivacyStatementLanguage.GERMAN)) && !Files.exists(getPrivacyStatementPath(PrivacyStatementLanguage.ENGLISH))) {
            return new PrivacyStatement(privacyStatementText, language);
        }
        else if (language == PrivacyStatementLanguage.GERMAN && !Files.exists(getPrivacyStatementPath(language))) {
            language = PrivacyStatementLanguage.ENGLISH;
        }
        else if (language == PrivacyStatementLanguage.ENGLISH && !Files.exists(getPrivacyStatementPath(language))) {
            language = PrivacyStatementLanguage.GERMAN;
        }

        try {
            privacyStatementText = Files.readString(getPrivacyStatementPath(language));
        }
        catch (IOException e) {
            log.error("Could not read privacy statement file for language {}", language);
            throw new InternalServerErrorException("Could not read privacy statement file for language " + language);
        }
        return new PrivacyStatement(privacyStatementText, language);

    }

    public PrivacyStatement updatePrivacyStatement(PrivacyStatement privacyStatement) {
        try {
            Files.writeString(getPrivacyStatementPath(privacyStatement.getLanguage()), privacyStatement.getText(), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        }
        catch (IOException e) {
            log.error("Could not update privacy statement file for language {}", privacyStatement.getLanguage());
            throw new InternalServerErrorException("Could not update privacy statement file for language " + privacyStatement.getLanguage());
        }
        return new PrivacyStatement(privacyStatement.getText(), privacyStatement.getLanguage());
    }

    private Path getPrivacyStatementPath(PrivacyStatementLanguage language) throws IOException {
        var path = new ClassPathResource("public/content/privacy_statement_" + language.getShortName() + ".md").getFile().toPath();
        return path;
        // return Path.of(BASE_PATH, PRIVACY_STATEMENT_FILE_NAME + language.getShortName() + PRIVACY_STATEMENT_FILE_EXTENSION);
    }
}
