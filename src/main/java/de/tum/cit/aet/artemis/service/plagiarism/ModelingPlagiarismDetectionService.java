package de.tum.cit.aet.artemis.service.plagiarism;

import static com.google.gson.JsonParser.parseString;
import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.service.plagiarism.PlagiarismService.hasMinimumScore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.PlagiarismCheckState;
import de.tum.cit.aet.artemis.domain.modeling.ModelingExercise;
import de.tum.cit.aet.artemis.domain.modeling.ModelingSubmission;
import de.tum.cit.aet.artemis.domain.participation.Participation;
import de.tum.cit.aet.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.cit.aet.artemis.domain.plagiarism.PlagiarismSubmission;
import de.tum.cit.aet.artemis.domain.plagiarism.modeling.ModelingPlagiarismResult;
import de.tum.cit.aet.artemis.domain.plagiarism.modeling.ModelingSubmissionElement;
import de.tum.cit.aet.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.cit.aet.artemis.service.compass.umlmodel.parsers.UMLModelParser;
import de.tum.cit.aet.artemis.service.plagiarism.cache.PlagiarismCacheService;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;

@Profile(PROFILE_CORE)
@Service
public class ModelingPlagiarismDetectionService {

    private static final Logger log = LoggerFactory.getLogger(ModelingPlagiarismDetectionService.class);

    private final PlagiarismWebsocketService plagiarismWebsocketService;

    private final PlagiarismCacheService plagiarismCacheService;

    private final PlagiarismService plagiarismService;

    public ModelingPlagiarismDetectionService(PlagiarismWebsocketService plagiarismWebsocketService, PlagiarismCacheService plagiarismCacheService,
            PlagiarismService plagiarismService) {
        this.plagiarismWebsocketService = plagiarismWebsocketService;
        this.plagiarismCacheService = plagiarismCacheService;
        this.plagiarismService = plagiarismService;
    }

    /**
     * Convenience method to extract all latest submissions from a ModelingExercise and compute pair-wise distances.
     *
     * @param exerciseWithParticipationsSubmissionsResults Text Exercise with fetched participations and submissions
     * @param minimumSimilarity                            the minimum similarity so that the result is considered
     * @param minimumModelSize                             the minimum number of model elements to be considered as plagiarism
     * @param minimumScore                                 the minimum result score (if available) to be considered as plagiarism
     * @return List of submission id pairs and similarity score
     */
    public ModelingPlagiarismResult checkPlagiarism(ModelingExercise exerciseWithParticipationsSubmissionsResults, double minimumSimilarity, int minimumModelSize,
            int minimumScore) {
        var courseId = exerciseWithParticipationsSubmissionsResults.getCourseViaExerciseGroupOrCourseMember().getId();

        try {
            if (plagiarismCacheService.isActivePlagiarismCheck(courseId)) {
                throw new BadRequestAlertException("Only one active plagiarism check per course allowed", "PlagiarismCheck", "oneActivePlagiarismCheck");
            }
            plagiarismCacheService.setActivePlagiarismCheck(courseId);

            final List<ModelingSubmission> modelingSubmissions = modelingSubmissionsForComparison(exerciseWithParticipationsSubmissionsResults, minimumScore);

            Long exerciseId = exerciseWithParticipationsSubmissionsResults.getId();
            ModelingPlagiarismResult result = checkPlagiarism(modelingSubmissions, minimumSimilarity, minimumModelSize, exerciseId);

            result.setExercise(exerciseWithParticipationsSubmissionsResults);

            return result;
        }
        finally {
            plagiarismCacheService.setInactivePlagiarismCheck(courseId);
        }
    }

    /**
     * Calculate the similarity distribution of the given comparisons.
     */
    private int[] calculateSimilarityDistribution(Set<PlagiarismComparison<ModelingSubmissionElement>> comparisons) {
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
     * @param minimumModelSize    the minimum number of model elements to be considered as plagiarism
     * @param exerciseId          the id of the exercise for which the modeling submissions are compared
     * @return List of submission id pairs and similarity score
     */
    public ModelingPlagiarismResult checkPlagiarism(List<ModelingSubmission> modelingSubmissions, double minimumSimilarity, int minimumModelSize, Long exerciseId) {
        String topic = plagiarismWebsocketService.getModelingExercisePlagiarismCheckTopic(exerciseId);

        ModelingPlagiarismResult result = new ModelingPlagiarismResult();

        Map<UMLDiagram, ModelingSubmission> models = new HashMap<>();

        AtomicInteger processedSubmissionCount = new AtomicInteger(1);
        modelingSubmissions.forEach(modelingSubmission -> {
            String progressMessage = "Getting UML diagram for submission: " + processedSubmissionCount + "/" + modelingSubmissions.size();
            plagiarismWebsocketService.notifyInstructorAboutPlagiarismState(topic, PlagiarismCheckState.RUNNING, List.of(progressMessage));

            try {
                log.debug("Build UML diagram from json");
                UMLDiagram model = UMLModelParser.buildModelFromJSON(parseString(modelingSubmission.getModel()).getAsJsonObject(), modelingSubmission.getId());

                if (model.getAllModelElements().size() >= minimumModelSize) {
                    models.put(model, modelingSubmission);
                }
            }
            catch (IOException e) {
                log.error("Parsing the modeling submission {} did throw an exception:", modelingSubmission.getId(), e);
            }

            processedSubmissionCount.getAndIncrement();
        });

        log.info("Found {} modeling submissions with at least {} elements to compare", models.size(), minimumModelSize);

        Set<PlagiarismComparison<ModelingSubmissionElement>> comparisons = new HashSet<>();
        List<UMLDiagram> nonEmptyDiagrams = new ArrayList<>(models.keySet());

        long timeBeforeStartInMillis = System.currentTimeMillis();

        // It is intended to use the classic for loop here, because we only want to check
        // similarity between two different submissions once
        for (int i = 0; i < nonEmptyDiagrams.size(); i++) {
            String progressMessage = "Comparing submissions: " + (i + 1) + "/" + nonEmptyDiagrams.size();
            plagiarismWebsocketService.notifyInstructorAboutPlagiarismState(topic, PlagiarismCheckState.RUNNING, List.of(progressMessage));

            for (int j = i + 1; j < nonEmptyDiagrams.size(); j++) {

                UMLDiagram model1 = nonEmptyDiagrams.get(i);
                UMLDiagram model2 = nonEmptyDiagrams.get(j);

                final double similarity = model1.similarity(model2);
                log.debug("Compare result {} with {}: {}", i, j, similarity);

                if (similarity * 100 < minimumSimilarity) {
                    // ignore comparison results with too small similarity
                    continue;
                }

                ModelingSubmission modelingSubmissionA = models.get(model1);
                ModelingSubmission modelingSubmissionB = models.get(model2);

                log.info("Found similar models {} with {}: {}", i, j, similarity);

                PlagiarismSubmission<ModelingSubmissionElement> submissionA = PlagiarismSubmission.fromModelingSubmission(modelingSubmissionA);
                submissionA.setSize(model1.getAllModelElements().size());
                submissionA.setElements(model1.getAllModelElements().stream().map(ModelingSubmissionElement::fromUMLElement).toList());

                PlagiarismSubmission<ModelingSubmissionElement> submissionB = PlagiarismSubmission.fromModelingSubmission(modelingSubmissionB);
                submissionB.setSize(model2.getAllModelElements().size());
                submissionB.setElements(model2.getAllModelElements().stream().map(ModelingSubmissionElement::fromUMLElement).toList());

                PlagiarismComparison<ModelingSubmissionElement> comparison = new PlagiarismComparison<>();

                comparison.setPlagiarismResult(result);
                comparison.setSimilarity(similarity * 100);
                comparison.setSubmissionA(submissionA);
                comparison.setSubmissionB(submissionB);
                // TODO: Add matches to highlight similar modeling elements
                comparison.setMatches(new HashSet<>());

                comparisons.add(comparison);
            }
        }

        log.info("Found {} similar modeling submission combinations (>{})", comparisons.size(), minimumSimilarity);
        plagiarismWebsocketService.notifyInstructorAboutPlagiarismState(topic, PlagiarismCheckState.COMPLETED, List.of());

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
     * @param exerciseWithParticipationsAndSubmissions ModelingExercise with fetched participations and submissions
     * @param minimumScore                             consider only submissions whose score is greater or equal to this value
     * @return List containing the latest modeling submission for every participation
     */
    public List<ModelingSubmission> modelingSubmissionsForComparison(ModelingExercise exerciseWithParticipationsAndSubmissions, int minimumScore) {

        // Note: minimum size is checked at a different place in this service
        return exerciseWithParticipationsAndSubmissions.getStudentParticipations().parallelStream().filter(plagiarismService.filterForStudents())
                .map(Participation::findLatestSubmission).filter(Optional::isPresent).map(Optional::get).filter(submission -> submission instanceof ModelingSubmission)
                .map(submission -> (ModelingSubmission) submission).filter(submission -> hasMinimumScore(submission, minimumScore)).toList();
    }
}
