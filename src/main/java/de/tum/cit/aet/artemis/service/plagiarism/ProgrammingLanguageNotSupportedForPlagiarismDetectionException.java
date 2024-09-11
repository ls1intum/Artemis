package de.tum.cit.aet.artemis.service.plagiarism;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

public class ProgrammingLanguageNotSupportedForPlagiarismDetectionException extends Exception {

    ProgrammingLanguageNotSupportedForPlagiarismDetectionException(ProgrammingLanguage language) {
        super("Artemis does not support plagiarism checks for the programming language " + language);
    }
}
