package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.repository.ResultRepository;

import java.time.ZonedDateTime;

abstract class AssessmentService {
  protected final ResultRepository resultRepository;

  public AssessmentService(ResultRepository resultRepository) {
    this.resultRepository = resultRepository;
  }

  Result submitResult(Result result, Exercise exercise, Double calculatedScore) {
    Double maxScore = exercise.getMaxScore();
    result.setRatedIfNotExceeded(exercise.getDueDate(),result.getSubmission().getSubmissionDate());
    result.setCompletionDate(ZonedDateTime.now());
    double totalScore = calculateTotalScore(calculatedScore, maxScore);
    result.setScore(totalScore, maxScore);
    result.setResultString(totalScore, maxScore);
    resultRepository.save(result);
    return result;
  }

  private double calculateTotalScore(Double calculatedScore, Double maxScore) {
    double totalScore = Math.max(0, calculatedScore);
    return (maxScore == null) ? totalScore : Math.min(totalScore, maxScore);
  }
}
