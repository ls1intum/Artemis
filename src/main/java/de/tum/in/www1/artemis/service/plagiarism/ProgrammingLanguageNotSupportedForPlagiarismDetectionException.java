package de.tum.in.www1.artemis.service.plagiarism;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

public class ProgrammingLanguageNotSupportedForPlagiarismDetectionException extends Exception {

    ProgrammingLanguageNotSupportedForPlagiarismDetectionException(ProgrammingLanguage language) {
        super("Artemis does not support plagiarism checks for the programming language " + language);
    }
}
