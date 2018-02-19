package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.*;
import de.tum.in.www1.exerciseapp.repository.DragAndDropMappingRepository;
import de.tum.in.www1.exerciseapp.repository.QuizExerciseRepository;
import de.tum.in.www1.exerciseapp.repository.QuizSubmissionRepository;
import de.tum.in.www1.exerciseapp.repository.ResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional
public class QuizExerciseService {

    private final Logger log = LoggerFactory.getLogger(QuizExerciseService.class);

    private final QuizExerciseRepository quizExerciseRepository;
    private final DragAndDropMappingRepository dragAndDropMappingRepository;
    private final AuthorizationCheckService authCheckService;
    private final ResultRepository resultRepository;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private final UserService userService;

    public QuizExerciseService(UserService userService,
                               QuizExerciseRepository quizExerciseRepository,
                               DragAndDropMappingRepository dragAndDropMappingRepository,
                               AuthorizationCheckService authCheckService,
                               ResultRepository resultRepository,
                               QuizSubmissionRepository quizSubmissionRepository) {
        this.userService = userService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.dragAndDropMappingRepository = dragAndDropMappingRepository;
        this.authCheckService = authCheckService;
        this.resultRepository = resultRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
    }

    /**
     * Save the given quizExercise to the database
     * and make sure that objects with references to one another
     * are saved in the correct order to avoid PersistencyExceptions
     *
     * @param quizExercise the quiz exercise to save
     * @return the saved quiz exercise including
     */
    public QuizExercise save(QuizExercise quizExercise) {
        log.debug("Request to save QuizExercise : {}", quizExercise);

        // fix references in all drag and drop questions (step 1/2)
        for (Question question : quizExercise.getQuestions()) {
            if (question instanceof DragAndDropQuestion) {
                DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) question;
                // save references as index to prevent Hibernate Persistence problem
                saveCorrectMappingsInIndices(dragAndDropQuestion);
            }
        }

        // save result
        // Note: save will automatically remove deleted questions from the exercise and deleted answer options from the questions
        //       and delete the now orphaned entries from the database
        QuizExercise result = quizExerciseRepository.save(quizExercise);

        // fix references in all drag and drop questions (step 2/2)
        for (Question question : result.getQuestions()) {
            if (question instanceof DragAndDropQuestion) {
                DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) question;
                // restore references from index after save
                restoreCorrectMappingsFromIndices(dragAndDropQuestion);
            }
        }

        return result;
    }

    /**
     * Get all quiz exercises.
     *
     * @return the list of entities
     */
    @Transactional(readOnly = true)
    public List<QuizExercise> findAll() {
        log.debug("REST request to get all QuizExercises");
        List<QuizExercise> quizExercises = quizExerciseRepository.findAll();
        User user = userService.getUserWithGroupsAndAuthorities();
        Stream<QuizExercise> authorizedExercises = quizExercises.stream().filter(
            exercise -> {
                Course course = exercise.getCourse();
                return authCheckService.isTeachingAssistantInCourse(course, user) ||
                    authCheckService.isInstructorInCourse(course, user) ||
                    authCheckService.isAdmin();
            }
        );
        return authorizedExercises.collect(Collectors.toList());
    }

    /**
     * Get one quiz exercise by id.
     *
     * @param id the id of the entity
     * @return the entity
     */
    @Transactional(readOnly = true)
    public QuizExercise findOne(Long id) {
        log.debug("Request to get Quiz Exercise : {}", id);
        return quizExerciseRepository.findOne(id);
    }

    /**
     * Get one quiz exercise by id and eagerly load questions
     *
     * @param id the id of the entity
     * @return the entity
     */
    @Transactional(readOnly = true)
    public QuizExercise findOneWithQuestions(Long id) {
        log.debug("Request to get Quiz Exercise : {}", id);
        long start = System.currentTimeMillis();
        QuizExercise quizExercise = quizExerciseRepository.findOne(id);
        log.info("    loaded quiz after {} ms", System.currentTimeMillis() - start);
        if (quizExercise != null) {
            quizExercise.getQuestions().size();
            log.info("    loaded questions after {} ms", System.currentTimeMillis() - start);
        }
        return quizExercise;
    }

    /**
     * Get one quiz exercise by id and eagerly load questions and statistics
     *
     * @param id the id of the entity
     * @return the entity
     */
    @Transactional(readOnly = true)
    public QuizExercise findOneWithQuestionsAndStatistics(Long id) {
        log.debug("Request to get Quiz Exercise : {}", id);
        long start = System.currentTimeMillis();
        QuizExercise quizExercise = quizExerciseRepository.findOne(id);
        log.info("    loaded quiz after {} ms", System.currentTimeMillis() - start);
        if (quizExercise != null) {
            quizExercise.getQuestions().size();
            log.info("    loaded questions after {} ms", System.currentTimeMillis() - start);
            quizExercise.getQuizPointStatistic().getPointCounters().size();
            log.info("    loaded quiz point statistic after {} ms", System.currentTimeMillis() - start);
            for (Question question : quizExercise.getQuestions()) {
                question.getQuestionStatistic().getRatedCorrectCounter();
            }
            log.info("    loaded question statistics after {} ms", System.currentTimeMillis() - start);
        }
        return quizExercise;
    }

    /**
     * Get all quiz exercises for the given course.
     *
     * @param courseId the id of the course
     * @return the entity
     */
    @Transactional(readOnly = true)
    public List<QuizExercise> findByCourseId(Long courseId) {
        log.debug("Request to get all Quiz Exercises in Course : {}", courseId);
        List<QuizExercise> quizExercises = quizExerciseRepository.findByCourseId(courseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        Stream<QuizExercise> authorizedExercises = quizExercises.stream().filter(
            exercise -> {
                Course course = exercise.getCourse();
                return authCheckService.isTeachingAssistantInCourse(course, user) ||
                    authCheckService.isInstructorInCourse(course, user) ||
                    authCheckService.isAdmin();
            }
        );
        return authorizedExercises.collect(Collectors.toList());
    }

    /**
     * Delete the quiz exercise by id.
     *
     * @param id the id of the entity
     */
    @Transactional
    public void delete(Long id) {
        log.debug("Request to delete Exercise : {}", id);
        quizExerciseRepository.delete(id);
    }

    /**
     * adjust existing results if an answer or and question was deleted
     *
     * @param quizExercise the changed quizExercise.
     */
    @Transactional
    public void adjustResultsOnQuizDeletions(QuizExercise quizExercise) {
        //change existing results if an answer or and question was deleted
        for (Result result : resultRepository.findByParticipationExerciseIdOrderByCompletionDateAsc(quizExercise.getId())) {

            Set<SubmittedAnswer> submittedAnswersToDelete = new HashSet<>();
            QuizSubmission quizSubmission = quizSubmissionRepository.findOne(result.getSubmission().getId());

            for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
                if (submittedAnswer instanceof MultipleChoiceSubmittedAnswer) {
                    // Delete all references to question and answers if the question was deleted
                    if (!quizExercise.getQuestions().contains(submittedAnswer.getQuestion())) {
                        submittedAnswer.setQuestion(null);
                        ((MultipleChoiceSubmittedAnswer) submittedAnswer).setSelectedOptions(null);
                        submittedAnswersToDelete.add(submittedAnswer);
                    } else {
                        // find same question in quizExercise
                        Question question = quizExercise.findQuestionById(submittedAnswer.getQuestion().getId());

                        // Check if an answerOption was deleted and delete reference to in selectedOptions
                        ((MultipleChoiceSubmittedAnswer) submittedAnswer).checkForDeletedAnswerOptions((MultipleChoiceQuestion) question);
                    }
                    // TODO: @Moritz: DragAndDrop Question
                }
            }
            quizSubmission.getSubmittedAnswers().removeAll(submittedAnswersToDelete);
            quizSubmissionRepository.save(quizSubmission);
        }
    }

    /**
     * remove dragItem and dropLocation from correct mappings and set dragItemIndex and dropLocationIndex instead
     *
     * @param dragAndDropQuestion the question for which to perform these actions
     */
    private void saveCorrectMappingsInIndices(DragAndDropQuestion dragAndDropQuestion) {
        List<DragAndDropMapping> mappingsToBeRemoved = new ArrayList<>();
        for (DragAndDropMapping mapping : dragAndDropQuestion.getCorrectMappings()) {
            // check for NullPointers
            if (mapping.getDragItem() == null || mapping.getDropLocation() == null) {
                mappingsToBeRemoved.add(mapping);
                continue;
            }

            // drag item index
            DragItem dragItem = mapping.getDragItem();
            boolean dragItemFound = false;
            for (DragItem questionDragItem : dragAndDropQuestion.getDragItems()) {
                if (dragItem.equals(questionDragItem)) {
                    dragItemFound = true;
                    mapping.setDragItemIndex(dragAndDropQuestion.getDragItems().indexOf(questionDragItem));
                    mapping.setDragItem(null);
                    break;
                }
            }

            // replace drop location
            DropLocation dropLocation = mapping.getDropLocation();
            boolean dropLocationFound = false;
            for (DropLocation questionDropLocation : dragAndDropQuestion.getDropLocations()) {
                if (dropLocation.equals(questionDropLocation)) {
                    dropLocationFound = true;
                    mapping.setDropLocationIndex(dragAndDropQuestion.getDropLocations().indexOf(questionDropLocation));
                    mapping.setDropLocation(null);
                    break;
                }
            }

            // if one of them couldn't be found, remove the mapping entirely
            if (!dragItemFound || !dropLocationFound) {
                mappingsToBeRemoved.add(mapping);
            }
        }

        for (DragAndDropMapping mapping : mappingsToBeRemoved) {
            dragAndDropQuestion.removeCorrectMappings(mapping);
        }
    }

    /**
     * restore dragItem and dropLocation for correct mappings using dragItemIndex and dropLocationIndex
     *
     * @param dragAndDropQuestion the question for which to perform these actions
     */
    private void restoreCorrectMappingsFromIndices(DragAndDropQuestion dragAndDropQuestion) {
        for (DragAndDropMapping mapping : dragAndDropQuestion.getCorrectMappings()) {
            // drag item
            mapping.setDragItem(dragAndDropQuestion.getDragItems().get(mapping.getDragItemIndex()));
            // drop location
            mapping.setDropLocation(dragAndDropQuestion.getDropLocations().get(mapping.getDropLocationIndex()));
            // set question
            mapping.setQuestion(dragAndDropQuestion);
            // save mapping
            dragAndDropMappingRepository.save(mapping);
        }
    }


}
