package de.tum.cit.aet.artemis.plagiarism.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.plagiarism.exception.PlagiarismApiNotPresentException;
import de.tum.cit.aet.artemis.plagiarism.exception.ProgrammingLanguageNotSupportedForPlagiarismDetectionException;
import de.tum.cit.aet.artemis.plagiarism.web.PlagiarismResultResponseBuilder;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleAccessArchitectureTest;

class PlagiarismApiArchitectureTest extends AbstractModuleAccessArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".plagiarism";
    }

    @Override
    protected Set<Class<?>> getIgnoredClasses() {
        return Set.of(ProgrammingLanguageNotSupportedForPlagiarismDetectionException.class, PlagiarismResultResponseBuilder.class, PlagiarismApiNotPresentException.class);
    }
}
