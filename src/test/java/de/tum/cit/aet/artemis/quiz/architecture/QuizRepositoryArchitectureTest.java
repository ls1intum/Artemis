package de.tum.cit.aet.artemis.quiz.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleRepositoryArchitectureTest;

class QuizRepositoryArchitectureTest extends AbstractModuleRepositoryArchitectureTest {

    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".quiz";
    }

    @Override
    protected Set<String> testTransactionalExclusions() {
        return Set.of("de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository.withInitializedQuestionChildCollections(java.util.Optional)",
                "de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository.findWithEagerQuestionsAndStatisticsById(java.lang.Long)",
                "de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository.findWithEagerQuestionsAndStatisticsAndCompetenciesAndBatchesAndGradingCriteriaById(java.lang.Long)",
                "de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository.findWithEagerQuestionsById(java.lang.Long)",
                "de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository.findWithEagerQuestionsAndCompetenciesById(java.lang.Long)",
                "de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository.findForVersioningById(java.lang.Long)");
    }
}
