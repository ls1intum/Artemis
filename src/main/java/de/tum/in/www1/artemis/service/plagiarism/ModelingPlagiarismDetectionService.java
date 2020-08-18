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
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.service.compass.controller.UMLModelParser;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.web.rest.dto.ModelingSubmissionComparisonDTO;
import de.tum.in.www1.artemis.web.rest.dto.ModelingSubmissionComparisonElement;

@Service
public class ModelingPlagiarismDetectionService {

    private final Logger log = LoggerFactory.getLogger(ModelingPlagiarismDetectionService.class);

    /**
     * Convenience method to extract all latest submissions from a ModelingExercise and compute pair-wise distances.
     *
     * @param exerciseWithParticipationsSubmissionsResults Text Exercise with fetched participations and submissions
     * @param minimumSimilarity the minimum similarity so that the result is considered
     * @param minimumModelSize the miminum number of model elements to be considered as plagiarism
     * @param minimumScore the minimum result score (if available) to be considered as plagiarism
     * @return List of submission id pairs and similarity score
     */
    public List<ModelingSubmissionComparisonDTO> compareSubmissions(ModelingExercise exerciseWithParticipationsSubmissionsResults, double minimumSimilarity, int minimumModelSize,
            int minimumScore) {
        final List<ModelingSubmission> modelingSubmissions = modelingSubmissionsForComparison(exerciseWithParticipationsSubmissionsResults);
        log.info("Found " + modelingSubmissions.size() + " modeling submissions in exercise " + exerciseWithParticipationsSubmissionsResults.getId());
        return compareSubmissions(modelingSubmissions, minimumSimilarity, minimumModelSize, minimumScore);
    }

    /**
     * Pairwise comparison of text submissions using a TextComparisonStrategy
     *
     * @param modelingSubmissions List of modeling submissions
     * @param minimumSimilarity the minimum similarity so that the result is considered
     * @param minimumModelSize the miminum number of model elements to be considered as plagiarism
     * @param minimumScore the minimum result score (if available) to be considered as plagiarism
     * @return List of submission id pairs and similarity score
     */
    public List<ModelingSubmissionComparisonDTO> compareSubmissions(List<ModelingSubmission> modelingSubmissions, double minimumSimilarity, int minimumModelSize,
            int minimumScore) {

        Map<UMLDiagram, ModelingSubmission> models = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();

        for (var modelingSubmission : modelingSubmissions) {
            if (!modelingSubmission.isEmpty(objectMapper)) {
                try {
                    log.debug("Build UML diagram from json");
                    UMLDiagram model = UMLModelParser.buildModelFromJSON(parseString(modelingSubmission.getModel()).getAsJsonObject(), modelingSubmission.getId());
                    if (model.getAllModelElements().size() >= minimumModelSize) {
                        models.put(model, modelingSubmission);
                    }
                }
                catch (IOException e) {
                    log.error("Parsing the modeling submission " + modelingSubmission.getId() + " did throw an exception:", e);
                }
            }
        }

        log.info("Found " + models.size() + " modeling submissions with at least " + minimumModelSize + " elements to compare");

        final List<ModelingSubmissionComparisonDTO> comparisonResults = new ArrayList<>();

        var nonEmptyModelsList = new ArrayList<>(models.keySet());

        // it is intended to use the classic for loop here, because we only want to check similarity between two different submissions once
        for (int i = 0; i < nonEmptyModelsList.size(); i++) {
            for (int j = i + 1; j < nonEmptyModelsList.size(); j++) {
                var model1 = nonEmptyModelsList.get(i);
                var model2 = nonEmptyModelsList.get(j);
                final double similarity = model1.similarity(model2);
                log.debug("Compare result " + i + " with " + j + ": " + similarity);
                if (similarity < minimumSimilarity) {
                    // ignore comparison results with too small similarity
                    continue;
                }

                var submission1 = models.get(model1);
                var submission2 = models.get(model2);
                if (submission1.getResult() != null && submission1.getResult().getScore() != null && submission1.getResult().getScore() < minimumScore
                        && submission2.getResult() != null && submission2.getResult().getScore() != null && submission2.getResult().getScore() < minimumModelSize) {
                    // ignore comparison results with too small scores
                    continue;
                }

                log.info("Found similar models " + i + " with " + j + ": " + similarity);

                var comparisonResult = new ModelingSubmissionComparisonDTO();
                var element1 = new ModelingSubmissionComparisonElement().submissionId(submission1.getId()).size(model1.getAllModelElements().size());
                var element2 = new ModelingSubmissionComparisonElement().submissionId(submission2.getId()).size(model2.getAllModelElements().size());
                element1.studentLogin(((StudentParticipation) submission1.getParticipation()).getParticipantIdentifier());
                element2.studentLogin(((StudentParticipation) submission2.getParticipation()).getParticipantIdentifier());
                comparisonResult.setElement1(element1);
                comparisonResult.setElement2(element2);
                comparisonResult.similarity(similarity);
                if (submission1.getResult() != null) {
                    comparisonResult.getElement1().score(submission1.getResult().getScore());
                }
                if (submission2.getResult() != null) {
                    comparisonResult.getElement2().score(submission2.getResult().getScore());
                }

                comparisonResults.add(comparisonResult);
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
