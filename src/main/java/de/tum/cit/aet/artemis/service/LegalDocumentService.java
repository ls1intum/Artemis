package de.tum.cit.aet.artemis.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.LegalDocumentType;
import de.tum.cit.aet.artemis.web.rest.dto.ImprintDTO;
import de.tum.cit.aet.artemis.web.rest.dto.LegalDocument;
import de.tum.cit.aet.artemis.web.rest.dto.PrivacyStatementDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;

/**
 * Service class responsible for providing and managing legal documents (privacy statment, imprint).
 */
@Profile(PROFILE_CORE)
@Service
public class LegalDocumentService {

    private static final Logger log = LoggerFactory.getLogger(LegalDocumentService.class);

    @Value("${artemis.legal-path}")
    private Path legalDocumentsBasePath;

    private static final String LEGAL_DOCUMENTS_FILE_EXTENSION = ".md";

    /**
     * Returns the privacy statement if you want to update it.
     *
     * @param language the language of the privacy statement
     * @return the privacy statement that should be updated
     */
    public PrivacyStatementDTO getPrivacyStatementForUpdate(Language language) {
        return (PrivacyStatementDTO) getLegalDocumentForUpdate(language, LegalDocumentType.PRIVACY_STATEMENT);
    }

    /**
     * Returns the imprint if you want to update it.
     *
     * @param language the language of the imprint
     * @return the imprint that should be updated
     */
    public ImprintDTO getImprintForUpdate(Language language) {
        return (ImprintDTO) getLegalDocumentForUpdate(language, LegalDocumentType.IMPRINT);
    }

    /**
     * Returns the imprint if you want to view it.
     *
     * @param language the language of the imprint
     * @return the imprint to view
     */
    public ImprintDTO getImprint(Language language) {
        return (ImprintDTO) getLegalDocument(language, LegalDocumentType.IMPRINT);
    }

    /**
     * Returns the privacy statement if you want to view it.
     *
     * @param language the language of the privacy statement
     * @return the privacy statement to view
     */
    public PrivacyStatementDTO getPrivacyStatement(Language language) {
        return (PrivacyStatementDTO) getLegalDocument(language, LegalDocumentType.PRIVACY_STATEMENT);
    }

    /**
     * Updates the imprint
     *
     * @param imprint the imprint to update with the new content
     * @return the updated imprint
     */
    public ImprintDTO updateImprint(ImprintDTO imprint) {
        return (ImprintDTO) updateLegalDocument(imprint);
    }

    /**
     * Updates the privacy statement
     *
     * @param privacyStatement the privacy statement to update with the new content
     * @return the updated privacy statement
     */
    public PrivacyStatementDTO updatePrivacyStatement(PrivacyStatementDTO privacyStatement) {
        return (PrivacyStatementDTO) updateLegalDocument(privacyStatement);
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
                case PRIVACY_STATEMENT -> new PrivacyStatementDTO("", language);
                case IMPRINT -> new ImprintDTO("", language);
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
            log.error("Could not read {} file for language {}:{}", type, language, e.getMessage(), e);
            throw new InternalServerErrorException("Could not read " + type + " file for language " + language);
        }
        return type == LegalDocumentType.PRIVACY_STATEMENT ? new PrivacyStatementDTO(legalDocumentText, language) : new ImprintDTO(legalDocumentText, language);
    }

    protected LegalDocument updateLegalDocument(LegalDocument legalDocument) {
        if (legalDocument.text().isBlank()) {
            throw new BadRequestAlertException("Legal document text cannot be empty", legalDocument.type().name(), "emptyLegalDocument");
        }
        try {
            // If the directory, doesn't exist, we need to create the directory first, otherwise writeString fails.
            if (!Files.exists(legalDocumentsBasePath)) {
                Files.createDirectories(legalDocumentsBasePath);
            }
            FileUtils.writeStringToFile(getLegalDocumentPath(legalDocument.language(), legalDocument.type()).toFile(), legalDocument.text(), StandardCharsets.UTF_8);
            return legalDocument;
        }
        catch (IOException e) {
            log.error("Could not update {} file for language {}: {} ", legalDocument.type(), legalDocument.language(), e.getMessage(), e);
            throw new InternalServerErrorException("Could not update " + legalDocument.type() + " file for language " + legalDocument.language());
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
