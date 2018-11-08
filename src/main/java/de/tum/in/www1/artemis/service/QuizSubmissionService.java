package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.QuizExercise;
import de.tum.in.www1.artemis.domain.QuizSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.QuizSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.scheduled.QuizScheduleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Service
@Transactional
public class QuizSubmissionService {

    private final QuizSubmissionRepository quizSubmissionRepository;
    private final ResultRepository resultRepository;

    public QuizSubmissionService(QuizSubmissionRepository quizSubmissionRepository,
                                 ResultRepository resultRepository) {
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.resultRepository = resultRepository;
    }

    @Transactional(readOnly = true)
    public QuizSubmission findOne(Long id) {
        return quizSubmissionRepository.findById(id).get();
    }

    @Transactional(readOnly = true)
    public List<QuizSubmission> findAll() {
        return quizSubmissionRepository.findAll();
    }

    @Transactional
    public void delete(Long id) {
        quizSubmissionRepository.deleteById(id);
    }

    /**
     * Submit the given submission for practice
     *
     * @param quizSubmission the submission to submit
     * @param quizExercise the exercise to submit in
     * @param participation the participation where the result should be saved
     * @return the result entity
     */
    @Transactional
    public Result submitForPractice(QuizSubmission quizSubmission, QuizExercise quizExercise, Participation participation) {
        // update submission properties
        quizSubmission.setSubmitted(true);
        quizSubmission.setType(SubmissionType.MANUAL);

        // calculate scores
        quizSubmission.calculateAndUpdateScores(quizExercise);

        // create and save result
        Result result = new Result().participation(participation).submission(quizSubmission);
        result.setRated(false);
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        result.setCompletionDate(ZonedDateTime.now());
        // calculate score and update result accordingly
        result.evaluateSubmission();
        // save result
        quizSubmission.setResult(result);
        quizSubmissionRepository.save(quizSubmission);
        // replace proxy with submission, because of Lazy-fetching
        result.setSubmission(quizSubmission);

        // add result to statistics
        QuizScheduleService.addResultToStatistic(quizExercise.getId(), result);

        return result;
    }

}
