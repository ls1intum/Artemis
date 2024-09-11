package de.tum.cit.aet.artemis.service.quiz;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.exam.Exam;
import de.tum.cit.aet.artemis.domain.quiz.QuizGroup;
import de.tum.cit.aet.artemis.domain.quiz.QuizPool;
import de.tum.cit.aet.artemis.domain.quiz.QuizQuestion;
import de.tum.cit.aet.artemis.repository.DragAndDropMappingRepository;
import de.tum.cit.aet.artemis.repository.ExamRepository;
import de.tum.cit.aet.artemis.repository.QuizGroupRepository;
import de.tum.cit.aet.artemis.repository.QuizPoolRepository;
import de.tum.cit.aet.artemis.repository.ShortAnswerMappingRepository;
import de.tum.cit.aet.artemis.service.exam.ExamQuizQuestionsGenerator;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;

/**
 * This service contains the functions to manage QuizPool entity.
 */
@Profile(PROFILE_CORE)
@Service
public class QuizPoolService extends QuizService<QuizPool> implements ExamQuizQuestionsGenerator {

    private static final String ENTITY_NAME = "quizPool";

    private static final Logger log = LoggerFactory.getLogger(QuizPoolService.class);

    private final QuizPoolRepository quizPoolRepository;

    private final QuizGroupRepository quizGroupRepository;

    private final ExamRepository examRepository;

    public QuizPoolService(DragAndDropMappingRepository dragAndDropMappingRepository, ShortAnswerMappingRepository shortAnswerMappingRepository,
            QuizPoolRepository quizPoolRepository, QuizGroupRepository quizGroupRepository, ExamRepository examRepository) {
        super(dragAndDropMappingRepository, shortAnswerMappingRepository);
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
        quizPoolRepository.findWithEagerQuizQuestionsByExamId(examId).ifPresent(existingQuizPool -> {
            List<Long> existingQuizGroupIds = existingQuizPool.getQuizQuestions().stream().map(QuizQuestion::getQuizGroupId).filter(Objects::nonNull).toList();
            removeUnusedQuizGroup(existingQuizGroupIds, savedQuizGroups);
        });

        Map<String, Long> quizGroupNameIdMap = savedQuizGroups.stream().collect(Collectors.toMap(QuizGroup::getName, QuizGroup::getId));
        for (QuizQuestion quizQuestion : quizPool.getQuizQuestions()) {
            if (quizQuestion.getQuizGroup() != null) {
                quizQuestion.setQuizGroupId(quizGroupNameIdMap.get(quizQuestion.getQuizGroup().getName()));
            }
            else {
                quizQuestion.setQuizGroupId(null);
            }
        }
        quizPool.reconnectJSONIgnoreAttributes();

        log.debug("Save quiz pool to database: {}", quizPool);
        super.save(quizPool);

        QuizPool savedQuizPool = quizPoolRepository.findWithEagerQuizQuestionsByExamId(examId).orElseThrow(() -> new EntityNotFoundException(ENTITY_NAME, "examId=" + examId));
        savedQuizPool.setQuizGroups(savedQuizGroups);
        reassignQuizQuestion(savedQuizPool, savedQuizGroups);

        return savedQuizPool;
    }

    /**
     * Find a quiz pool (if exists) that belongs to the given exam id
     *
     * @param examId the id of the exam to be searched
     * @return optional quiz pool that belongs to the given exam id
     */
    public Optional<QuizPool> findWithQuizGroupsAndQuestionsByExamId(Long examId) {
        Optional<QuizPool> quizPoolOptional = quizPoolRepository.findWithEagerQuizQuestionsByExamId(examId);
        if (quizPoolOptional.isPresent()) {
            QuizPool quizPool = quizPoolOptional.get();
            List<Long> quizGroupIds = quizPool.getQuizQuestions().stream().map(QuizQuestion::getQuizGroupId).filter(Objects::nonNull).toList();
            List<QuizGroup> quizGroups = quizGroupRepository.findAllById(quizGroupIds);
            quizPool.setQuizGroups(quizGroups);
            reassignQuizQuestion(quizPool, quizGroups);
        }
        return quizPoolOptional;
    }

    /**
     * Find a quiz pool (if exists) that belongs to the given exam id
     *
     * @param examId the id of the exam to be searched
     * @return quiz pool that belongs to the given exam id
     */
    public Optional<QuizPool> findByExamId(Long examId) {
        return quizPoolRepository.findByExamId(examId);
    }

    /**
     * Reassign the connection between quiz question, quiz pool and quiz group
     *
     * @param quizPool   the quiz pool to be reset
     * @param quizGroups the list of quiz group to be reset
     */
    private void reassignQuizQuestion(QuizPool quizPool, List<QuizGroup> quizGroups) {
        Map<Long, QuizGroup> idQuizGroupMap = quizGroups.stream().collect(Collectors.toMap(QuizGroup::getId, Function.identity()));
        for (QuizQuestion quizQuestion : quizPool.getQuizQuestions()) {
            if (quizQuestion.getQuizGroupId() != null) {
                quizQuestion.setQuizGroup(idQuizGroupMap.get(quizQuestion.getQuizGroupId()));
            }
        }
    }

    /**
     * Remove existing groups that do not exist anymore in the updated quiz pool
     *
     * @param existingQuizGroupIds the list of existing quiz group id of the quiz pool
     * @param usedQuizGroups       the list of quiz group that are still exists in the updated quiz pool
     */
    private void removeUnusedQuizGroup(List<Long> existingQuizGroupIds, List<QuizGroup> usedQuizGroups) {
        Set<Long> usedQuizGroupIds = usedQuizGroups.stream().map(QuizGroup::getId).collect(Collectors.toSet());
        Set<Long> ids = existingQuizGroupIds.stream().filter(id -> !usedQuizGroupIds.contains(id)).collect(Collectors.toSet());
        quizGroupRepository.deleteAllById(ids);
    }

    @Override
    protected QuizPool saveAndFlush(QuizPool quizConfiguration) {
        return quizPoolRepository.saveAndFlush(quizConfiguration);
    }

    @Override
    public List<QuizQuestion> generateQuizQuestionsForExam(long examId) {
        Optional<QuizPool> quizPoolOptional = findWithQuizGroupsAndQuestionsByExamId(examId);
        if (quizPoolOptional.isPresent()) {
            QuizPool quizPool = quizPoolOptional.get();
            List<QuizGroup> quizGroups = quizPool.getQuizGroups();
            List<QuizQuestion> quizQuestions = quizPool.getQuizQuestions();

            Map<Long, List<QuizQuestion>> quizGroupQuestionsMap = getQuizQuestionsGroup(quizGroups, quizQuestions);
            return generateQuizQuestions(quizGroupQuestionsMap, quizGroups, quizQuestions);
        }
        else {
            return new ArrayList<>();
        }
    }

    private static Map<Long, List<QuizQuestion>> getQuizQuestionsGroup(List<QuizGroup> quizGroups, List<QuizQuestion> quizQuestions) {
        Map<Long, List<QuizQuestion>> quizGroupQuestionsMap = new HashMap<>();
        for (QuizGroup quizGroup : quizGroups) {
            quizGroupQuestionsMap.put(quizGroup.getId(), new ArrayList<>());
        }
        for (QuizQuestion quizQuestion : quizQuestions) {
            if (quizQuestion.getQuizGroup() != null) {
                quizGroupQuestionsMap.get(quizQuestion.getQuizGroup().getId()).add(quizQuestion);
            }
        }
        return quizGroupQuestionsMap;
    }

    private static List<QuizQuestion> generateQuizQuestions(Map<Long, List<QuizQuestion>> quizGroupQuestionsMap, List<QuizGroup> quizGroups, List<QuizQuestion> quizQuestions) {
        SecureRandom random = new SecureRandom();
        List<QuizQuestion> results = new ArrayList<>();
        for (QuizGroup quizGroup : quizGroups) {
            List<QuizQuestion> quizGroupQuestions = quizGroupQuestionsMap.get(quizGroup.getId());
            results.add(quizGroupQuestions.get(random.nextInt(quizGroupQuestions.size())));
        }
        for (QuizQuestion quizQuestion : quizQuestions) {
            if (quizQuestion.getQuizGroup() == null) {
                results.add(quizQuestion);
            }
        }
        return results;
    }
}
