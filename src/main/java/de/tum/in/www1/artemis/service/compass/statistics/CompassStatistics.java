package de.tum.in.www1.artemis.service.compass.statistics;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;

public class CompassStatistics {

    private final Logger log = LoggerFactory.getLogger(CompassStatistics.class);

    /**
     * format: uniqueElements [{id} name apollonId conflicts] numberModels numberConflicts totalConfidence totalCoverage models [{id} confidence coverage conflicts]
     *
     * @return statistics about the UML model
     */
    // TODO: I don't think we should expose JSONObject to this internal server class. It would be better to return Java objects here
    // TODO: New pipeline does not calculate these values beforehand calculate them here and return
    public JsonObject getStatistics() {
        JsonObject jsonObject = new JsonObject();

        JsonObject uniqueElements = new JsonObject();
        // int numberOfConflicts = 0;
        // for (UMLElement umlElement : modelIndex.getUniqueElements()) {
        // JsonObject uniqueElement = new JsonObject();
        // uniqueElement.addProperty("name", umlElement.toString());
        // uniqueElement.addProperty("apollonId", umlElement.getJSONElementID());
        //// boolean hasConflict = hasConflict(umlElement.getSimilarityID());
        //// if (hasConflict) {
        //// numberOfConflicts++;
        //// }
        //// uniqueElement.addProperty("conflicts", hasConflict);
        //// uniqueElements.add(String.valueOf(umlElement.getSimilarityID()), uniqueElement);
        // }
        // jsonObject.add("uniqueElements", uniqueElements);
        //
        // jsonObject.addProperty("numberModels", modelIndex.getModelCollection().size());
        // jsonObject.addProperty("numberConflicts", numberOfConflicts);
        // jsonObject.addProperty("totalConfidence", getTotalConfidence());
        // jsonObject.addProperty("totalCoverage", getTotalCoverage());
        //
        // JsonObject models = new JsonObject();
        // for (Map.Entry<Long, UMLDiagram> modelEntry : getModelMap().entrySet()) {
        // JsonObject model = new JsonObject();
        // model.addProperty("coverage", automaticAssessmentController.getLastAssessmentCoverage(modelEntry.getValue().getModelSubmissionId()));
        // model.addProperty("confidence", automaticAssessmentController.getLastAssessmentConfidence(modelEntry.getValue().getModelSubmissionId()));
        // int numberOfModelConflicts = 0;
        // List<UMLElement> elements = new ArrayList<>(modelEntry.getValue().getAllModelElements());
        // for (UMLElement element : elements) {
        //// boolean modelHasConflict = hasConflict(element.getSimilarityID());
        //// if (modelHasConflict) {
        //// numberOfModelConflicts++;
        //// }
        // }
        // model.addProperty("conflicts", numberOfModelConflicts);
        // model.addProperty("elements", elements.size());
        // model.addProperty("classes", elements.stream().filter(umlElement -> umlElement instanceof UMLClass).count());
        // model.addProperty("attributes", elements.stream().filter(umlElement -> umlElement instanceof UMLAttribute).count());
        // model.addProperty("methods", elements.stream().filter(umlElement -> umlElement instanceof UMLMethod).count());
        // model.addProperty("associations", elements.stream().filter(umlElement -> umlElement instanceof UMLRelationship).count());
        // models.add(modelEntry.getKey().toString(), model);
        // }
        // jsonObject.add("models", models);

        return jsonObject;
    }

    /**
     * Used for internal analysis of modeling data. Do not remove, usage is commented out due to performance reasons.
     *
     * @param exerciseId the id of the modeling exercise that is analyzed
     * @param finishedResults the list of finished results, i.e. results for which assessor and completion date is not null
     */
    // TODO: New compass pipeline does not calculate most of these values beforehand change the way the values are acquired here
    public void printStatistic(long exerciseId, List<Result> finishedResults) {
        log.info("Statistics for exercise {}\n\n\n", exerciseId);

        long totalNumberOfFeedback = 0;
        long totalNumberOfAutomaticFeedback = 0;
        long totalNumberOfAdaptedFeedback = 0;
        long totalNumberOfManualFeedback = 0;

        long numberOfAssessedClasses = 0;
        long numberOfAssessedAttrbutes = 0;
        long numberOfAssessedMethods = 0;
        long numberOfAssessedRelationships = 0;
        long numberOfAssessedPackages = 0;

        long totalLengthOfFeedback = 0;
        long totalLengthOfPositiveFeedback = 0;
        long totalNumberOfPositiveFeedbackItems = 0;
        long totalLengthOfNegativeFeedback = 0;
        long totalNumberOfNegativeFeedbackItems = 0;
        long totalLengthOfNeutralFeedback = 0;
        long totalNumberOfNeutralFeedbackItems = 0;

        for (Result result : finishedResults) {
            List<Feedback> referenceFeedback = result.getFeedbacks().stream().filter(feedback -> feedback.getReference() != null).collect(Collectors.toList());

            totalNumberOfFeedback += referenceFeedback.size();
            totalNumberOfAutomaticFeedback += referenceFeedback.stream().filter(feedback -> feedback.getType() == FeedbackType.AUTOMATIC).count();
            totalNumberOfAdaptedFeedback += referenceFeedback.stream().filter(feedback -> feedback.getType() == FeedbackType.AUTOMATIC_ADAPTED).count();
            totalNumberOfManualFeedback += referenceFeedback.stream().filter(feedback -> feedback.getType() == FeedbackType.MANUAL).count();

            numberOfAssessedClasses += referenceFeedback.stream()
                    .filter(feedback -> feedback.getReferenceElementType().equals("Class") || feedback.getReferenceElementType().equals("AbstractClass")
                            || feedback.getReferenceElementType().equals("Interface") || feedback.getReferenceElementType().equals("Enumeration"))
                    .count();
            numberOfAssessedAttrbutes += referenceFeedback.stream().filter(feedback -> feedback.getReferenceElementType().equals("ClassAttribute")).count();
            numberOfAssessedMethods += referenceFeedback.stream().filter(feedback -> feedback.getReferenceElementType().equals("ClassMethod")).count();
            numberOfAssessedRelationships += referenceFeedback.stream()
                    .filter(feedback -> feedback.getReferenceElementType().equals("ClassBidirectional") || feedback.getReferenceElementType().equals("ClassUnidirectional")
                            || feedback.getReferenceElementType().equals("ClassAggregation") || feedback.getReferenceElementType().equals("ClassInheritance")
                            || feedback.getReferenceElementType().equals("ClassDependency") || feedback.getReferenceElementType().equals("ClassComposition")
                            || feedback.getReferenceElementType().equals("ClassRealization"))
                    .count();
            numberOfAssessedPackages += referenceFeedback.stream().filter(feedback -> feedback.getReferenceElementType().equals("Package")).count();

            for (Feedback feedback : referenceFeedback) {
                int feedbackLength = 0;

                if (feedback.getText() != null) {
                    feedbackLength = feedback.getText().length();
                }

                totalLengthOfFeedback += feedbackLength;

                if (feedback.getCredits() > 0) {
                    totalLengthOfPositiveFeedback += feedbackLength;
                    totalNumberOfPositiveFeedbackItems++;
                }
                else if (feedback.getCredits() == 0) {
                    totalLengthOfNeutralFeedback += feedbackLength;
                    totalNumberOfNeutralFeedbackItems++;
                }
                else if (feedback.getCredits() < 0) {
                    totalLengthOfNegativeFeedback += feedbackLength;
                    totalNumberOfNegativeFeedbackItems++;
                }
            }
        }

        //// long numberOfModels = modelIndex.getModelCollection().size();
        //// Map<UMLElement, Integer> modelElementMapping = modelIndex.getElementSimilarityMap();
        //// long numberOfModelElements = modelElementMapping.size();
        //// long numberOfClasses = 0;
        //// long numberOfAttrbutes = 0;
        //// long numberOfMethods = 0;
        //// long numberOfRelationships = 0;
        //// long numberOfPackages = 0;
        //
        // for (UMLElement element : modelElementMapping.keySet()) {
        // if (element instanceof UMLClass) {
        // numberOfClasses++;
        // }
        // else if (element instanceof UMLAttribute) {
        // numberOfAttrbutes++;
        // }
        // else if (element instanceof UMLMethod) {
        // numberOfMethods++;
        // }
        // else if (element instanceof UMLRelationship) {
        // numberOfRelationships++;
        // }
        // else if (element instanceof UMLPackage) {
        // numberOfPackages++;
        // }
        // }
        //
        // // General information
        // log.info("################################################## General information ##################################################\n");
        //
        // log.info("Number of models: {}\n", numberOfModels);
        // log.info("Number of model elements: {}\n", numberOfModelElements);
        // log.info("Number of classes: {}\n", numberOfClasses);
        // log.info("Number of attributes: {}\n", numberOfAttrbutes);
        // log.info("Number of methods: {}\n", numberOfMethods);
        // log.info("Number of relationships: {}\n", numberOfRelationships);
        // log.info("Number of packages: {}\n", numberOfPackages);
        // double elementsPerModel = numberOfModelElements * 1.0 / numberOfModels;
        // log.info("Average number of elements per model: {}\n", elementsPerModel);
        //
        // log.info("Number of assessed models: {}\n", finishedResults.size());
        // log.info("Number of assessed model elements: {}\n", totalNumberOfFeedback);
        // log.info("Number of assessed classes: {} ({}%)\n", numberOfAssessedClasses, Math.round(numberOfAssessedClasses * 10000.0 / numberOfClasses) / 100.0);
        // log.info("Number of assessed attributes: {} ({}%)\n", numberOfAssessedAttrbutes, Math.round(numberOfAssessedAttrbutes * 10000.0 / numberOfAttrbutes) / 100.0);
        // log.info("Number of assessed methods: {} ({}%)\n", numberOfAssessedMethods, Math.round(numberOfAssessedMethods * 10000.0 / numberOfMethods) / 100.0);
        // log.info("Number of assessed relationships: {} ({}%)\n", numberOfAssessedRelationships,
        // Math.round(numberOfAssessedRelationships * 10000.0 / numberOfRelationships) / 100.0);
        // log.info("Number of assessed packages: {} ({}%)\n", numberOfAssessedPackages, Math.round(numberOfAssessedPackages * 10000.0 / numberOfPackages) / 100.0);
        // double feedbackPerAssessment = totalNumberOfFeedback * 1.0 / finishedResults.size();
        // log.info("Average number of feedback elements per assessment: {}\n\n\n", feedbackPerAssessment);

        // Feedback type
        log.info("################################################## Feedback type ##################################################\n");

        log.info("Automatic feedback: {} ({}%)\n", totalNumberOfAutomaticFeedback, Math.round(totalNumberOfAutomaticFeedback * 10000.0 / totalNumberOfFeedback) / 100.0);
        log.info("Adapted feedback: {} ({}%)\n", totalNumberOfAdaptedFeedback, Math.round(totalNumberOfAdaptedFeedback * 10000.0 / totalNumberOfFeedback) / 100.0);
        log.info("Manual feedback: {} ({}%)\n", totalNumberOfManualFeedback, Math.round(totalNumberOfManualFeedback * 10000.0 / totalNumberOfFeedback) / 100.0);
        log.info("Amount of automatic feedback that was adapted: {}%\n\n\n",
                Math.round(totalNumberOfAdaptedFeedback * 10000.0 / (totalNumberOfAutomaticFeedback + totalNumberOfAdaptedFeedback)) / 100.0);

        // Feedback length
        log.info("################################################## Feedback length ##################################################\n");

        log.info("Total amount of feedback: {}\n", totalNumberOfFeedback);
        log.info("Average length of feedback: {}\n", totalLengthOfFeedback * 1.0 / totalNumberOfFeedback);
        log.info("Total amount of positive feedback: {}\n", totalNumberOfPositiveFeedbackItems);
        log.info("Average length of positive feedback: {}\n", totalLengthOfPositiveFeedback * 1.0 / totalNumberOfPositiveFeedbackItems);
        log.info("Total amount of neutral feedback: {}\n", totalNumberOfNeutralFeedbackItems);
        log.info("Average length of neutral feedback: {}\n", totalLengthOfNeutralFeedback * 1.0 / totalNumberOfNeutralFeedbackItems);
        log.info("Total amount of negative feedback: {}\n", totalNumberOfNegativeFeedbackItems);
        log.info("Average length of negative feedback: {}\n\n\n", totalLengthOfNegativeFeedback * 1.0 / totalNumberOfNegativeFeedbackItems);

        // Similarity sets
        log.info("################################################## Similarity sets ##################################################\n");
        //
        // // Note, that these two value refer to all similarity sets that have an assessment, i.e. it is not the total number as it excludes the sets without assessments. This
        // might
        // // distort the analysis values below.
        // long numberOfSimilaritySets = automaticAssessmentController.getAssessmentMap().size();
        // long numberOfElementsInSimilaritySets = 0;
        //
        // long numberOfSimilaritySetsPositiveScore = 0;
        // long numberOfSimilaritySetsPositiveScoreRegardingConfidence = 0;
        //
        // for (SimilaritySetAssessment similaritySetAssessment : automaticAssessmentController.getAssessmentMap().values()) {
        // numberOfElementsInSimilaritySets += similaritySetAssessment.getFeedbackList().size();
        //
        // Score score = similaritySetAssessment.getScore();
        // if (score.getPoints() > 0) {
        // numberOfSimilaritySetsPositiveScore += 1;
        // if (score.getConfidence() >= 0.8) {
        // numberOfSimilaritySetsPositiveScoreRegardingConfidence += 1;
        // }
        // }
        // }

        // log.info("Number of unique elements (without context) of submitted models: {}\n", modelIndex.getNumberOfUniqueElements());
        // log.info("Number of similarity sets (including context) of assessed models: {}\n", numberOfSimilaritySets);
        // log.info("Average number of elements per similarity set: {}\n", numberOfElementsInSimilaritySets * 1.0 / numberOfSimilaritySets);
        // // The optimal correction effort describes the maximum amount of model elements that tutors would have to assess in an optimal scenario
        // log.info("Optimal correction effort (# similarity sets / # model elements): {}\n", numberOfSimilaritySets * 1.0 / numberOfElementsInSimilaritySets);
        //
        // log.info("Number of similarity sets with positive score: {}\n", numberOfSimilaritySetsPositiveScore);
        // log.info("Number of similarity sets with positive score and confidence at least 80%: {}\n\n\n", numberOfSimilaritySetsPositiveScoreRegardingConfidence);
        //
        // // Variability index
        // log.info("################################################## Variability index ##################################################\n");
        //
        // log.info("Variability index #1 (positive score): {}\n", numberOfSimilaritySetsPositiveScore / elementsPerModel);
        // log.info("Variability index #2 (positive score and confidence >= 80%): {}\n", numberOfSimilaritySetsPositiveScoreRegardingConfidence / elementsPerModel);
        // log.info("Variability index #3 (based on \"all\" similarity sets): {}\n", numberOfSimilaritySets / elementsPerModel);
        //
        // log.info("Normalized variability index #1 (positive score): {}\n", (numberOfSimilaritySetsPositiveScore - elementsPerModel) / (numberOfModelElements -
        // elementsPerModel));
        // log.info("Normalized variability index #2 (positive score and confidence >= 80%): {}\n",
        // (numberOfSimilaritySetsPositiveScoreRegardingConfidence - elementsPerModel) / (numberOfModelElements - elementsPerModel));
        // log.info("Normalized variability index #3 (based on \"all\" similarity sets): {}\n",
        // (numberOfSimilaritySets - elementsPerModel) / (numberOfModelElements - elementsPerModel));
        //
        // // Alternative calculation of the variability index considering the average feedback items per assessment instead of the average elements per model
        // log.info("Alternative variability index #1 (positive score): {}\n", numberOfSimilaritySetsPositiveScore / feedbackPerAssessment);
        // log.info("Alternative variability index #2 (positive score and confidence >= 80%): {}\n", numberOfSimilaritySetsPositiveScoreRegardingConfidence /
        // feedbackPerAssessment);
        // log.info("Alternative variability index #3 (based on \"all\" similarity sets): {}\n", numberOfSimilaritySets / feedbackPerAssessment);
        //
        // log.info("Normalized alternative variability index #1 (positive score): {}\n",
        // (numberOfSimilaritySetsPositiveScore - feedbackPerAssessment) / (totalNumberOfFeedback - feedbackPerAssessment));
        // log.info("Normalized alternative variability index #2 (positive score and confidence >= 80%): {}\n",
        // (numberOfSimilaritySetsPositiveScoreRegardingConfidence - feedbackPerAssessment) / (totalNumberOfFeedback - feedbackPerAssessment));
        // log.info("Normalized alternative variability index #3 (based on \"all\" similarity sets): {}\n",
        // (numberOfSimilaritySets - feedbackPerAssessment) / (totalNumberOfFeedback - feedbackPerAssessment));
    }
}
