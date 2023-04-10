package de.tum.in.www1.artemis.service;

import java.nio.file.Path;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Imprint;
import de.tum.in.www1.artemis.domain.LegalDocumentLanguage;
import de.tum.in.www1.artemis.domain.LegalDocumentType;

@Service
public class ImprintService extends LegalDocumentService {

    private static final String BASE_PATH = "imprints";

    private static final String IMPRINT_FILE_NAME = "imprint_";

    private static final String IMPRINT_FILE_EXTENSION = ".md";

    /**
     * Returns the imprint if you want to update it.
     * If it currently doesn't exist an empty string is returned as it will be created once the user saves it.
     *
     * @param language the language of the imprint
     * @return the imprint with the given language
     */
    public Imprint getImprintForUpdate(LegalDocumentLanguage language) {
        return (Imprint) getLegalDocumentForUpdate(language, LegalDocumentType.IMPRINT);

    }

    /**
     * Returns the privacy statement if you want to view it
     * If it currently doesn't exist in the given language, the other language is returned.
     * If it currently doesn't exist in any language, an exception is thrown.
     *
     * @param language the language of the privacy statement
     * @return the privacy statement with the given language
     */

    public Imprint getImprint(LegalDocumentLanguage language) {
        return (Imprint) getLegalDocument(language, LegalDocumentType.IMPRINT);

    }

    /**
     * Updates the imprint and saves it in the file system
     *
     * @param imprint the imprint to update
     * @return the updated imprint
     */
    public Imprint updateImprint(Imprint imprint) {
        return (Imprint) updateLegalDocument(imprint);
    }

    @Override
    protected Path getLegalDocumentPathForType(LegalDocumentLanguage language) {
        return Path.of(BASE_PATH, IMPRINT_FILE_NAME + language.getShortName() + IMPRINT_FILE_EXTENSION);
    }
}
