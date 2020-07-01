package de.tum.in.www1.artemis.service;

import java.util.HashSet;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.repository.QuizSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;

@Service
public class ExamQuizService {

    static final Logger log = LoggerFactory.getLogger(ExamQuizService.class);

    private final StudentParticipationRepository studentParticipationRepository;

    private final ResultRepository resultRepository;

    private final QuizSubmissionRepository quizSubmissionRepository;

    private final QuizExerciseService quizExerciseService;

    private final QuizStatisticService quizStatisticService;

    public ExamQuizService(StudentParticipationRepository studentParticipationRepository, ResultRepository resultRepository, QuizSubmissionRepository quizSubmissionRepository,
            QuizExerciseService quizExerciseService, QuizStatisticService quizStatisticService) {
        this.studentParticipationRepository = studentParticipationRepository;
        this.resultRepository = resultRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.quizExerciseService = quizExerciseService;
        this.quizStatisticService = quizStatisticService;
    }

    // TODO: @sleiss JavaDoc
    public void evaluateQuiz(@NotNull QuizExercise quizExercise) {
        quizExercise = quizExerciseService.findOneWithQuestionsAndStatisticsAndParticipations(quizExercise.getId());
        Set<Result> createdResults = evaluateSubmissionsAndSaveInDB(quizExercise);
        quizStatisticService.updateStatistics(createdResults, quizExercise);
    }

    // TODO: @sleiss JavaDoc
    private Set<Result> evaluateSubmissionsAndSaveInDB(@NotNull QuizExercise quizExercise) {
        Set<Result> createdResults = new HashSet<>();
        Set<StudentParticipation> studentParticipations = quizExercise.getStudentParticipations();

        for (var participation : studentParticipations) {
            try {
                Set<Submission> submissions = participation.getSubmissions();
                QuizSubmission quizSubmission;
                if (submissions.size() == 0) {
                    log.warn("Found no submissions for participation {} (Participant {}) in quiz {}", participation.getId(), participation.getParticipant().getName(),
                            quizExercise.getId());
                    continue;
                }
                else if (submissions.size() > 1) {
                    log.warn("Found more than 1 submission for participation {} (Participant {}) in quiz {}", participation.getId(), participation.getParticipant().getName(),
                            quizExercise.getId());
                    quizSubmission = (QuizSubmission) submissions.iterator().next();
                }
                else {
                    quizSubmission = (QuizSubmission) submissions.iterator().next();
                }
                quizSubmission.setType(SubmissionType.TIMEOUT);

                // Create Participation and Result and save to Database (DB Write)
                // Remove processed Submissions from SubmissionHashMap and write Participations with Result into ParticipationHashMap and Results into ResultHashMap

                participation.setInitializationState(InitializationState.FINISHED);

                // create new result
                Result result = new Result().participation(participation).submission(quizSubmission);
                result.setRated(true);
                result.setAssessmentType(AssessmentType.AUTOMATIC);
                result.setCompletionDate(quizSubmission.getSubmissionDate());
                result.setSubmission(quizSubmission);

                // calculate scores and update result and submission accordingly
                quizSubmission.calculateAndUpdateScores(quizExercise);
                result.evaluateSubmission();

                // add result to participation
                participation.addResult(result);

                // NOTE: we save participation, submission and result here individually so that one exception (e.g. duplicated key) cannot destroy multiple student answers
                participation = studentParticipationRepository.save(participation);
                quizSubmissionRepository.save(quizSubmission);
                result = resultRepository.save(result);

                // Add result so that it can be returned (and processed later)
                createdResults.add(result);
            }
            catch (Exception e) {
                log.error("Exception in evaluateExamQuizExercise() for user {} in quiz {}: {}", participation.getParticipantIdentifier(), quizExercise.getId(), e.getMessage(), e);
            }
        }

        return createdResults;
    }
}
