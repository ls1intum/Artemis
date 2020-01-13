package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.domain.view.QuizView;
import de.tum.in.www1.artemis.repository.*;

@Service
public class QuizExerciseService {

    private final Logger log = LoggerFactory.getLogger(QuizExerciseService.class);

    private final QuizExerciseRepository quizExerciseRepository;

    private final DragAndDropMappingRepository dragAndDropMappingRepository;

    private final ShortAnswerMappingRepository shortAnswerMappingRepository;

    private final AuthorizationCheckService authCheckService;

    private final ResultRepository resultRepository;

    private final QuizSubmissionRepository quizSubmissionRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    private final UserService userService;

    private final ObjectMapper objectMapper;

    public QuizExerciseService(UserService userService, QuizExerciseRepository quizExerciseRepository, DragAndDropMappingRepository dragAndDropMappingRepository,
            ShortAnswerMappingRepository shortAnswerMappingRepository, AuthorizationCheckService authCheckService, ResultRepository resultRepository,
            QuizSubmissionRepository quizSubmissionRepository, SimpMessageSendingOperations messagingTemplate,
            MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter) {
        this.userService = userService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.dragAndDropMappingRepository = dragAndDropMappingRepository;
        this.shortAnswerMappingRepository = shortAnswerMappingRepository;
        this.authCheckService = authCheckService;
        this.resultRepository = resultRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = mappingJackson2HttpMessageConverter.getObjectMapper();
    }

    /**
     * Save the given quizExercise to the database and make sure that objects with references to one another are saved in the correct order to avoid PersistencyExceptions
     *
     * @param quizExercise the quiz exercise to save
     * @return the saved quiz exercise
     */
    public QuizExercise save(QuizExercise quizExercise) {
        log.debug("Request to save QuizExercise : {}", quizExercise);

        // fix references in all drag and drop and short answer questions (step 1/2)
        for (var quizQuestion : quizExercise.getQuizQuestions()) {
            if (quizQuestion instanceof MultipleChoiceQuestion) {
                var multipleChoiceQuestion = (MultipleChoiceQuestion) quizQuestion;
                var quizQuestionStatistic = (MultipleChoiceQuestionStatistic) multipleChoiceQuestion.getQuizQuestionStatistic();
                if (quizQuestionStatistic == null) {
                    quizQuestionStatistic = new MultipleChoiceQuestionStatistic();
                    multipleChoiceQuestion.setQuizQuestionStatistic(quizQuestionStatistic);
                }

                for (var answerOption : multipleChoiceQuestion.getAnswerOptions()) {
                    quizQuestionStatistic.addAnswerOption(answerOption);
                }

                // if an answerOption was removed then remove the associated AnswerCounters implicitly
                Set<AnswerCounter> answerCounterToDelete = new HashSet<>();
                for (AnswerCounter answerCounter : quizQuestionStatistic.getAnswerCounters()) {
                    if (answerCounter.getId() != null) {
                        if (!(multipleChoiceQuestion.getAnswerOptions().contains(answerCounter.getAnswer()))) {
                            answerCounter.setAnswer(null);
                            answerCounterToDelete.add(answerCounter);
                        }
                    }
                }
                quizQuestionStatistic.getAnswerCounters().removeAll(answerCounterToDelete);
            }
            else if (quizQuestion instanceof DragAndDropQuestion) {
                var dragAndDropQuestion = (DragAndDropQuestion) quizQuestion;
                var quizQuestionStatistic = (DragAndDropQuestionStatistic) dragAndDropQuestion.getQuizQuestionStatistic();
                if (quizQuestionStatistic == null) {
                    quizQuestionStatistic = new DragAndDropQuestionStatistic();
                    dragAndDropQuestion.setQuizQuestionStatistic(quizQuestionStatistic);
                }

                for (var dropLocation : dragAndDropQuestion.getDropLocations()) {
                    quizQuestionStatistic.addDropLocation(dropLocation);
                }

                // if a dropLocation was removed then remove the associated AnswerCounters implicitly
                Set<DropLocationCounter> dropLocationCounterToDelete = new HashSet<>();
                for (DropLocationCounter dropLocationCounter : quizQuestionStatistic.getDropLocationCounters()) {
                    if (dropLocationCounter.getId() != null) {
                        if (!(dragAndDropQuestion.getDropLocations().contains(dropLocationCounter.getDropLocation()))) {
                            dropLocationCounter.setDropLocation(null);
                            dropLocationCounterToDelete.add(dropLocationCounter);
                        }
                    }
                }
                quizQuestionStatistic.getDropLocationCounters().removeAll(dropLocationCounterToDelete);

                // save references as index to prevent Hibernate Persistence problem
                saveCorrectMappingsInIndices(dragAndDropQuestion);
            }
            else if (quizQuestion instanceof ShortAnswerQuestion) {
                var shortAnswerQuestion = (ShortAnswerQuestion) quizQuestion;
                var quizQuestionStatistic = (ShortAnswerQuestionStatistic) shortAnswerQuestion.getQuizQuestionStatistic();
                if (quizQuestionStatistic == null) {
                    quizQuestionStatistic = new ShortAnswerQuestionStatistic();
                    shortAnswerQuestion.setQuizQuestionStatistic(quizQuestionStatistic);
                }

                for (var spot : shortAnswerQuestion.getSpots()) {
                    quizQuestionStatistic.addSpot(spot);
                }

                // if a spot was removed then remove the associated spotCounters implicitly
                Set<ShortAnswerSpotCounter> spotCounterToDelete = new HashSet<>();
                for (ShortAnswerSpotCounter spotCounter : quizQuestionStatistic.getShortAnswerSpotCounters()) {
                    if (spotCounter.getId() != null) {
                        if (!(shortAnswerQuestion.getSpots().contains(spotCounter.getSpot()))) {
                            spotCounter.setSpot(null);
                            spotCounterToDelete.add(spotCounter);
                        }
                    }
                }
                quizQuestionStatistic.getShortAnswerSpotCounters().removeAll(spotCounterToDelete);

                // save references as index to prevent Hibernate Persistence problem
                saveCorrectMappingsInIndicesShortAnswer(shortAnswerQuestion);
            }
        }

        // Note: save will automatically remove deleted questions from the exercise and deleted answer options from the questions
        // and delete the now orphaned entries from the database
        quizExercise = quizExerciseRepository.save(quizExercise);

        // fix references in all drag and drop questions and short answer questions (step 2/2)
        for (QuizQuestion quizQuestion : quizExercise.getQuizQuestions()) {
            if (quizQuestion instanceof DragAndDropQuestion) {
                DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) quizQuestion;
                // restore references from index after save
                restoreCorrectMappingsFromIndices(dragAndDropQuestion);
            }
            else if (quizQuestion instanceof ShortAnswerQuestion) {
                ShortAnswerQuestion shortAnswerQuestion = (ShortAnswerQuestion) quizQuestion;
                // restore references from index after save
                restoreCorrectMappingsFromIndicesShortAnswer(shortAnswerQuestion);
            }
        }
        return quizExercise;
    }

    /**
     * Get all quiz exercises.
     *
     * @return the list of entities
     */
    public List<QuizExercise> findAll() {
        log.debug("REST request to get all QuizExercises");
        List<QuizExercise> quizExercises = quizExerciseRepository.findAll();
        User user = userService.getUserWithGroupsAndAuthorities();
        Stream<QuizExercise> authorizedExercises = quizExercises.stream().filter(exercise -> {
            Course course = exercise.getCourse();
            return authCheckService.isTeachingAssistantInCourse(course, user) || authCheckService.isInstructorInCourse(course, user) || authCheckService.isAdmin();
        });
        return authorizedExercises.collect(Collectors.toList());
    }

    /**
     * Get one quiz exercise by id.
     *
     * @param quizExerciseId the id of the entity
     * @return the entity
     */
    public Optional<QuizExercise> findById(Long quizExerciseId) {
        log.debug("Request to get Quiz Exercise : {}", quizExerciseId);
        return quizExerciseRepository.findById(quizExerciseId);
    }

    /**
     * Get one quiz exercise
     *
     * @param quizExerciseId the id of the entity
     * @return the entity
     */
    public QuizExercise findOne(Long quizExerciseId) {
        log.debug("Request to get Quiz Exercise : {}", quizExerciseId);
        Optional<QuizExercise> quizExercise = quizExerciseRepository.findById(quizExerciseId);
        return quizExercise.orElse(null);
    }

    /**
     * Get one quiz exercise by id and eagerly load questions
     *
     * @param quizExerciseId the id of the entity
     * @return the entity
     */
    public QuizExercise findOneWithQuestions(Long quizExerciseId) {
        log.debug("Request to get Quiz Exercise : {}", quizExerciseId);
        Optional<QuizExercise> quizExercise = quizExerciseRepository.findWithEagerQuestionsById(quizExerciseId);
        return quizExercise.orElse(null);
    }

    /**
     * Get one quiz exercise by id and eagerly load questions and statistics
     *
     * @param quizExerciseId the id of the entity
     * @return the entity
     */
    public QuizExercise findOneWithQuestionsAndStatistics(Long quizExerciseId) {
        log.debug("Request to get Quiz Exercise : {}", quizExerciseId);
        Optional<QuizExercise> optionalQuizExercise = quizExerciseRepository.findWithEagerQuestionsAndStatisticsById(quizExerciseId);
        return optionalQuizExercise.orElse(null);
    }

    /**
     * Get all quiz exercises for the given course.
     *
     * @param courseId the id of the course
     * @return the entity
     */
    public List<QuizExercise> findByCourseId(Long courseId) {
        log.debug("Request to get all Quiz Exercises in Course : {}", courseId);
        List<QuizExercise> quizExercises = quizExerciseRepository.findByCourseId(courseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        if (quizExercises.size() > 0) {
            Course course = quizExercises.get(0).getCourse();
            if (!authCheckService.isTeachingAssistantInCourse(course, user) && !authCheckService.isInstructorInCourse(course, user) && !authCheckService.isAdmin()) {
                return new LinkedList<>();
            }
        }
        return quizExercises;
    }

    /**
     * Get all quiz exercises that are planned to start in the future
     *
     * @return the list of quiz exercises
     */
    public List<QuizExercise> findAllPlannedToStartInTheFutureWithQuestions() {
        return quizExerciseRepository.findByIsPlannedToStartAndReleaseDateIsAfter(true, ZonedDateTime.now());
    }

    /**
     * adjust existing results if an answer or and question was deleted and recalculate the scores
     *
     * @param quizExercise the changed quizExercise.
     */
    public void adjustResultsOnQuizChanges(QuizExercise quizExercise) {
        // change existing results if an answer or and question was deleted
        for (Result result : resultRepository.findByParticipationExerciseIdOrderByCompletionDateAsc(quizExercise.getId())) {

            Set<SubmittedAnswer> submittedAnswersToDelete = new HashSet<>();
            QuizSubmission quizSubmission = quizSubmissionRepository.findById(result.getSubmission().getId()).get();

            for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
                // Delete all references to question and question-elements if the question was changed
                submittedAnswer.checkAndDeleteReferences(quizExercise);
                if (!quizExercise.getQuizQuestions().contains(submittedAnswer.getQuizQuestion())) {
                    submittedAnswersToDelete.add(submittedAnswer);
                }
            }
            quizSubmission.getSubmittedAnswers().removeAll(submittedAnswersToDelete);

            // recalculate existing score
            quizSubmission.calculateAndUpdateScores(quizExercise);
            // update Successful-Flag in Result
            StudentParticipation studentParticipation = (StudentParticipation) result.getParticipation();
            studentParticipation.setExercise(quizExercise);
            result.setSubmission(quizSubmission);
            result.evaluateSubmission();

            // save the updated Result and its Submission
            quizSubmissionRepository.save(quizSubmission);
            resultRepository.save(result);
        }
    }

    /**
     * Sends a QuizExercise to all subscribed clients
     * @param quizExercise the QuizExercise which will be sent
     */
    public void sendQuizExerciseToSubscribedClients(QuizExercise quizExercise) {
        try {
            long start = System.currentTimeMillis();
            Class view = viewForStudentsInQuizExercise(quizExercise);
            byte[] payload = objectMapper.writerWithView(view).writeValueAsBytes(quizExercise);
            messagingTemplate.send("/topic/quizExercise/" + quizExercise.getId(), MessageBuilder.withPayload(payload).build());
            log.info("    sent out quizExercise to all listening clients in {} ms", System.currentTimeMillis() - start);
        }
        catch (JsonProcessingException e) {
            log.error("Exception occurred while serializing quiz exercise", e);
        }
    }

    /**
     * Check if the current user has at least TA-level permissions for the given exercise
     *
     * @param quizExercise the exercise to check permissions for
     * @return true, if the user has the required permissions, false otherwise
     */
    public boolean userHasTAPermissions(QuizExercise quizExercise) {
        Course course = quizExercise.getCourse();
        User user = userService.getUserWithGroupsAndAuthorities();
        return authCheckService.isTeachingAssistantInCourse(course, user) || authCheckService.isInstructorInCourse(course, user) || authCheckService.isAdmin();
    }

    /**
     * get the view for students in the given quiz
     * 
     * @param quizExercise the quiz to get the view for
     * @return the view depending on the current state of the quiz
     */
    public Class viewForStudentsInQuizExercise(QuizExercise quizExercise) {
        if (!quizExercise.isStarted()) {
            return QuizView.Before.class;
        }
        else if (quizExercise.isSubmissionAllowed()) {
            return QuizView.During.class;
        }
        else {
            return QuizView.After.class;
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

            // drop location index
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
     * remove solutions and spots from correct mappings and set solutionIndex and spotIndex instead
     *
     * @param shortAnswerQuestion the question for which to perform these actions
     */
    private void saveCorrectMappingsInIndicesShortAnswer(ShortAnswerQuestion shortAnswerQuestion) {
        List<ShortAnswerMapping> mappingsToBeRemoved = new ArrayList<>();
        for (ShortAnswerMapping mapping : shortAnswerQuestion.getCorrectMappings()) {
            // check for NullPointers
            if (mapping.getSolution() == null || mapping.getSpot() == null) {
                mappingsToBeRemoved.add(mapping);
                continue;
            }

            // solution index
            ShortAnswerSolution solution = mapping.getSolution();
            boolean solutionFound = false;
            for (ShortAnswerSolution questionSolution : shortAnswerQuestion.getSolutions()) {
                if (solution.equals(questionSolution)) {
                    solutionFound = true;
                    mapping.setShortAnswerSolutionIndex(shortAnswerQuestion.getSolutions().indexOf(questionSolution));
                    mapping.setSolution(null);
                    break;
                }
            }

            // replace spot
            ShortAnswerSpot spot = mapping.getSpot();
            boolean spotFound = false;
            for (ShortAnswerSpot questionSpot : shortAnswerQuestion.getSpots()) {
                if (spot.equals(questionSpot)) {
                    spotFound = true;
                    mapping.setShortAnswerSpotIndex(shortAnswerQuestion.getSpots().indexOf(questionSpot));
                    mapping.setSpot(null);
                    break;
                }
            }

            // if one of them couldn't be found, remove the mapping entirely
            if (!solutionFound || !spotFound) {
                mappingsToBeRemoved.add(mapping);
            }
        }

        for (ShortAnswerMapping mapping : mappingsToBeRemoved) {
            shortAnswerQuestion.removeCorrectMappings(mapping);
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

    /**
     * restore solution and spots for correct mappings using solutionIndex and spotIndex
     *
     * @param shortAnswerQuestion the question for which to perform these actions
     */
    private void restoreCorrectMappingsFromIndicesShortAnswer(ShortAnswerQuestion shortAnswerQuestion) {
        for (ShortAnswerMapping mapping : shortAnswerQuestion.getCorrectMappings()) {
            // solution
            mapping.setSolution(shortAnswerQuestion.getSolutions().get(mapping.getShortAnswerSolutionIndex()));
            // spot
            mapping.setSpot(shortAnswerQuestion.getSpots().get(mapping.getShortAnswerSpotIndex()));
            // set question
            mapping.setQuestion(shortAnswerQuestion);
            // save mapping
            shortAnswerMappingRepository.save(mapping);
        }
    }
}
