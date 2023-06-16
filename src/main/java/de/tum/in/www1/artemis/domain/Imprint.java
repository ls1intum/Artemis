package de.tum.in.www1.artemis.domain;

import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.enumeration.LegalDocumentType;

public class Imprint extends LegalDocument {

    public Imprint(Language language) {
        super(LegalDocumentType.IMPRINT, language);
    }

    public Imprint(String imprintText, Language language) {
        super(LegalDocumentType.IMPRINT, imprintText, language);
    }
}
