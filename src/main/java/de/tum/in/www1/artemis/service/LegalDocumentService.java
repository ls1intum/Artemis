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

import de.tum.in.www1.artemis.domain.Imprint;
import de.tum.in.www1.artemis.domain.LegalDocument;
import de.tum.in.www1.artemis.domain.PrivacyStatement;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.enumeration.LegalDocumentType;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

/**
 * Service class responsible for providing and managing legal documents (privacy statment, imprint).
 */
@Service
public class LegalDocumentService {

    private final Logger log = LoggerFactory.getLogger(LegalDocumentService.class);

    @Value("${artemis.legal-path}")
    private Path legalDocumentsBasePath;

    private static final String LEGAL_DOCUMENTS_FILE_EXTENSION = ".md";

    /**
     * Returns the privacy statement if you want to update it.
     *
     * @param language the language of the privacy statement
     * @return the privacy statement that should be updated
     */
    public PrivacyStatement getPrivacyStatementForUpdate(Language language) {
        return (PrivacyStatement) getLegalDocumentForUpdate(language, LegalDocumentType.PRIVACY_STATEMENT);
    }

    /**
     * Returns the imprint if you want to update it.
     *
     * @param language the language of the imprint
     * @return the imprint that should be updated
     */
    public Imprint getImprintForUpdate(Language language) {
        return (Imprint) getLegalDocumentForUpdate(language, LegalDocumentType.IMPRINT);
    }

    /**
     * Returns the imprint if you want to view it.
     *
     * @param language the language of the imprint
     * @return the imprint to view
     */
    public Imprint getImprint(Language language) {
        return (Imprint) getLegalDocument(language, LegalDocumentType.IMPRINT);
    }

    /**
     * Returns the privacy statement if you want to view it.
     *
     * @param language the language of the privacy statement
     * @return the privacy statement to view
     */
    public PrivacyStatement getPrivacyStatement(Language language) {
        return (PrivacyStatement) getLegalDocument(language, LegalDocumentType.PRIVACY_STATEMENT);
    }

    /**
     * Updates the imprint
     *
     * @param imprint the imprint to update with the new content
     * @return the updated imprint
     */
    public Imprint updateImprint(Imprint imprint) {
        return (Imprint) updateLegalDocument(imprint);
    }

    /**
     * Updates the privacy statement
     *
     * @param privacyStatement the privacy statement to update with the new content
     * @return the updated privacy statement
     */
    public PrivacyStatement updatePrivacyStatement(PrivacyStatement privacyStatement) {
        return (PrivacyStatement) updateLegalDocument(privacyStatement);
    }

    /**
     * Returns the legal document if you want to update it.
     * If it currently doesn't exist an empty string is returned as it will be created once the user saves it.
     *
     * @param language the language of the legal document
     * @param type     the type of the legal document
     * @return the legal document with the given language and type
     */
    private LegalDocument getLegalDocumentForUpdate(Language language, LegalDocumentType type) {
        if (getLegalDocumentPathIfExists(language, type).isEmpty()) {
            return switch (type) {
                case PRIVACY_STATEMENT -> new PrivacyStatement("", language);
                case IMPRINT -> new Imprint("", language);
            };

        }
        return readLegalDocument(language, type);
    }

    /**
     * Returns the legal document if you want to view it
     * If it currently doesn't exist in the given language, the other language is returned.
     * If it currently doesn't exist in any language, an exception is thrown.
     *
     * @param language the language of the legal document
     * @param type     the type of the legal document
     * @return the legal document with the given language and type
     */
    private LegalDocument getLegalDocument(Language language, LegalDocumentType type) {
        // if it doesn't exist for one language, try to return the other language, and only throw an exception if it doesn't exist for both languages
        Language alternativeLanguage = language == Language.GERMAN ? Language.ENGLISH : Language.GERMAN;
        if (getLegalDocumentPathIfExists(language, type).isPresent()) {
            return readLegalDocument(language, type);
        }
        else if (getLegalDocumentPathIfExists(alternativeLanguage, type).isPresent()) {
            return readLegalDocument(alternativeLanguage, type);
        }
        else {
            throw new BadRequestAlertException("Could not find " + type + " file for any language", "legalDocument", "noLegalDocumentFile");
        }
    }

    private LegalDocument readLegalDocument(Language language, LegalDocumentType type) {
        String legalDocumentText;
        try {
            legalDocumentText = Files.readString(getLegalDocumentPath(language, type));
        }
        catch (IOException e) {
            log.error("Could not read {} file for language {}:{}", type, language, e);
            throw new InternalServerErrorException("Could not read " + type + " file for language " + language);
        }
        return type == LegalDocumentType.PRIVACY_STATEMENT ? new PrivacyStatement(legalDocumentText, language) : new Imprint(legalDocumentText, language);
    }

    protected LegalDocument updateLegalDocument(LegalDocument legalDocument) {
        if (legalDocument.getText().isBlank()) {
            throw new BadRequestAlertException("Legal document text cannot be empty", legalDocument.getType().name(), "emptyLegalDocument");
        }
        try {
            // If the directory, doesn't exist, we need to create the directory first, otherwise writeString fails.
            if (!Files.exists(legalDocumentsBasePath)) {
                Files.createDirectories(legalDocumentsBasePath);
            }
            Files.writeString(getLegalDocumentPath(legalDocument.getLanguage(), legalDocument.getType()), legalDocument.getText(), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            return legalDocument;
        }
        catch (IOException e) {
            log.error("Could not update {} file for language {}: {} ", legalDocument.getType(), legalDocument.getLanguage(), e);
            throw new InternalServerErrorException("Could not update " + legalDocument.getType() + " file for language " + legalDocument.getLanguage());
        }
    }

    private Optional<Path> getLegalDocumentPathIfExists(Language language, LegalDocumentType type) {
        var filePath = getLegalDocumentPath(language, type);
        if (Files.exists(filePath)) {
            return Optional.of(filePath);
        }
        return Optional.empty();
    }

    private Path getLegalDocumentPath(Language language, LegalDocumentType type) {
        return legalDocumentsBasePath.resolve(type.getFileBaseName() + language.getShortName() + LEGAL_DOCUMENTS_FILE_EXTENSION);
    }

}
