package de.tum.in.www1.artemis.service.plagiarism;

import static com.google.gson.JsonParser.parseString;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmission;
import de.tum.in.www1.artemis.domain.plagiarism.modeling.ModelingPlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.modeling.ModelingSubmissionElement;
import de.tum.in.www1.artemis.service.compass.controller.UMLModelParser;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;

@Service
public class ModelingPlagiarismDetectionService {

    private final Logger log = LoggerFactory.getLogger(ModelingPlagiarismDetectionService.class);

    /**
     * Convenience method to extract all latest submissions from a ModelingExercise and compute
     * pair-wise distances.
     *
     * @param exerciseWithParticipationsSubmissionsResults Text Exercise with fetched participations
     *                                                     and submissions
     * @param minimumSimilarity                            the minimum similarity so that the result
     *                                                     is considered
     * @param minimumModelSize                             the minimum number of model elements to
     *                                                     be considered as plagiarism
     * @param minimumScore                                 the minimum result score (if available)
     *                                                     to be considered as plagiarism
     * @return List of submission id pairs and similarity score
     */
    public ModelingPlagiarismResult compareSubmissions(ModelingExercise exerciseWithParticipationsSubmissionsResults, double minimumSimilarity, int minimumModelSize,
            int minimumScore) {
        final List<ModelingSubmission> modelingSubmissions = modelingSubmissionsForComparison(exerciseWithParticipationsSubmissionsResults);

        log.info("Found " + modelingSubmissions.size() + " modeling submissions in exercise " + exerciseWithParticipationsSubmissionsResults.getId());

        ModelingPlagiarismResult result = compareSubmissions(modelingSubmissions, minimumSimilarity, minimumModelSize, minimumScore);

        result.setExerciseId(exerciseWithParticipationsSubmissionsResults.getId());

        return result;
    }

    /**
     * Calculate the similarity distribution of the given comparisons.
     */
    private int[] calculateSimilarityDistribution(List<PlagiarismComparison<ModelingSubmissionElement>> comparisons) {
        int[] similarityDistribution = new int[10];

        comparisons.stream().map(PlagiarismComparison::getSimilarity).map(percent -> percent / 10).map(Double::intValue).map(index -> index == 10 ? 9 : index)
                .forEach(index -> similarityDistribution[index]++);

        return similarityDistribution;
    }

    /**
     * Pairwise comparison of text submissions using a TextComparisonStrategy
     *
     * @param modelingSubmissions List of modeling submissions
     * @param minimumSimilarity   the minimum similarity so that the result is considered
     * @param minimumModelSize    the minimum number of model elements to be considered as
     *                            plagiarism
     * @param minimumScore        the minimum result score (if available) to be considered as
     *                            plagiarism
     * @return List of submission id pairs and similarity score
     */
    public ModelingPlagiarismResult compareSubmissions(List<ModelingSubmission> modelingSubmissions, double minimumSimilarity, int minimumModelSize, int minimumScore) {
        ModelingPlagiarismResult result = new ModelingPlagiarismResult();

        Map<UMLDiagram, ModelingSubmission> models = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();

        modelingSubmissions.stream().filter(modelingSubmission -> !modelingSubmission.isEmpty(objectMapper))
                .filter(modelingSubmission -> minimumScore == 0 || modelingSubmission.getLatestResult() != null && modelingSubmission.getLatestResult().getScore() != null
                        && modelingSubmission.getLatestResult().getScore() >= minimumScore)
                .forEach(modelingSubmission -> {
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
                });

        log.info(String.format("Found %d modeling submissions with at least %d elements to compare", models.size(), minimumModelSize));

        List<PlagiarismComparison<ModelingSubmissionElement>> comparisons = new ArrayList<>();
        List<UMLDiagram> nonEmptyDiagrams = new ArrayList<>(models.keySet());

        long timeBeforeStartInMillis = System.currentTimeMillis();

        // It is intended to use the classic for loop here, because we only want to check
        // similarity between two different submissions once
        for (int i = 0; i < nonEmptyDiagrams.size(); i++) {
            for (int j = i + 1; j < nonEmptyDiagrams.size(); j++) {
                UMLDiagram model1 = nonEmptyDiagrams.get(i);
                UMLDiagram model2 = nonEmptyDiagrams.get(j);

                final double similarity = model1.similarity(model2);
                log.debug("Compare result " + i + " with " + j + ": " + similarity);

                if (similarity < minimumSimilarity) {
                    // ignore comparison results with too small similarity
                    continue;
                }

                ModelingSubmission modelingSubmissionA = models.get(model1);
                ModelingSubmission modelingSubmissionB = models.get(model2);

                log.info("Found similar models " + i + " with " + j + ": " + similarity);

                PlagiarismSubmission<ModelingSubmissionElement> submissionA = PlagiarismSubmission.fromModelingSubmission(modelingSubmissionA);
                submissionA.setSize(model1.getAllModelElements().size());
                submissionA.setElements(model1.getAllModelElements().stream().map(ModelingSubmissionElement::fromUMLElement).collect(Collectors.toList()));

                PlagiarismSubmission<ModelingSubmissionElement> submissionB = PlagiarismSubmission.fromModelingSubmission(modelingSubmissionB);
                submissionB.setSize(model2.getAllModelElements().size());
                submissionB.setElements(model2.getAllModelElements().stream().map(ModelingSubmissionElement::fromUMLElement).collect(Collectors.toList()));

                PlagiarismComparison<ModelingSubmissionElement> comparison = new PlagiarismComparison<>();

                comparison.setSimilarity(similarity * 100);
                comparison.setSubmissionA(submissionA);
                comparison.setSubmissionB(submissionB);
                // TODO: Add matches to highlight similar modeling elements
                comparison.setMatches(new ArrayList<>());

                comparisons.add(comparison);
            }
        }

        log.info(String.format("Found %d similar modeling submission combinations (>%f)", comparisons.size(), minimumSimilarity));

        long durationInMillis = System.currentTimeMillis() - timeBeforeStartInMillis;
        int[] similarityDistribution = calculateSimilarityDistribution(comparisons);

        result.setComparisons(comparisons);
        result.setDuration(durationInMillis);
        result.setSimilarityDistribution(similarityDistribution);

        return result;
    }

    /**
     * Reduce a ModelingExercise Object to a list of latest modeling submissions.
     *
     * @param exerciseWithParticipationsAndSubmissions ModelingExercise with fetched participations
     *                                                 and submissions
     * @return List containing the latest modeling submission for every participation
     */
    public List<ModelingSubmission> modelingSubmissionsForComparison(ModelingExercise exerciseWithParticipationsAndSubmissions) {
        return exerciseWithParticipationsAndSubmissions.getStudentParticipations().parallelStream().map(Participation::findLatestSubmission).filter(Optional::isPresent)
                .map(Optional::get).filter(submission -> submission instanceof ModelingSubmission).map(submission -> (ModelingSubmission) submission).collect(toUnmodifiableList());
    }

}
