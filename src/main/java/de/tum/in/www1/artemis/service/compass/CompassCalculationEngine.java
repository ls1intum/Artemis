package de.tum.in.www1.artemis.service.compass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.service.compass.assessment.Assessment;
import de.tum.in.www1.artemis.service.compass.assessment.CompassResult;
import de.tum.in.www1.artemis.service.compass.assessment.ModelElementAssessment;
import de.tum.in.www1.artemis.service.compass.assessment.Score;
import de.tum.in.www1.artemis.service.compass.conflict.Conflict;
import de.tum.in.www1.artemis.service.compass.controller.*;
import de.tum.in.www1.artemis.service.compass.grade.CompassGrade;
import de.tum.in.www1.artemis.service.compass.grade.Grade;
import de.tum.in.www1.artemis.service.compass.umlmodel.*;
import de.tum.in.www1.artemis.service.compass.utils.JSONMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


public class CompassCalculationEngine implements CalculationEngine {

    private final Logger log = LoggerFactory.getLogger(CompassCalculationEngine.class);

    private ModelIndex modelIndex;
    private AssessmentIndex assessmentIndex;

    private AutomaticAssessmentController automaticAssessmentController;
    private ModelSelector modelSelector;
    private LocalDateTime lastUsed;


    CompassCalculationEngine(Map<Long, JsonObject> models, Map<Long, JsonObject> assessments) {
        lastUsed = LocalDateTime.now();
        modelIndex = new ModelIndex();
        assessmentIndex = new AssessmentIndex();

        automaticAssessmentController = new AutomaticAssessmentController();
        modelSelector = new ModelSelector(); //TODO MJ fix Bug where on load of exercise no modelsWaitingForAssessment are added ? No differentiation between submitted and saved assessments!

        for (Map.Entry<Long, JsonObject> entry : models.entrySet()) {
            buildModel(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Long, JsonObject> entry : assessments.entrySet()) {
            buildAssessment(entry.getKey(), entry.getValue());
            modelSelector.addAlreadyAssessedModel(entry.getKey());
        }

        assessModelsAutomatically();
    }

    /**
     * @param submissionId       ID of submission the modelingAssessment belongs to
     * @param modelingAssessment assessment to check for conflicts
     * @return a list of conflicts modelingAssessment causes with the current manual assessment data
     */
    public List<Conflict> getConflicts(long submissionId, List<Feedback> modelingAssessment) {
        List<Conflict> conflicts = new ArrayList<>();
//        TODO adapt to new assessment model
//        UMLModel model = modelIndex.getModel(submissionId);
//        modelingAssessment.forEach(currentElementAssessment -> {
//            UMLElement currentElement = model.getElementByJSONID(currentElementAssessment.getId()); //TODO MJ return Optional ad throw Exception if no UMLElement found?
//            assessmentIndex.getAssessment(currentElement.getElementID()).ifPresent(assessment -> {
//                List<Score> scores = assessment.getScores(currentElement.getContext());
//                List<Score> scoresInConflict = scores.stream()
//                    .filter(score -> !scoresAreConsideredEqual(score.getPoints(), currentElementAssessment.getCredits()))
//                    .collect(Collectors.toList());
//                if (!scoresInConflict.isEmpty()) {
//                    conflicts.add(new Conflict(currentElement, currentElementAssessment, scoresInConflict));
//                }
//            });
//        });
        return conflicts;
    }


    private void buildModel(long id, JsonObject jsonModel) {
        try {
            UMLModel model = JSONParser.buildModelFromJSON(jsonModel, id);
            SimilarityDetector.analyzeSimilarity(model, modelIndex);
            modelIndex.addModel(model);
        } catch (IOException e) {
            log.error("Could not load file !", e);
        }
    }


    private void buildAssessment(long id, JsonObject jsonAssessment) {
        UMLModel model = modelIndex.getModelMap().get(id);
        if (model == null) {
            return;
        }
        Map<String, Score> scoreList = JSONParser.getScoresFromJSON(jsonAssessment, model);
        this.addNewManualAssessment(scoreList, model);
        modelSelector.removeModelWaitingForAssessment(model.getModelID());
    }


    protected Collection<UMLModel> getUmlModelCollection() {
        return modelIndex.getModelCollection();
    }


    protected Map<Long, UMLModel> getModelMap() {
        return modelIndex.getModelMap();
    }


    @SuppressWarnings("unused")
    private double getTotalCoverage() {
        return automaticAssessmentController.getTotalCoverage();
    }


    @SuppressWarnings("unused")
    private double getTotalConfidence() {
        return automaticAssessmentController.getTotalConfidence();
    }


    private void assessModelsAutomatically() {
        automaticAssessmentController.assessModelsAutomatically(modelIndex, assessmentIndex);
    }


    private void addNewManualAssessment(Map<String, Score> scoreHashMap, UMLModel model) {
        try {
            automaticAssessmentController.addScoresToAssessment(assessmentIndex, scoreHashMap, model);
        } catch (IOException e) {
            log.error("manual assessment for " + model.getName() + " could not be added: " + e.getMessage());
        }
    }


    /**
     * @return id of the next optimal model or null if all models are completely assessed
     */
    @Override
    public Map.Entry<Long, Grade> getNextOptimalModel() {
        lastUsed = LocalDateTime.now();
        Long optimalModelId = modelSelector.selectNextModel(modelIndex);
        if (optimalModelId == null) {
            return null;
        }
        Grade grade = getResultForModel(optimalModelId);
        // Should never happen
        if (grade == null) {
            grade = new CompassGrade();
        }
        return new AbstractMap.SimpleEntry<>(optimalModelId, grade);
    }


    @Override
    public Grade getResultForModel(long modelId) {
        lastUsed = LocalDateTime.now();
        if (!modelIndex.getModelMap().containsKey(modelId)) {
            return null;
        }

        UMLModel model = modelIndex.getModelMap().get(modelId);
        CompassResult compassResult = model.getLastAssessmentCompassResult();

        if (compassResult == null) {
            return automaticAssessmentController.assessModelAutomatically(model, assessmentIndex);
        }
        return compassResult;
    }


    @Override
    public Collection<Long> getModelIds() {
        return modelIndex.getModelMap().keySet();
    }


    @Override
    public void notifyNewAssessment(List<ModelElementAssessment> modelingAssessment, long submissionId) {
        lastUsed = LocalDateTime.now();
        modelSelector.addAlreadyAssessedModel(submissionId);
        UMLModel model = modelIndex.getModel(submissionId);
        addNewManualAssessment(modelingAssessment, model);
        modelSelector.removeModelWaitingForAssessment(model.getModelID());
        assessModelsAutomatically();
    }


    @Override
    public void notifyNewModel(String model, long modelId) {
        lastUsed = LocalDateTime.now();
        // Do not add models that might already exist
        if (modelIndex.getModelMap().containsKey(modelId)) {
            return;
        }
        buildModel(modelId, new JsonParser().parse(model).getAsJsonObject());
    }


    @Override
    public LocalDateTime getLastUsedAt() {
        return lastUsed;
    }


    @Override
    public Map<Long, Grade> getModelsWaitingForAssessment() {
        Map<Long, Grade> optimalModels = new HashMap<>();
        for (long modelId : modelSelector.getModelsWaitingForAssessment()) {
            optimalModels.put(modelId, getResultForModel(modelId));
        }
        return optimalModels;
    }


    @Override
    public void removeModelWaitingForAssessment(long modelId, boolean isAssessed) {
        modelSelector.removeModelWaitingForAssessment(modelId);
        if (!isAssessed && (modelIndex.getModelMap().get(modelId) == null ||
            !modelIndex.getModelMap().get(modelId).isEntirelyAssessed())) {
            modelSelector.removeAlreadyAssessedModel(modelId);
        } else if (isAssessed) {
            modelSelector.addAlreadyAssessedModel(modelId);
        }
    }


    @Override
    public List<Feedback> convertToFeedback(Grade grade, long modelId) {
        UMLModel model = this.modelIndex.getModelMap().get(modelId);
        if (model == null) {
            return null;
        }
        return JSONParser.convertToFeedback(grade, model);
    }


    /**
     * format:
     * uniqueElements
     * [{id}
     * name
     * apollonId
     * conflicts]
     * numberModels
     * numberConflicts
     * totalConfidence
     * totalCoverage
     * models
     * [{id}
     * confidence
     * coverage
     * conflicts]
     *
     * @return statistics about the UML model
     */
    //TODO: I don't think we should expose JSONObject to this internal server class. It would be better to return Java objects here
    @Override
    public JsonObject getStatistics() {
        JsonObject jsonObject = new JsonObject();

        JsonObject uniqueElements = new JsonObject();
        int conflicts = 0;
        for (UMLElement umlElement : this.modelIndex.getUniqueElements()) {
            JsonObject uniqueElement = new JsonObject();
            uniqueElement.addProperty("name", umlElement.getName());
            uniqueElement.addProperty("apollonId", umlElement.getJSONElementID());
            boolean conflict = this.hasConflict(umlElement.getElementID());
            if (conflict) {
                conflicts++;
            }
            uniqueElement.addProperty("conflicts", conflict);
            uniqueElements.add(umlElement.getElementID() + "", uniqueElement);
        }
        jsonObject.add("uniqueElements", uniqueElements);

        jsonObject.addProperty("numberModels", this.modelIndex.getModelCollection().size());
        jsonObject.addProperty("numberConflicts", conflicts);
        jsonObject.addProperty("totalConfidence", this.getTotalConfidence());
        jsonObject.addProperty("totalCoverage", this.getTotalCoverage());

        JsonObject models = new JsonObject();
        for (Map.Entry<Long, UMLModel> modelEntry : this.getModelMap().entrySet()) {
            JsonObject model = new JsonObject();
            model.addProperty("coverage", modelEntry.getValue().getLastAssessmentCoverage());
            model.addProperty("confidence", modelEntry.getValue().getLastAssessmentConfidence());
            int modelConflicts = 0;
            List<UMLElement> elements = new ArrayList<>();
            elements.addAll(modelEntry.getValue().getClassList());
            elements.addAll(modelEntry.getValue().getAssociationList());
            for (UMLClass umlClass : modelEntry.getValue().getClassList()) {
                elements.addAll(umlClass.getAttributes());
                elements.addAll(umlClass.getMethods());
            }
            for (UMLElement element : elements) {
                boolean modelConflict = this.hasConflict(element.getElementID());
                if (modelConflict) {
                    modelConflicts++;
                }
            }
            model.addProperty("conflicts", modelConflicts);
            model.addProperty("elements", elements.size());
            model.addProperty("classes", elements.stream().filter(umlElement -> umlElement instanceof UMLClass).count());
            model.addProperty("attributes", elements.stream().filter(umlElement -> umlElement instanceof UMLAttribute).count());
            model.addProperty("methods", elements.stream().filter(umlElement -> umlElement instanceof UMLMethod).count());
            model.addProperty("associations", elements.stream().filter(umlElement -> umlElement instanceof UMLAssociation).count());
            models.add(modelEntry.getKey().toString(), model);
        }
        jsonObject.add("models", models);

        return jsonObject;
    }

    private void addNewManualAssessment(List<ModelElementAssessment> modelingAssessment, UMLModel model) {
        Map<String, Score> scoreList = createScoreList(modelingAssessment,model);
        try {
            automaticAssessmentController.addScoresToAssessment(assessmentIndex, scoreList, model);
        } catch (IOException e) {
            log.error("manual assessment for " + model.getName() + " could not be added: " + e.getMessage());
        }
    }

    /**
     * checks and logs if each ModelElementAssessment corresponds to a element in the UMLModel. And returns a mapping from each jsonElementID in the Assessment to its Score
     *
     * @param modelingAssessment the modeling assessment to create the score list of
     * @param model              UmlModel the modelingAssessment belongs to
     * @return mapping of the jsonElementID of each ModelElement contained in the modellingAssessment to its corresponding score
     */
    private Map<String, Score> createScoreList(List<ModelElementAssessment> modelingAssessment, UMLModel model) {
        Map<String, Score> scoreHashMap = new HashMap<>();
        for (ModelElementAssessment assessment : modelingAssessment) {
            String jsonElementID = assessment.getId();
            boolean found = false;

            switch (assessment.getType()) {
                case JSONMapping.assessmentElementTypeClass:
                    for (UMLClass umlClass : model.getClassList()) {
                        if (umlClass.getJSONElementID().equals(jsonElementID)) {
                            found = true;
                            break;
                        }
                    }
                    break;
                case JSONMapping.assessmentElementTypeAttribute:
                    for (UMLClass umlClass : model.getClassList()) {
                        for (UMLAttribute umlAttribute : umlClass.getAttributes()) {
                            if (umlAttribute.getJSONElementID().equals(jsonElementID)) {
                                found = true;
                                break;
                            }
                        }
                    }
                    break;
                case JSONMapping.assessmentElementTypeMethod:
                    for (UMLClass umlClass : model.getClassList()) {
                        for (UMLMethod umlMethod : umlClass.getMethods()) {
                            if (umlMethod.getJSONElementID().equals(jsonElementID)) {
                                found = true;
                                break;
                            }
                        }
                    }
                    break;
                case JSONMapping.assessmentElementTypeRelationship:
                    for (UMLAssociation umlAssociation : model.getAssociationList()) {
                        if (umlAssociation.getJSONElementID().equals(jsonElementID)) {
                            found = true;
                            break;
                        }
                    }
                    break;
            }

            if (!found) {
                /*
                 * This might happen if e.g. the user input was malformed and the compass model parser had to ignore the element
                 */
                log.warn("Element " + jsonElementID + " of type " + assessment.getType() + " not in model");
                continue;
            }
            List<String> comment = new ArrayList<>();
            if (assessment.getCommment() != null && !assessment.getCommment().equals("")) {
                comment.add(assessment.getCommment());
            }

            // Ignore misformatted score
            try {
                Score score = new Score(assessment.getCredits(), comment, 1.0);
                scoreHashMap.put(jsonElementID, score);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
        return scoreHashMap;
    }


    private boolean hasConflict(int elementId) {
        Optional<Assessment> assessment = this.assessmentIndex.getAssessment(elementId);
        if (assessment.isPresent()) {
            for (List<Score> scores : assessment.get().getContextScoreList().values()) {
                double uniqueScore = -1;
                for (Score score : scores) {
                    if (uniqueScore != -1 && uniqueScore != score.getPoints()) {
                        return true;
                    }
                    uniqueScore = score.getPoints();
                }
            }
        }
        return false;
    }


    private boolean scoresAreConsideredEqual(double score1, double score2) {
        return Math.abs(score1 - score2) < Constants.COMPASS_SCORE_EQUALITY_THRESHOLD;
    }


    // Used for internal analysis of metrics
    void printStatistic() {
        // Variability of solutions
        log.debug("Number of unique elements (without context) == similarity sets: " +
            this.modelIndex.getNumberOfUniqueElements() + "\n");

        int totalModelElements = 0;
        for (UMLModel umlModel : this.getUmlModelCollection()) {
            totalModelElements += umlModel.getClassList().size() + umlModel.getAssociationList().size();
            for (UMLClass umlClass : umlModel.getClassList()) {
                totalModelElements += umlClass.getMethods().size() + umlClass.getAttributes().size();
            }
        }

        log.debug("Number of total model elements: " + totalModelElements + "\n");
        double optimalEqu = (totalModelElements * 1.0) / this.getModelMap().size();
        log.debug("Number of optimal similarity sets: " + optimalEqu + "\n");
        log.debug("Variance: " +
            (this.modelIndex.getNumberOfUniqueElements() - optimalEqu) / (totalModelElements - optimalEqu) + "\n");

        // Total coverage and confidence
        this.automaticAssessmentController.assessModelsAutomatically(modelIndex, assessmentIndex);
        log.debug("Total confidence: " + this.automaticAssessmentController.getTotalConfidence() + "\n");
        log.debug("Total coverage: " + this.automaticAssessmentController.getTotalCoverage() + "\n");

        // Conflicts
        int conflicts = 0;
        double uniqueElementsContext = 0;
        for (Assessment assessment : this.assessmentIndex.getAssessmentsMap().values()) {
            for (List<Score> scores : assessment.getContextScoreList().values()) {
                uniqueElementsContext++;
                double uniqueScore = -1;
                for (Score score : scores) {
                    if (uniqueScore != -1 && uniqueScore != score.getPoints()) {
                        conflicts++;
                        break;
                    }
                    uniqueScore = score.getPoints();
                }
            }
        }
        log.debug("Total conflicts (with context): " + conflicts + "\n");
        log.debug("Relative conflicts (with context): " + (conflicts / uniqueElementsContext) + "\n");
    }
}
