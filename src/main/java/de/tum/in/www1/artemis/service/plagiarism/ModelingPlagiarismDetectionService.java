package de.tum.in.www1.artemis.service.plagiarism;

import static com.google.gson.JsonParser.parseString;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.io.IOException;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.service.compass.controller.UMLModelParser;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.web.rest.dto.ModelingSubmissionComparisonDTO;

@Service
public class ModelingPlagiarismDetectionService {

    private final Logger log = LoggerFactory.getLogger(ModelingPlagiarismDetectionService.class);

    /**
     * Convenience method to extract all latest submissions from a ModelingExercise and compute pair-wise distances.
     *
     * @param exerciseWithParticipationsSubmissionsResults Text Exercise with fetched participations and submissions
     * @param minimumSimilarity the minimum similarity so that the result is considered
     * @return List of submission id pairs and similarity score
     */
    public List<ModelingSubmissionComparisonDTO> compareSubmissions(ModelingExercise exerciseWithParticipationsSubmissionsResults, double minimumSimilarity) {
        final List<ModelingSubmission> modelingSubmissions = modelingSubmissionsForComparison(exerciseWithParticipationsSubmissionsResults);
        log.info("Found " + modelingSubmissions.size() + " modeling submissions in exercise " + exerciseWithParticipationsSubmissionsResults.getId());
        return compareSubmissions(modelingSubmissions, minimumSimilarity);
    }

    /**
     * Pairwise comparison of text submissions using a TextComparisonStrategy
     *
     * @param modelingSubmissions List of modeling submissions
     * @param minimumSimilarity the minimum similarity so that the result is considered
     * @return List of submission id pairs and similarity score
     */
    public List<ModelingSubmissionComparisonDTO> compareSubmissions(List<ModelingSubmission> modelingSubmissions, double minimumSimilarity) {

        Map<UMLDiagram, ModelingSubmission> nonEmptyModels = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();

        for (var modelingSubmission : modelingSubmissions) {
            if (!modelingSubmission.isEmpty(objectMapper)) {
                try {
                    log.debug("Build UML diagram from json");
                    UMLDiagram model = UMLModelParser.buildModelFromJSON(parseString(modelingSubmission.getModel()).getAsJsonObject(), modelingSubmission.getId());
                    if (model.getAllModelElements().size() > 0) {
                        nonEmptyModels.put(model, modelingSubmission);
                    }
                }
                catch (IOException e) {
                    log.error("Parsing the modeling submission " + modelingSubmission.getId() + " did throw an exception:", e);
                }
            }
        }

        log.info("Found " + nonEmptyModels.size() + " non empty modeling submissions to compare");

        final List<ModelingSubmissionComparisonDTO> comparisonResults = new ArrayList<>();

        var nonEmptyModelsList = new ArrayList<>(nonEmptyModels.keySet());

        // it is intended to use the classic for loop here, because we only want to check similarity between two different submissions once
        for (int i = 0; i < nonEmptyModelsList.size(); i++) {
            for (int j = i + 1; j < nonEmptyModelsList.size(); j++) {
                var model1 = nonEmptyModelsList.get(i);
                var model2 = nonEmptyModelsList.get(j);
                final double similarity = model1.similarity(model2);
                log.debug("Compare result " + i + " with " + j + ": " + similarity);
                if (similarity >= minimumSimilarity) {
                    log.info("Found similar models " + i + " with " + j + ": " + similarity);
                    var modelingSubmissionComparisonResult = new ModelingSubmissionComparisonDTO();
                    var submission1 = nonEmptyModels.get(model1);
                    var submission2 = nonEmptyModels.get(model2);
                    modelingSubmissionComparisonResult.submissionId1(submission1.getId());
                    modelingSubmissionComparisonResult.submissionId2(submission2.getId());
                    modelingSubmissionComparisonResult.size1(model1.getAllModelElements().size());
                    modelingSubmissionComparisonResult.size2(model2.getAllModelElements().size());
                    modelingSubmissionComparisonResult.similarity(similarity);
                    if (submission1.getResult() != null) {
                        modelingSubmissionComparisonResult.score1(submission1.getResult().getScore());
                    }
                    if (submission2.getResult() != null) {
                        modelingSubmissionComparisonResult.score2(submission2.getResult().getScore());
                    }

                    comparisonResults.add(modelingSubmissionComparisonResult);
                }
            }
        }

        log.info("Found " + comparisonResults.size() + " similar modeling submission combinations ( > " + minimumSimilarity + ")");

        return comparisonResults;
    }

    /**
     * Reduce a ModelingExercise Object to a list of latest modeling submissions.
     *
     * @param exerciseWithParticipationsAndSubmissions ModelingExercise with fetched participations and submissions
     * @return List containing the latest modeling submission for every participation
     */
    public List<ModelingSubmission> modelingSubmissionsForComparison(ModelingExercise exerciseWithParticipationsAndSubmissions) {
        return exerciseWithParticipationsAndSubmissions.getStudentParticipations().parallelStream().map(Participation::findLatestSubmission).filter(Optional::isPresent)
                .map(Optional::get).filter(submission -> submission instanceof ModelingSubmission).map(submission -> (ModelingSubmission) submission).collect(toUnmodifiableList());
    }

}
