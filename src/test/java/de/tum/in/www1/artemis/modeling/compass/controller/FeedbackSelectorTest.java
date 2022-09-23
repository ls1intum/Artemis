package de.tum.in.www1.artemis.modeling.compass.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.modeling.ModelElement;
import de.tum.in.www1.artemis.service.compass.controller.FeedbackSelector;

class FeedbackSelectorTest {

    @Test
    void testLongestFeedbackTextSelection() {
        Feedback feedback1 = new Feedback().credits(1.0).reference("Class:6aba5764-d102-4740-9675-b2bd0a4f2123").text("feedback text");
        Feedback feedback2 = new Feedback().credits(1.0).reference("Class:6aba5764-d102-4740-9675-b2bd0a4f2124").text("short");
        Feedback feedback3 = new Feedback().credits(1.0).reference("Class:6aba5764-d102-4740-9675-b2bd0a4f2125").text("very long feedback text");

        Result result = new Result();
        ModelElement modelElement = new ModelElement();
        modelElement.setModelElementType("Class");
        modelElement.setModelElementId("6aba5764-d102-4740-9675-b2bd0a4f2127");

        Feedback selectedFeedback = FeedbackSelector.selectFeedback(modelElement, List.of(feedback1, feedback2), result);

        assertThat(selectedFeedback).as("feedback was selected").isNotNull();
        assertThat(selectedFeedback.getReference()).as("feedback is assigned to the element").isEqualTo("Class:6aba5764-d102-4740-9675-b2bd0a4f2127");
        assertThat(selectedFeedback.getText()).as("feedback text of element is correct").isEqualTo("feedback text");

        selectedFeedback = FeedbackSelector.selectFeedback(modelElement, List.of(feedback1, feedback2, feedback3), result);
        assertThat(selectedFeedback).as("feedback was selected").isNotNull();
        assertThat(selectedFeedback.getReference()).as("feedback is assigned to the element").isEqualTo("Class:6aba5764-d102-4740-9675-b2bd0a4f2127");
        assertThat(selectedFeedback.getText()).as("feedback text of element is correct").isEqualTo("very long feedback text");
    }

    @Test
    void testConfidenceThreshold() {
        Feedback feedback1 = new Feedback().credits(1.0).reference("Class:6aba5764-d102-4740-9675-b2bd0a4f2121");
        Feedback feedback2 = new Feedback().credits(20.0).reference("Class:6aba5764-d102-4740-9675-b2bd0a4f2122").text("wrong text");

        Result result = new Result();
        ModelElement modelElement = new ModelElement();
        modelElement.setModelElementType("Class");
        modelElement.setModelElementId("6aba5764-d102-4740-9675-b2bd0a4f2126");

        Feedback selectedFeedback = FeedbackSelector.selectFeedback(modelElement, List.of(feedback2), result);

        assertThat(selectedFeedback).as("feedback was selected").isNotNull();
        assertThat(selectedFeedback.getReference()).as("feedback is assigned to the element").isEqualTo("Class:6aba5764-d102-4740-9675-b2bd0a4f2126");
        assertThat(selectedFeedback.getCredits()).as("credits of element are correct").isEqualTo(20);
        assertThat(selectedFeedback.getText()).as("feedback text of element is correct").isEqualTo("wrong text");

        feedback1 = feedback1.text("long feedback text");
        selectedFeedback = FeedbackSelector.selectFeedback(modelElement, List.of(feedback1, feedback2), result);
        assertThat(selectedFeedback).as("feedback not selected").isNull();

        Feedback feedback3 = new Feedback().credits(1.0).reference("Class:6aba5764-d102-4740-9675-b2bd0a4f2123").text("short text");
        selectedFeedback = FeedbackSelector.selectFeedback(modelElement, List.of(feedback1, feedback2, feedback3), result);
        assertThat(selectedFeedback).as("feedback not selected").isNull();

        Feedback feedback4 = new Feedback().credits(1.0).reference("Class:6aba5764-d102-4740-9675-b2bd0a4f2124").text("very long feedback text");
        selectedFeedback = FeedbackSelector.selectFeedback(modelElement, List.of(feedback1, feedback2, feedback3, feedback4), result);
        assertThat(selectedFeedback).as("feedback not selected").isNull();

        Feedback feedback5 = new Feedback().credits(1.0).reference("Class:6aba5764-d102-4740-9675-b2bd0a4f2125").text("medium text");
        selectedFeedback = FeedbackSelector.selectFeedback(modelElement, List.of(feedback1, feedback2, feedback3, feedback4, feedback5), result);

        assertThat(selectedFeedback).as("feedback was selected").isNotNull();
        assertThat(selectedFeedback.getReference()).as("feedback is assigned to the element").isEqualTo("Class:6aba5764-d102-4740-9675-b2bd0a4f2126");
        assertThat(selectedFeedback.getCredits()).as("credits of element are correct").isEqualTo(1);
        assertThat(selectedFeedback.getText()).as("feedback text of element is correct").isEqualTo("very long feedback text");
    }
}
