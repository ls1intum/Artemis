package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.repository.ResultRepository;

import java.text.DecimalFormat;
import java.time.ZonedDateTime;

abstract class AssessmentService {
    protected final ResultRepository resultRepository;

    public AssessmentService(ResultRepository resultRepository) {
        this.resultRepository = resultRepository;
    }

    Result prepareSubmission(Result result, Exercise exercise, Double calculatedScore) {
        Boolean rated = exercise.getDueDate() == null || result.getSubmission().getSubmissionDate().isBefore(exercise.getDueDate());
        result.setRated(rated);
        result.setCompletionDate(ZonedDateTime.now());

        Double maxScore = exercise.getMaxScore();
        Double totalScore = Math.min(Math.max(0, calculatedScore), maxScore);
        double percentageScore = totalScore/maxScore * 100;
        result.setScore(Math.round(percentageScore));
        DecimalFormat formatter = new DecimalFormat("#.##"); // limit decimal places to 2
        result.setResultString(formatter.format(totalScore) + " of " + formatter.format(maxScore) + " points");
        result.setSuccessful(result.getScore() == 100L);

        resultRepository.save(result);
        return result;
    }
}
