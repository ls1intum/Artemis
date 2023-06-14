package de.tum.in.www1.artemis.exercise.textexercise;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.analytics.TextAssessmentEvent;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.enumeration.TextAssessmentEventType;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.exercise.ExerciseFactory;

public class TextExerciseFactory {

    public static TextExercise generateTextExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, Course course) {
        var textExercise = (TextExercise) ExerciseFactory.populateExercise(new TextExercise(), releaseDate, dueDate, assessmentDueDate, course);
        textExercise.setExampleSolution("This is my example solution");
        return textExercise;
    }

    public static TextExercise generateTextExerciseForExam(ExerciseGroup exerciseGroup) {
        var textExercise = (TextExercise) ExerciseFactory.populateExerciseForExam(new TextExercise(), exerciseGroup);
        textExercise.setExampleSolution("This is my example solution");
        return textExercise;
    }

    /**
     * Generates a TextAssessment event with the given parameters
     *
     * @param eventType       the type of the event
     * @param feedbackType    the type of the feedback
     * @param segmentType     the segment type of the event
     * @param courseId        the course id of the event
     * @param userId          the userid of the event
     * @param exerciseId      the exercise id of the event
     * @param participationId the participation id of the event
     * @param submissionId    the submission id of the event
     * @return the TextAssessment event with all the properties applied
     */
    public static TextAssessmentEvent generateTextAssessmentEvent(TextAssessmentEventType eventType, FeedbackType feedbackType, TextBlockType segmentType, Long courseId,
            Long userId, Long exerciseId, Long participationId, Long submissionId) {
        TextAssessmentEvent event = new TextAssessmentEvent();
        event.setId(null);
        event.setEventType(eventType);
        event.setFeedbackType(feedbackType);
        event.setSegmentType(segmentType);
        event.setCourseId(courseId);
        event.setTextExerciseId(exerciseId);
        event.setParticipationId(participationId);
        event.setSubmissionId(submissionId);
        event.setUserId(userId);
        return event;
    }

    /**
     * Generates a list of different combinations of assessment events based on the given parameters
     *
     * @param courseId        the course id of the event
     * @param userId          the userid of the event
     * @param exerciseId      the exercise id of the event
     * @param participationId the participation id of the event
     * @param submissionId    the submission id of the event
     * @return a list of TextAssessment events that are generated
     */
    public static List<TextAssessmentEvent> generateMultipleTextAssessmentEvents(Long courseId, Long userId, Long exerciseId, Long participationId, Long submissionId) {
        List<TextAssessmentEvent> events = new ArrayList<>();
        events.add(generateTextAssessmentEvent(TextAssessmentEventType.VIEW_AUTOMATIC_SUGGESTION_ORIGIN, FeedbackType.AUTOMATIC, TextBlockType.AUTOMATIC, courseId, userId,
                exerciseId, participationId, submissionId));
        events.add(generateTextAssessmentEvent(TextAssessmentEventType.EDIT_AUTOMATIC_FEEDBACK, FeedbackType.AUTOMATIC, TextBlockType.AUTOMATIC, courseId, userId, exerciseId,
                participationId, submissionId));
        events.add(generateTextAssessmentEvent(TextAssessmentEventType.HOVER_OVER_IMPACT_WARNING, FeedbackType.MANUAL, TextBlockType.AUTOMATIC, courseId, userId, exerciseId,
                participationId, submissionId));
        events.add(generateTextAssessmentEvent(TextAssessmentEventType.DELETE_FEEDBACK, FeedbackType.MANUAL, TextBlockType.AUTOMATIC, courseId, userId, exerciseId, participationId,
                submissionId));
        events.add(generateTextAssessmentEvent(TextAssessmentEventType.ADD_FEEDBACK_AUTOMATICALLY_SELECTED_BLOCK, FeedbackType.MANUAL, TextBlockType.AUTOMATIC, courseId, userId,
                exerciseId, participationId, submissionId));
        events.add(generateTextAssessmentEvent(TextAssessmentEventType.DELETE_FEEDBACK, FeedbackType.MANUAL, TextBlockType.AUTOMATIC, courseId, userId, exerciseId, participationId,
                submissionId));
        events.add(generateTextAssessmentEvent(TextAssessmentEventType.ADD_FEEDBACK_MANUALLY_SELECTED_BLOCK, FeedbackType.MANUAL, TextBlockType.MANUAL, courseId, userId,
                exerciseId, participationId, submissionId));
        events.add(generateTextAssessmentEvent(TextAssessmentEventType.SUBMIT_ASSESSMENT, FeedbackType.MANUAL, TextBlockType.MANUAL, courseId, userId, exerciseId, participationId,
                submissionId));
        events.add(generateTextAssessmentEvent(TextAssessmentEventType.CLICK_TO_RESOLVE_CONFLICT, FeedbackType.MANUAL, TextBlockType.MANUAL, courseId, userId, exerciseId,
                participationId, submissionId));
        events.add(generateTextAssessmentEvent(TextAssessmentEventType.ASSESS_NEXT_SUBMISSION, FeedbackType.MANUAL, TextBlockType.MANUAL, courseId, userId, exerciseId,
                participationId, submissionId));
        return events;
    }

    public static TextBlock generateTextBlock(int startIndex, int endIndex, String text) {
        final TextBlock textBlock = new TextBlock();
        textBlock.setStartIndex(startIndex);
        textBlock.setEndIndex(endIndex);
        textBlock.setText(text);
        textBlock.computeId();
        return textBlock;
    }

    public static TextBlock generateTextBlock(int startIndex, int endIndex) {
        return generateTextBlock(startIndex, endIndex, "");
    }

    /**
     * Generates example TextSubmissions
     *
     * @param count How many submissions should be generated (max. 10)
     * @return A list containing the generated TextSubmissions
     */
    public static List<TextSubmission> generateTextSubmissions(int count) {
        if (count > 10) {
            throw new IllegalArgumentException();
        }

        // Example texts for submissions
        String[] submissionTexts = {
                "Differences: \nAntipatterns: \n-Have one problem and two solutions(one problematic and one refactored)\n-Antipatterns are a sign of bad architecture and bad coding \nPattern:\n-Have one problem and one solution\n-Patterns are a sign of elaborated architecutre and coding",
                "The main difference between patterns and antipatterns is, that patterns show you a good way to do something and antipatterns show a bad way to do something. Nevertheless patterns may become antipatterns in the course of changing understanding of how good software engineering looks like. One example for that is functional decomposition, which used to be a pattern and \"good practice\". Over the time it turned out that it is not a goog way to solve problems, so it became a antipattern.\n\nA pattern itsself is a proposed solution to a problem that occurs often and in different situations.\nIn contrast to that a antipattern shows commonly made mistakes when dealing with a certain problem. Nevertheless a refactored solution is aswell proposed.",
                "1.Patterns can evolve into Antipatterns when change occurs\\n2. Pattern has one solution, whereas anti pattern can have subtypes of solution\\n3. Antipattern has negative consequences and symptom, where as patterns looks only into benefits and consequences",
                "Patterns: A way to Model code in differents ways \nAntipattern: A way of how Not to Model code",
                "Antipatterns are used when there are common mistakes in software management and development to find these, while patterns by themselves are used to build software systems in the context of frequent change by reducing complexity and isolating the change.\nAnother difference is that the antipatterns have problematic solution and then refactored solution, while patterns only have a solution.",
                "- In patterns we have a problem and a solution, in antipatterns we have a problematic solution and a refactored solution instead\n- patterns represent best practices from the industry etc. so proven concepts, whereas antipatterns shed a light on common mistakes during software development etc.",
                "1) Patterns have one solution, antipatterns have to solutions (one problematic and one refactored).\n2) for the coice of patterns code has to be written; for antipatterns, the bad smell code already exists",
                "Design Patterns:\n\nSolutions which are productive and efficient and are developed by Software Engineers over the years of practice and solving problems.\n\nAnti Patterns:\n\nKnown solutions which are actually bad or defective to certain kind of problems.",
                "Patterns has one problem and one solution.\nAntipatterns have one problematic solution and a solution for that. The antipattern happens when  a solution that is been used for a long time can not apply anymore. ",
                "Patterns identify problems and present solutions.\nAntipatterns identify problems but two kinds of solutions. One problematic solution and a better \"refactored\" version of the solution. Problematic solutions are suggested not to be used because they results in smells or hinder future work." };

        // Create Submissions with id's 0 - count
        List<TextSubmission> textSubmissions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            TextSubmission textSubmission = new TextSubmission((long) i).text(submissionTexts[i]);
            textSubmission.setLanguage(Language.ENGLISH);
            textSubmissions.add(textSubmission);
        }

        return textSubmissions;
    }
}
