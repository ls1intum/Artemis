package de.tum.in.www1.artemis.service;

import static com.google.gson.JsonParser.parseString;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.math3.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.TransformationModelingExercise;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.Visibility;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.parsers.UMLModelParser;
import de.tum.in.www1.artemis.service.compass.umlmodel.reachabilitygraph.ReachabilityGraphArc;
import de.tum.in.www1.artemis.service.compass.umlmodel.reachabilitygraph.ReachabilityGraphMarking;
import de.tum.in.www1.artemis.web.rest.util.StringUtil;

@Service
public class ReachabilityGraphCorrectionService {

    private final Logger log = LoggerFactory.getLogger(ReachabilityGraphCorrectionService.class);

    private final ResultService resultService;

    public ReachabilityGraphCorrectionService(ResultService resultService) {
        this.resultService = resultService;
    }

    private Set<List<Integer>> solutionMarkings(List<UMLElement> elements) {
        return elements.stream().filter(m -> m instanceof ReachabilityGraphMarking).map(UMLElement::getName).map(StringUtil::markingStringToList).filter(Optional::isPresent)
                .map(Optional::get).collect(Collectors.toSet());
    }

    private List<Integer> solutionInitialMarking(List<UMLElement> elements) {
        return elements.stream().filter(m -> m instanceof ReachabilityGraphMarking).map(m -> (ReachabilityGraphMarking) m).filter(ReachabilityGraphMarking::isInitialMarking)
                .map(UMLElement::getName).map(StringUtil::markingStringToList).filter(Optional::isPresent).map(Optional::get).findAny().orElse(null);
    }

    private Map<Pair<List<Integer>, List<Integer>>, Set<String>> solutionArcs(List<UMLElement> elements) {
        Map<Pair<List<Integer>, List<Integer>>, Set<String>> solutionArcs = new HashMap<>();

        elements.stream().filter(element -> element instanceof ReachabilityGraphArc).map(element -> (ReachabilityGraphArc) element).forEach(arc -> {
            Optional<List<Integer>> source = StringUtil.markingStringToList(arc.getSource().getName());
            Optional<List<Integer>> target = StringUtil.markingStringToList(arc.getTarget().getName());

            if (source.isPresent() && target.isPresent()) {
                Pair<List<Integer>, List<Integer>> pair = new Pair<>(source.get(), target.get());
                solutionArcs.putIfAbsent(pair, new HashSet<>());
                solutionArcs.get(pair).add(arc.getTransition());
            }
        });

        return solutionArcs;
    }

    public ModelingSubmission autoGrade(TransformationModelingExercise transformationModelingExercise, ModelingSubmission modelingSubmission) {
        try {
            List<UMLElement> solution = UMLModelParser.buildModelFromJSON(parseString(transformationModelingExercise.getSampleSolutionModel()).getAsJsonObject(), 0)
                    .getAllModelElements();

            List<UMLElement> submission = UMLModelParser.buildModelFromJSON(parseString(modelingSubmission.getModel()).getAsJsonObject(), modelingSubmission.getId())
                    .getAllModelElements();

            Set<List<Integer>> solutionMarkings = solutionMarkings(solution);
            List<Integer> solutionInitialMarking = solutionInitialMarking(solution);
            Map<Pair<List<Integer>, List<Integer>>, Set<String>> solutionArcs = solutionArcs(solution);

            double pointsForCorrectMarking = transformationModelingExercise.getCorrectionSchemePointsFor("correctMarking");
            double pointsForCorrectArc = transformationModelingExercise.getCorrectionSchemePointsFor("correctArc");
            double pointsForCorrectInitialMarking = transformationModelingExercise.getCorrectionSchemePointsFor("correctInitialMarking");
            double pointsForAdditionalMarking = transformationModelingExercise.getCorrectionSchemePointsFor("additionalMarking");
            double pointsForAdditionalArc = transformationModelingExercise.getCorrectionSchemePointsFor("additionalArc");
            double pointsForAdditionalInitialMarking = transformationModelingExercise.getCorrectionSchemePointsFor("additionalInitialMarking");

            List<Feedback> feedbacks = new ArrayList<>();

            for (UMLElement element : submission) {
                if (element instanceof ReachabilityGraphMarking m) {
                    String name = StringUtil.stripSurroundingBrackets(StringUtil.stripWhitespaces(element.getName()));

                    List<Integer> marking = Arrays.stream(name.split(",")).filter(NumberUtils::isParsable).mapToInt(Integer::parseInt).boxed().collect(Collectors.toList());

                    Feedback feedback = new Feedback().reference(element.getType() + ":" + element.getJSONElementID()).type(FeedbackType.AUTOMATIC).visibility(Visibility.ALWAYS);

                    double credits = 0;
                    String text = "";

                    boolean shouldBeInitialMarking = marking.equals(solutionInitialMarking);
                    boolean isInFactInitialMarking = m.isInitialMarking();

                    if (solutionMarkings.contains(marking)) {
                        credits += pointsForCorrectMarking;
                        text += "Diese Markierung ist richtig (" + pointsForCorrectMarking + "P). ";
                    }
                    else {
                        credits += pointsForAdditionalMarking;
                        text += "Diese Markierung sollte nicht existieren (" + pointsForAdditionalMarking + "P). ";
                    }

                    if (shouldBeInitialMarking != isInFactInitialMarking) {
                        if (shouldBeInitialMarking) {
                            text += "Diese Markierung sollte als Startmarkierung gekennzeichnet sein (" + pointsForCorrectMarking + "P).";
                        }
                        else {
                            credits += pointsForAdditionalInitialMarking;
                            text += "Diese Markierung sollte nicht als Startmarkierung gekennzeichnet sein (" + pointsForAdditionalInitialMarking + "P).";
                        }
                    }
                    else if (shouldBeInitialMarking) {
                        credits += pointsForCorrectInitialMarking;
                        text += "Die Startmarkierung ist gesetzt (" + pointsForCorrectInitialMarking + "P).";
                    }

                    feedback.credits(credits).text(text.trim());
                    feedbacks.add(feedback);

                    solutionMarkings.remove(marking);
                }
                else if (element instanceof ReachabilityGraphArc arc) {
                    String sourceStr = StringUtil.stripSurroundingBrackets(StringUtil.stripWhitespaces(arc.getSource().getName()));
                    String targetStr = StringUtil.stripSurroundingBrackets(StringUtil.stripWhitespaces(arc.getTarget().getName()));
                    String transition = StringUtil.stripWhitespaces(arc.getTransition());

                    List<Integer> source = Arrays.stream(sourceStr.split(",")).filter(NumberUtils::isParsable).mapToInt(Integer::parseInt).boxed().collect(Collectors.toList());
                    List<Integer> target = Arrays.stream(targetStr.split(",")).filter(NumberUtils::isParsable).mapToInt(Integer::parseInt).boxed().collect(Collectors.toList());

                    Pair<List<Integer>, List<Integer>> pair = new Pair<>(source, target);
                    Set<String> expectedTransitions = solutionArcs.get(pair);

                    Feedback feedback = new Feedback().reference(arc.getType() + ":" + arc.getJSONElementID()).type(FeedbackType.AUTOMATIC);

                    if (expectedTransitions == null || !expectedTransitions.contains(transition)) {
                        feedback.credits(pointsForAdditionalArc).text("Diese Kante sollte nicht existieren (" + pointsForAdditionalArc + "P).");
                    }
                    else {
                        feedback.credits(pointsForCorrectArc).text("Diese Kante ist richtig (" + pointsForCorrectArc + "P).");
                    }

                    feedbacks.add(feedback);

                    if (solutionArcs.get(pair) != null) {
                        solutionArcs.get(pair).remove(transition);
                    }
                }
            }

            for (List<Integer> marking : solutionMarkings) {
                feedbacks.add(new Feedback().type(FeedbackType.MANUAL_UNREFERENCED).credits(0.)
                        .detailText("Die Markierung " + marking.toString() + " fehlt (" + pointsForCorrectMarking + "P)."));
            }

            for (Map.Entry<Pair<List<Integer>, List<Integer>>, Set<String>> arcs : solutionArcs.entrySet()) {
                for (String transition : arcs.getValue()) {
                    feedbacks.add(new Feedback().type(FeedbackType.MANUAL_UNREFERENCED).credits(0.).detailText("Die Kante von " + arcs.getKey().getFirst().toString() + " nach "
                            + arcs.getKey().getSecond().toString() + " mit Transition " + transition + " fehlt (" + pointsForCorrectArc + "P)."));
                }
            }

            // TODO: setting the correct submission causes the result to not be saved
            Result result = (new Result()).participation(modelingSubmission.getParticipation()).submission(null);
            result.setScore(Math.max(0, feedbacks.stream().mapToDouble(Feedback::getCredits).sum()), transformationModelingExercise.getMaxPoints());
            result = resultService.createNewRatedAutomaticResult(result, feedbacks);
            modelingSubmission.addResult(result);
        }
        catch (IOException e) {
            log.warn("Failed to grade transformation modeling exercise: {}", e.getMessage());
        }

        return modelingSubmission;
    }
}
