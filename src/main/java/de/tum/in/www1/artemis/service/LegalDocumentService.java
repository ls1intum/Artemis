package de.tum.in.www1.artemis.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

@Service
public abstract class LegalDocumentService {

    private final Logger log = LoggerFactory.getLogger(LegalDocumentService.class);

    /**
     * Returns the legal document if you want to update it.
     * If it currently doesn't exist an empty string is returned as it will be created once the user saves it.
     *
     * @param language the language of the legal document
     * @param type     the type of the legal document
     * @return the legal document with the given language and type
     */
    protected LegalDocument getLegalDocumentForUpdate(LegalDocumentLanguage language, LegalDocumentType type) {
        String legalDocumentText = "";
        if (getLegalDocumentPath(language).isEmpty()) {
            return type == LegalDocumentType.PRIVACY_STATEMENT ? new PrivacyStatement(legalDocumentText, language) : new Imprint(legalDocumentText, language);

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
    protected LegalDocument getLegalDocument(LegalDocumentLanguage language, LegalDocumentType type) {
        // if it doesn't exist for one language, try to return the other language, and only throw an exception if it doesn't exist for both languages
        if (getLegalDocumentPath(LegalDocumentLanguage.GERMAN).isEmpty() && getLegalDocumentPath(LegalDocumentLanguage.ENGLISH).isEmpty()) {
            throw new BadRequestAlertException("Could not find " + type + " file for any language", type.name(), "noLegalDocumentFile");
        }
        else if (language == LegalDocumentLanguage.GERMAN && getLegalDocumentPath(language).isEmpty()) {
            language = LegalDocumentLanguage.ENGLISH;
        }
        else if (language == LegalDocumentLanguage.ENGLISH && getLegalDocumentPath(language).isEmpty()) {
            language = LegalDocumentLanguage.GERMAN;
        }

        return readLegalDocument(language, type);

    }

    private LegalDocument readLegalDocument(LegalDocumentLanguage language, LegalDocumentType type) {
        String legalDocumentText;
        try {
            legalDocumentText = Files.readString(getLegalDocumentPath(language).get());
        }
        catch (IOException e) {
            log.error("Could not read {} file for language {}", type, language);
            throw new InternalServerErrorException("Could not read " + type + " file for language " + language);
        }
        return type == LegalDocumentType.PRIVACY_STATEMENT ? new PrivacyStatement(legalDocumentText, language) : new Imprint(legalDocumentText, language);
    }

    protected LegalDocument updateLegalDocument(LegalDocument legalDocument) {
        if (legalDocument.getText().isBlank()) {
            throw new BadRequestAlertException("Legal document text cannot be empty", legalDocument.getType().name(), "emptyLegalDocument");
        }
        try {
            Files.writeString(getLegalDocumentPath(legalDocument.getLanguage(), true).get(), legalDocument.getText(), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        }
        catch (IOException e) {
            log.error("Could not update {} file for language {}", legalDocument.getType(), legalDocument.getLanguage());
            throw new InternalServerErrorException("Could not update " + legalDocument.getType() + " file for language " + legalDocument.getLanguage());
        }
        return legalDocument;
    }

    private Optional<Path> getLegalDocumentPath(LegalDocumentLanguage language, boolean isUpdate) {
        var filePath = getLegalDocumentPathForType(language);
        if (Files.exists(getLegalDocumentPathForType(language))) {
            return Optional.of(filePath);
        }
        // if it is an update, we need the path to create the file
        return isUpdate ? Optional.of(filePath) : Optional.empty();
    }

    private Optional<Path> getLegalDocumentPath(LegalDocumentLanguage language) {
        return getLegalDocumentPath(language, false);
    }

    protected abstract Path getLegalDocumentPathForType(LegalDocumentLanguage language);

}
