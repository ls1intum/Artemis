package de.tum.cit.aet.artemis.modeling.service.compass;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.modeling.domain.ModelCluster;
import de.tum.cit.aet.artemis.modeling.domain.ModelElement;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.repository.ModelClusterRepository;
import de.tum.cit.aet.artemis.modeling.repository.ModelElementRepository;
import de.tum.cit.aet.artemis.modeling.repository.ModelingSubmissionRepository;
import de.tum.cit.aet.artemis.modeling.service.compass.controller.FeedbackSelector;
import de.tum.cit.aet.artemis.modeling.service.compass.controller.ModelClusterFactory;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.UMLElement;

@Profile(PROFILE_CORE)
@Service
public class CompassService {

    private static final Logger log = LoggerFactory.getLogger(CompassService.class);

    private final ModelingSubmissionRepository modelingSubmissionRepository;

    private final ModelElementRepository modelElementRepository;

    private final ModelClusterRepository modelClusterRepository;

    private final FeedbackRepository feedbackRepository;

    public CompassService(ModelingSubmissionRepository modelingSubmissionRepository, ModelElementRepository modelElementRepository, ModelClusterRepository modelClusterRepository,
            FeedbackRepository feedbackRepository) {
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.modelClusterRepository = modelClusterRepository;
        this.modelElementRepository = modelElementRepository;
        this.feedbackRepository = feedbackRepository;
    }

    /**
     * Builds and saves the clusters
     * Does not build clusters if there is already a cluster for given exercise
     *
     * @param modelingExercise the exercise to build clusters for
     */
    public void build(ModelingExercise modelingExercise) {
        long start = System.nanoTime();

        if (!isSupported(modelingExercise)) {
            return;
        }

        List<ModelCluster> currentClusters = modelClusterRepository.findAllByExerciseIdWithEagerElements(modelingExercise.getId());
        if (!currentClusters.isEmpty()) {
            log.info("Clusters have already been built. First delete existing clusters and then rebuild them!");
            // Do not build submissions if this process has already been done before
            return;
        }
        List<ModelingSubmission> submissions = modelingSubmissionRepository.findSubmittedByExerciseIdWithEagerResultsAndFeedback(modelingExercise.getId());
        log.info("ModelCluster: start building clusters of {} submissions for modeling exercise {}", submissions.size(), modelingExercise.getId());

        ModelClusterFactory clusterFactory = new ModelClusterFactory();
        List<ModelCluster> modelClusters = clusterFactory.buildClusters(submissions, modelingExercise);
        log.info("ModelClusterTimeLog: building clusters of {} submissions for modeling exercise {} done in {}", submissions.size(), modelingExercise.getId(),
                TimeLogUtil.formatDurationFrom(start));
        modelClusterRepository.saveAll(modelClusters);
        modelElementRepository.saveAll(modelClusters.stream().flatMap(modelCluster -> modelCluster.getModelElements().stream()).toList());
        log.info("ModelClusterTimeLog: building and saving clusters of {} submissions for exercise {} done in {}", submissions.size(), modelingExercise.getId(),
                TimeLogUtil.formatDurationFrom(start));
    }

    /**
     * Selects the feedback suggestion for each element in submission and creates a result from them
     * Returns null if no feedback can be selected or the submission has already a manual feedback
     *
     * @param modelingSubmission the submission to select feedbacks for
     * @param modelingExercise   the modeling exercise to which the submission belongs
     * @return the semi-automatic result that has the feedbacks inside
     */
    public Result getSuggestionResult(ModelingSubmission modelingSubmission, ModelingExercise modelingExercise) {
        Result result = getAutomaticResultForSubmission(modelingSubmission);
        if (result != null) {
            List<Feedback> feedbacksForSuggestion = new ArrayList<>();
            ModelClusterFactory clusterBuilder = new ModelClusterFactory();
            List<UMLElement> elements = clusterBuilder.getModelElements(modelingSubmission);

            // elements can be null if the modeling submission does not contain a model
            // this can happen for empty submissions in exams
            if (elements == null) {
                return null;
            }

            // Note: this code assumes that the model element id is globally unique
            List<ModelElement> modelElements = modelElementRepository.findByModelElementIdIn(elements.stream().map(UMLElement::getJSONElementID).toList());
            List<Long> clusterIds = modelElements.stream().map(ModelElement::getCluster).map(ModelCluster::getId).toList();
            List<ModelCluster> modelClusters = modelClusterRepository.findAllByIdInWithEagerElements(clusterIds);
            List<String> references = modelClusters.stream().flatMap(modelCluster -> modelCluster.getModelElements().stream())
                    .map(modelElement1 -> modelElement1.getModelElementType() + ":" + modelElement1.getModelElementId()).toList();
            List<Feedback> feedbacks = feedbackRepository.findByReferenceInAndResult_Submission_Participation_Exercise(references, modelingExercise);
            for (ModelElement modelElement : modelElements) {
                if (modelElement != null) {
                    ModelCluster cluster = modelClusters.get(modelClusters.indexOf(modelElement.getCluster()));
                    Set<ModelElement> similarElements = cluster.getModelElements();
                    Set<String> similarReferences = similarElements.stream().map(element -> element.getModelElementType() + ":" + element.getModelElementId())
                            .collect(Collectors.toCollection(HashSet::new));
                    List<Feedback> similarFeedbacks = feedbacks.stream().filter(feedback -> similarReferences.contains(feedback.getReference())).toList();
                    Feedback suggestedFeedback = FeedbackSelector.selectFeedback(modelElement, similarFeedbacks, result);
                    if (suggestedFeedback != null) {
                        feedbacksForSuggestion.add(suggestedFeedback);
                    }
                }
            }
            if (feedbacksForSuggestion.isEmpty()) {
                return null;
            }
            result.getFeedbacks().clear(); // Note, that a result is always initialized with an empty list -> no NPE here
            result.getFeedbacks().addAll(feedbacksForSuggestion);
            result.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        }
        return result;
    }

    /**
     * Get the result of the given modeling submission. If the given submission already contains a manual result, this result is returned. Otherwise, it tries to load and return
     * the result for the submission from the hash map containing all automatic results. If no result could be found in the hash map, a new result is created for the given
     * submission.
     *
     * @param modelingSubmission the submission for which the result should be obtained
     * @return the result of the given submission either obtained from the submission or the semi-automatic result map, or a newly created one if it does not exist already
     */
    private Result getAutomaticResultForSubmission(ModelingSubmission modelingSubmission) {
        Result result = modelingSubmission.getLatestResult();

        if (result == null || AssessmentType.MANUAL != result.getAssessmentType()) {
            if (result == null) {
                StudentParticipation studentParticipation = (StudentParticipation) modelingSubmission.getParticipation();
                result = new Result().submission(modelingSubmission).participation(studentParticipation);
            }
            return result;
        }

        return null;
    }

    /**
     * Indicates if the given diagram type is supported by Compass. At the moment Compass only support class diagrams.
     *
     * @param modelingExercise the modelingExercise that should be checked if automatic assessment is supported
     * @return true if the given diagram type is supported by Compass, false otherwise
     */
    public boolean isSupported(ModelingExercise modelingExercise) {
        // In case the instructor specifies in the UI whether the semi-automatic assessment is possible or not.
        if (modelingExercise.getAssessmentType() != null) {
            return (modelingExercise.getAssessmentType() == AssessmentType.SEMI_AUTOMATIC);
        }
        // if the assessment mode is not specified (e.g. for legacy exercises), team exercises are not supported
        return !modelingExercise.isTeamMode();
    }
}
