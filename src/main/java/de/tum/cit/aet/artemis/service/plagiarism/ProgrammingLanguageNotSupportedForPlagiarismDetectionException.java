package de.tum.cit.aet.artemis.service.plagiarism;

import de.tum.cit.aet.artemis.domain.enumeration.ProgrammingLanguage;

public class ProgrammingLanguageNotSupportedForPlagiarismDetectionException extends Exception {

    ProgrammingLanguageNotSupportedForPlagiarismDetectionException(ProgrammingLanguage language) {
        super("Artemis does not support plagiarism checks for the programming language " + language);
    }
}
