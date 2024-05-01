package de.tum.in.www1.artemis.repository.aspects;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile(PROFILE_CORE)
@Component
@Aspect
public class QuizExerciseAspect {

    // @Before("execution(* de.tum.in.www1.artemis.repository.QuizExerciseRepository.save*(..)) && args(de.tum.in.www1.artemis.domain.quiz.QuizExercise)")
    // public void beforeSavingQuizExercise(JoinPoint joinPoint) {
    // Object[] args = joinPoint.getArgs();
    // QuizExercise quizExercise = (QuizExercise) args[0];
    //
    // processQuizQuestions(quizExercise.getQuizQuestions());
    // }
    //
    // @AfterReturning(pointcut = "execution(de.tum.in.www1.artemis.domain.quiz.QuizExercise de.tum.in.www1.artemis.repository.QuizExerciseRepository.find*(..))", returning =
    // "quizExercise")
    // public void afterReturningQuizExercise(QuizExercise quizExercise) {
    // deserializeFromJSON(quizExercise.getQuizQuestions());
    // }
    //
    // @AfterReturning(pointcut = "execution(java.util.List<de.tum.in.www1.artemis.domain.quiz.QuizExercise> de.tum.in.www1.artemis.repository.QuizExerciseRepository.find*(..))",
    // returning = "quizExercises")
    // public void afterReturningQuizExerciseList(List<QuizExercise> quizExercises) {
    // for (QuizExercise quizExercise : quizExercises) {
    // deserializeFromJSON(quizExercise.getQuizQuestions());
    // }
    // }

    /**
     * Processes a list of quiz questions by converting each question into a JSON string
     * and setting it as the content of the question. This method iterates over all
     * questions in the given quiz exercise.
     *
     * @param quizQuestions List of quiz questions. It must not be null.
     * @throws RuntimeException if there is a problem in serializing the question object to JSON.
     */
    // public void processQuizQuestions(List<QuizQuestion> quizQuestions) {
    // ObjectMapper objectMapper = new ObjectMapper();
    //
    // if (quizQuestions != null) {
    // for (QuizQuestion question : quizQuestions) {
    // if (question instanceof MultipleChoiceQuestion) {
    // question.setContent(serializeToJson(((MultipleChoiceQuestion) question).getAnswerOptions(), objectMapper));
    // }
    // }
    // }
    // }

    /**
     * Serializes a QuizQuestion object to its JSON representation using the provided ObjectMapper.
     * This method is called for each individual question during the quiz processing.
     *
     * @param answerOptions
     * @param objectMapper  the ObjectMapper instance used for serialization. It must not be null.
     * @return String representing the JSON serialized form of the QuizQuestion.
     * @throws RuntimeException if JSON processing fails, encapsulating the underlying JsonProcessingException.
     */
    // public String serializeToJson(List<AnswerOptionDTO> answerOptions, ObjectMapper objectMapper) {
    // try {
    // return objectMapper.writeValueAsString(answerOptions);
    // }
    // catch (JsonProcessingException e) {
    // throw new RuntimeException("Error serializing to JSON", e);
    // }
    // }

    /**
     * Deserializes a list of QuizQuestion objects from their JSON representation.
     * Each QuizQuestion in the input list is assumed to have its content in a JSON format.
     * This method attempts to deserialize that JSON content back into QuizQuestion objects,
     * preserving the original IDs. If any JSON parsing errors occur, a RuntimeException
     * is thrown encapsulating the original exception.
     *
     * @param quizQuestions the list of QuizQuestions with JSON content to be deserialized.
     * @throws RuntimeException if any JSON parsing errors occur during the deserialization process,
     *                              encapsulating the underlying JsonProcessingException.
     */
    // public void deserializeFromJSON(List<QuizQuestion> quizQuestions) {
    // ObjectMapper objectMapper = new ObjectMapper();
    //
    // if (quizQuestions != null) {
    // for (QuizQuestion quizQuestion : quizQuestions) {
    // if (quizQuestion instanceof MultipleChoiceQuestion) {
    // try {
    // List<AnswerOptionDTO> answerOptions = objectMapper.readValue(quizQuestion.getContent(), new TypeReference<>() {
    // });
    // ((MultipleChoiceQuestion) quizQuestion).setAnswerOptions(answerOptions);
    // }
    // catch (JsonProcessingException e) {
    // throw new RuntimeException(e);
    // }
    // }
    // }
    // }
    // }
}
