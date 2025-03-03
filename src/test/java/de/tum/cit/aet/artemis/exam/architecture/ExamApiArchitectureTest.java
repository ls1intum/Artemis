package de.tum.cit.aet.artemis.exam.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.quiz.service.QuizPoolService;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleAccessArchitectureTest;

class ExamApiArchitectureTest extends AbstractModuleAccessArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".exam";
    }

    @Override
    protected Set<Class<?>> getIgnoredClasses() {
        return Set.of(QuizPoolService.class); // Inheritance allowed from ExamQuizQuestionsGenerator as it literally defines an interface
    }
}
