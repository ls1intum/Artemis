package de.tum.in.www1.artemis.service.compass.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.GradingInstruction;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.modeling.ModelElement;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;

public class FeedbackSelector {

    /**
     * Selects the feedback to suggest from the list of feedback then sets up the selected feedback for the given element and result
     *
     * @param modelElement the UML model element the Feedback will be suggested for
     * @param feedbackList the list of feedback to choose from
     * @param result the result the selected feedback will belong to
     * @return the feedback that is selected for suggestion
     */
    public static Feedback selectFeedback(ModelElement modelElement, List<Feedback> feedbackList, Result result) {
        if (feedbackList == null || feedbackList.isEmpty()) {
            return null;
        }
        // counts the amount of feedback elements that have the same credits assigned, i.e. maps "credits -> amount" for every unique credit number
        Map<Double, Integer> creditCount = new HashMap<>();
        // collects the feedback texts of the feedback elements that have the same credits assigned, i.e. maps "credits -> set of feedback text" for every unique credit number
        Map<Double, Set<String>> creditFeedbackText = new HashMap<>();
        // collects associated grading instruction of the feedback that have the same credits assigned, i.e. maps "credits -> GradingInstruction" for every unique credit number
        Map<Double, GradingInstruction> creditGradingInstruction = new HashMap<>();

        for (Feedback existingFeedback : feedbackList) {
            double credits = existingFeedback.getCredits();
            creditCount.put(credits, creditCount.getOrDefault(credits, 0) + 1);

            if (existingFeedback.getText() != null) {
                Set<String> feedbackTextForCredits = creditFeedbackText.getOrDefault(credits, new HashSet<>());
                feedbackTextForCredits.add(existingFeedback.getText());
                creditFeedbackText.put(credits, feedbackTextForCredits);
            }
            if (existingFeedback.getGradingInstruction() != null) {
                creditGradingInstruction.put(credits, existingFeedback.getGradingInstruction());
            }
        }

        double maxCount = creditCount.values().stream().mapToInt(i -> i).max().orElse(0);
        double confidence = maxCount / feedbackList.size();
        double maxCountCredits = creditCount.entrySet().stream().filter(entry -> entry.getValue() == maxCount).map(Map.Entry::getKey).findFirst().orElse(0.0);
        Set<String> feedbackTextForMaxCountCredits = creditFeedbackText.getOrDefault(maxCountCredits, new HashSet<>());
        String text = feedbackTextForMaxCountCredits.stream().filter(Objects::nonNull).max(Comparator.comparingInt(String::length)).orElse("");
        GradingInstruction gradingInstruction = creditGradingInstruction.getOrDefault(maxCountCredits, new GradingInstruction());

        if (confidence < CompassConfiguration.ELEMENT_CONFIDENCE_THRESHOLD) {
            return null;
        }

        Feedback feedback = new Feedback();
        feedback.setCredits(roundCredits(maxCountCredits));
        feedback.setPositiveViaCredits();
        feedback.setReference(buildReferenceString(modelElement));
        feedback.setType(FeedbackType.AUTOMATIC);
        feedback.setResult(result);
        if (gradingInstruction.getId() != null) {
            feedback.setGradingInstruction(gradingInstruction);
        }
        else {
            feedback.setText(text);
        }
        return feedback;
    }

    /**
     * Creates the reference string to be stored in a Feedback element for modeling submissions. The reference of a modeling Feedback stores the type of the corresponding UML
     * element and its jsonElementId and is of the form "<umlElementType>:<jsonElementIds>".
     *
     * @param modelElement the UML model element the Feedback belongs to
     * @return the formatted reference string containing the element type and its jsonElementId
     */
    private static String buildReferenceString(ModelElement modelElement) {
        return modelElement.getModelElementType() + ":" + modelElement.getModelElementId();
    }

    /**
     * Round credits to avoid machine precision errors, make the credits more readable and give a slight advantage which makes 100% scores easier reachable.
     * <p>
     * Positive values > [x.0, x.15) gets rounded to x.0 > [x.15, x.65) gets rounded to x.5 > [x.65, x + 1) gets rounded to x + 1
     * <p>
     * Negative values > [-x - 1, -x.85) gets rounded to -x - 1 > [-x.85, -x.35) gets rounded to -x.5 > [-x.35, -x.0) gets rounded to -x.0
     *
     * @param credits to round
     * @return the rounded compass credits
     */
    private static double roundCredits(double credits) {
        BigDecimal point = new BigDecimal(String.valueOf(credits));
        boolean isNegative = point.doubleValue() < 0;
        // get the fractional part of the entry score and subtract 0.15 (e.g. 1.5 -> 0.35 or -1.5 -> -0.65)
        double fractionalPart = point.remainder(BigDecimal.ONE).subtract(new BigDecimal(String.valueOf(0.15))).doubleValue();
        // remove the fractional part of the entry score (e.g. 1.5 -> 1 or -1.5 -> -1)
        point = point.setScale(0, RoundingMode.DOWN);

        if (isNegative) {
            // for negative values subtract 1 to get the lower integer value (e.g. -1.5 -> -1 -> -2)
            point = point.subtract(BigDecimal.ONE);
            // and add 1 to the fractional part to get it into the same positive range as we have for positive values (e.g. -1.5 -> -0.5 -> 0.5)
            fractionalPart += 1;
        }

        if (fractionalPart >= 0.5) {
            point = point.add(BigDecimal.ONE);
        }
        else if (fractionalPart >= 0) {
            point = point.add(new BigDecimal(String.valueOf(0.5)));
        }

        return point.doubleValue();
    }
}
