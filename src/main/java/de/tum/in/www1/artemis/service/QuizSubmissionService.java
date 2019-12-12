package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.repository.QuizSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.scheduled.QuizScheduleService;

@Service
public class QuizSubmissionService {

    private final QuizSubmissionRepository quizSubmissionRepository;

    private final ResultRepository resultRepository;

    public QuizSubmissionService(QuizSubmissionRepository quizSubmissionRepository, ResultRepository resultRepository) {
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.resultRepository = resultRepository;
    }

    public QuizSubmission findOne(Long id) {
        return quizSubmissionRepository.findById(id).get();
    }

    public List<QuizSubmission> findAll() {
        return quizSubmissionRepository.findAll();
    }

    public void delete(Long id) {
        quizSubmissionRepository.deleteById(id);
    }

    /**
     * Submit the given submission for practice
     *
     * @param quizSubmission the submission to submit
     * @param quizExercise   the exercise to submit in
     * @param participation  the participation where the result should be saved
     * @return the result entity
     */
    public Result submitForPractice(QuizSubmission quizSubmission, QuizExercise quizExercise, Participation participation) {
        // update submission properties
        quizSubmission.setSubmitted(true);
        quizSubmission.setType(SubmissionType.MANUAL);
        quizSubmission.setSubmissionDate(ZonedDateTime.now());

        // calculate scores
        quizSubmission.calculateAndUpdateScores(quizExercise);

        // create and save result
        Result result = new Result().participation(participation);
        result = resultRepository.save(result);
        result.setSubmission(quizSubmission);
        result.setRated(false);
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        result.setCompletionDate(ZonedDateTime.now());
        // calculate score and update result accordingly
        result.evaluateSubmission();
        // save result
        quizSubmission.setResult(result);
        quizSubmission.setParticipation(participation);
        quizSubmissionRepository.save(quizSubmission);
        result = resultRepository.save(result);

        // result.submission and result.participation.exercise.quizQuestions turn into proxy objects after saving, so we need to set it again to prevent problems later on
        result.setSubmission(quizSubmission);
        result.setParticipation(participation);

        // add result to statistics
        QuizScheduleService.addResultForStatisticUpdate(quizExercise.getId(), result);

        return result;
    }

}
