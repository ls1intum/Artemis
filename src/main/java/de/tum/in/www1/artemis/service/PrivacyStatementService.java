package de.tum.in.www1.artemis.service;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.PrivacyStatement;
import de.tum.in.www1.artemis.domain.PrivacyStatementLanguage;

@Service
public class PrivacyStatementService {

    public PrivacyStatement getPrivacyStatement() {
        return new PrivacyStatement("This is the privacy statement", PrivacyStatementLanguage.ENGLISH);
    }

    public PrivacyStatement createPrivacyStatement(PrivacyStatement privacyStatement) {
        return new PrivacyStatement(privacyStatement.getPrivacyStatementText(), privacyStatement.getLanguage());

    }

    public PrivacyStatement updatePrivacyStatement(PrivacyStatement privacyStatement) {
        return new PrivacyStatement(privacyStatement.getPrivacyStatementText(), privacyStatement.getLanguage());
    }
}
