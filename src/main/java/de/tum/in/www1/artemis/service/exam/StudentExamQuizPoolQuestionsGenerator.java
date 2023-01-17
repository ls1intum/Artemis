package de.tum.in.www1.artemis.service.exam;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.tum.in.www1.artemis.domain.exam.quiz.QuizGroup;
import de.tum.in.www1.artemis.domain.quiz.QuizQuestion;

public class StudentExamQuizPoolQuestionsGenerator implements StudentExamQuizQuestionsGenerator {

    private final List<QuizGroup> quizGroups;

    private final List<QuizQuestion> quizQuestions;

    public StudentExamQuizPoolQuestionsGenerator() {
        this.quizGroups = new ArrayList<>();
        this.quizQuestions = new ArrayList<>();
    }

    public StudentExamQuizPoolQuestionsGenerator(List<QuizGroup> quizGroups, List<QuizQuestion> quizQuestions) {
        this.quizGroups = quizGroups;
        this.quizQuestions = quizQuestions;
    }

    /**
     * Generate randomly quiz questions for a student exam
     *
     * @return the list of quiz questions that are going to be assigned to a student exam
     */
    @Override
    public List<QuizQuestion> generateQuizQuestions() {
        SecureRandom random = new SecureRandom();
        List<QuizQuestion> results = new ArrayList<>();
        Map<Long, List<QuizQuestion>> quizGroupQuestionsMap = new HashMap<>();
        for (QuizGroup quizGroup : this.quizGroups) {
            quizGroupQuestionsMap.put(quizGroup.getId(), new ArrayList<>());
        }
        for (QuizQuestion quizQuestion : this.quizQuestions) {
            if (quizQuestion.getQuizGroup() != null) {
                quizGroupQuestionsMap.get(quizQuestion.getQuizGroup().getId()).add(quizQuestion);
            }
        }
        for (QuizGroup quizGroup : this.quizGroups) {
            List<QuizQuestion> quizQuestions = quizGroupQuestionsMap.get(quizGroup.getId());
            results.add(quizQuestions.get(random.nextInt(quizQuestions.size())));
        }
        for (QuizQuestion quizQuestion : this.quizQuestions) {
            if (quizQuestion.getQuizGroup() == null) {
                results.add(quizQuestion);
            }
        }
        return results;
    }
}
