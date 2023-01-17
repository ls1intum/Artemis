package de.tum.in.www1.artemis.service.exam;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.quiz.QuizGroup;
import de.tum.in.www1.artemis.domain.exam.quiz.QuizPool;
import de.tum.in.www1.artemis.domain.quiz.QuizQuestion;
import de.tum.in.www1.artemis.repository.DragAndDropMappingRepository;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.QuizGroupRepository;
import de.tum.in.www1.artemis.repository.QuizPoolRepository;
import de.tum.in.www1.artemis.repository.ShortAnswerMappingRepository;
import de.tum.in.www1.artemis.service.QuizConfiguration;
import de.tum.in.www1.artemis.service.QuizService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class QuizPoolService extends QuizService<QuizPool> {

    private static final String ENTITY_NAME = "quizPool";

    private final Logger log = LoggerFactory.getLogger(QuizPoolService.class);

    private final QuizPoolRepository quizPoolRepository;

    private final QuizGroupRepository quizGroupRepository;

    private final ExamRepository examRepository;

    public QuizPoolService(DragAndDropMappingRepository dragAndDropMappingRepository, ShortAnswerMappingRepository shortAnswerMappingRepository,
            QuizPoolRepository quizPoolRepository, QuizGroupRepository quizGroupRepository, ExamRepository examRepository) {
        super(dragAndDropMappingRepository, shortAnswerMappingRepository, null, null);
        this.quizPoolRepository = quizPoolRepository;
        this.quizGroupRepository = quizGroupRepository;
        this.examRepository = examRepository;
    }

    /**
     * Check if the given exam id is valid, then update quiz pool that belongs to the given exam id
     *
     * @param examId   the id of the exam to be checked
     * @param quizPool the quiz pool to be updated
     * @return updated quiz pool
     */
    public QuizPool update(Long examId, QuizPool quizPool) {
        Exam exam = examRepository.findByIdElseThrow(examId);

        quizPool.setExam(exam);

        if (quizPool.getQuizQuestions() == null || !quizPool.isValid()) {
            throw new BadRequestAlertException("The quiz pool is invalid", ENTITY_NAME, "invalidQuiz");
        }

        List<QuizGroup> savedQuizGroups = quizGroupRepository.saveAllAndFlush(quizPool.getQuizGroups());
        quizPoolRepository.findWithQuizQuestionsByExamId(examId).ifPresent(existingQuizPool -> removeUnusedQuizGroup(existingQuizPool.getQuizGroups(), savedQuizGroups));

        reassignQuizQuestion(quizPool, savedQuizGroups);
        quizPool.reconnectJSONIgnoreAttributes();

        log.debug("Save quiz pool to database: {}", quizPool);
        return (QuizPool) super.saveConfiguration(quizPool);
    }

    /**
     * Find a quiz pool (if exists) that belongs to the given exam id
     *
     * @param examId the id of the exam to be searched
     * @return quiz pool that belongs to the given exam id
     */
    public QuizPool findByExamId(Long examId) {
        return quizPoolRepository.findWithQuizQuestionsByExamId(examId).orElseThrow(() -> new EntityNotFoundException(ENTITY_NAME, "examId=" + examId));
    }

    /**
     * Build a generator to generate randomly quiz questions for student exams of given exam id
     *
     * @param examId the id of the exam of which the student exam belongs to
     * @return StudentExamQuizQuestionsGenerator the generator to generate randomly quiz questions that are going to be assigned to student exam
     */
    public StudentExamQuizQuestionsGenerator getStudentExamQuizQuestionsGenerator(Long examId) {
        Optional<QuizPool> quizPoolOptional = quizPoolRepository.findWithQuizQuestionsByExamId(examId);
        return quizPoolOptional.map(quizPool -> new StudentExamQuizPoolQuestionsGenerator(quizPool.getQuizGroups(), quizPool.getQuizQuestions()))
                .orElseGet(StudentExamQuizPoolQuestionsGenerator::new);
    }

    /**
     * Reassign the connection between quiz question, quiz pool and quiz group
     *
     * @param quizPool   the quiz pool to be reset
     * @param quizGroups the list of quiz group to be reset
     */
    private void reassignQuizQuestion(QuizPool quizPool, List<QuizGroup> quizGroups) {
        Map<String, QuizGroup> savedQuizGroupIndexMap = quizGroups.stream().collect(Collectors.toMap(QuizGroup::getName, quizGroup -> quizGroup));
        for (QuizQuestion quizQuestion : quizPool.getQuizQuestions()) {
            quizQuestion.setQuizPool(quizPool);
            if (quizQuestion.getQuizGroup() != null) {
                quizQuestion.setQuizGroup(savedQuizGroupIndexMap.get(quizQuestion.getQuizGroup().getName()));
            }
        }
    }

    /**
     * Remove existing groups that do not exist anymore in the updated quiz pool
     *
     * @param existingQuizGroups the list of existing quiz group of the quiz pool
     * @param usedQuizGroups     the list of quiz group that are still exists in the updated quiz pool
     */
    private void removeUnusedQuizGroup(List<QuizGroup> existingQuizGroups, List<QuizGroup> usedQuizGroups) {
        Set<Long> usedQuizGroupIds = usedQuizGroups.stream().map(QuizGroup::getId).collect(Collectors.toSet());
        List<Long> ids = existingQuizGroups.stream().map(QuizGroup::getId).filter(id -> !usedQuizGroupIds.contains(id)).toList();
        quizGroupRepository.deleteAllById(ids);
    }

    @Override
    public QuizPool saveAndFlush(QuizConfiguration quizConfiguration) {
        return quizPoolRepository.saveAndFlush((QuizPool) quizConfiguration);
    }

    @Override
    public void createQuizPointStatistic(QuizConfiguration quizConfiguration) {
        // TODO: This will be implemented when implementing statistics for Quiz Pool
    }
}
