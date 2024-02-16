package de.tum.in.www1.artemis.exercise.textexercise;

import java.time.ZonedDateTime;
import java.util.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.analytics.TextAssessmentEvent;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.TextAssessmentEventType;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exercise.ExerciseFactory;

/**
 * Factory for creating TextExercises and related objects.
 */
public class TextExerciseFactory {

    /**
     * Generates a TextExercise for a Course.
     *
     * @param releaseDate       The release date of the TextExercise
     * @param dueDate           The due date of the TextExercise
     * @param assessmentDueDate The assessment due date of the TextExercise
     * @param course            The Course to which the TextExercise belongs
     * @return The generated TextExercise
     */
    public static TextExercise generateTextExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, Course course) {
        var textExercise = (TextExercise) ExerciseFactory.populateExercise(new TextExercise(), releaseDate, dueDate, assessmentDueDate, course);
        textExercise.setExampleSolution("This is my example solution");
        return textExercise;
    }

    /**
     * Generates a TextExercise for an Exam.
     *
     * @param exerciseGroup The ExerciseGroup to which the TextExercise belongs
     * @return The generated TextExercise
     */
    public static TextExercise generateTextExerciseForExam(ExerciseGroup exerciseGroup) {
        var textExercise = (TextExercise) ExerciseFactory.populateExerciseForExam(new TextExercise(), exerciseGroup);
        textExercise.setExampleSolution("This is my example solution");
        return textExercise;
    }

    /**
     * Generates a TextExercise for an Exam.
     *
     * @param exerciseGroup The ExerciseGroup to which the TextExercise belongs
     * @param title         The title of the TextExercise
     * @return The generated TextExercise
     */
    public static TextExercise generateTextExerciseForExam(ExerciseGroup exerciseGroup, String title) {
        var textExercise = (TextExercise) ExerciseFactory.populateExerciseForExam(new TextExercise(), exerciseGroup, title);
        textExercise.setExampleSolution("This is my example solution");
        return textExercise;
    }

    /**
     * Generates a TextAssessment event with the given parameters
     *
     * @param eventType       The type of the event
     * @param feedbackType    The type of the feedback
     * @param segmentType     The segment type of the event
     * @param courseId        The course id of the event
     * @param userId          The userid of the event
     * @param exerciseId      The exercise id of the event
     * @param participationId The participation id of the event
     * @param submissionId    The submission id of the event
     * @return The TextAssessment event with all the properties applied
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
     * Generates a list of different combinations of TextAssessmentEvents based on the given parameters
     *
     * @param courseId        The course id of the event
     * @param userId          The user id of the event
     * @param exerciseId      The exercise id of the event
     * @param participationId The participation id of the event
     * @param submissionId    The submission id of the event
     * @return The generated List of TextAssessmentEvents
     */
    public static List<TextAssessmentEvent> generateMultipleTextAssessmentEvents(Long courseId, Long userId, Long exerciseId, Long participationId, Long submissionId) {
        List<TextAssessmentEvent> events = new ArrayList<>();
        events.add(generateTextAssessmentEvent(TextAssessmentEventType.EDIT_AUTOMATIC_FEEDBACK, FeedbackType.AUTOMATIC, TextBlockType.AUTOMATIC, courseId, userId, exerciseId,
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
        events.add(generateTextAssessmentEvent(TextAssessmentEventType.ASSESS_NEXT_SUBMISSION, FeedbackType.MANUAL, TextBlockType.MANUAL, courseId, userId, exerciseId,
                participationId, submissionId));
        return events;
    }

    /**
     * Generates a TextBlock with the given parameters.
     *
     * @param startIndex The start index of the TextBlock
     * @param endIndex   The end index of the TextBlock
     * @param text       The text of the TextBlock
     * @return The generated TextBlock
     */
    public static TextBlock generateTextBlock(int startIndex, int endIndex, String text) {
        final TextBlock textBlock = new TextBlock();
        textBlock.setStartIndex(startIndex);
        textBlock.setEndIndex(endIndex);
        textBlock.setText(text);
        textBlock.computeId();
        return textBlock;
    }

    /**
     * Generates a TextBlock with the given indices and empty text.
     *
     * @param startIndex The start index of the TextBlock
     * @param endIndex   The end index of the TextBlock
     * @return The generated TextBlock
     */
    public static TextBlock generateTextBlock(int startIndex, int endIndex) {
        return generateTextBlock(startIndex, endIndex, "");
    }

    /**
     * Generates a TextSubmission with the passed text.
     *
     * @param textExercise The TextExercise to which the TextSubmission belongs
     * @param text         The text of the TextSubmission
     * @return The generated TextSubmission
     */
    public static TextSubmission generateTextExerciseSubmission(TextExercise textExercise, String text) {
        TextSubmission submission = new TextSubmission();
        StudentParticipation studentParticipation = new StudentParticipation();
        submission.setParticipation(studentParticipation);

        submission.setText(text);

        textExercise.getStudentParticipations().add(studentParticipation);
        return submission;
    }

    /**
     * Generates a Set with a given number of TextBlocks.
     *
     * @param count The number of TextBlocks to create
     * @return The generated Set of TextBlocks
     */
    public static Set<TextBlock> generateTextBlocks(int count) {
        Set<TextBlock> textBlocks = new HashSet<>();
        TextBlock textBlock;
        for (int i = 0; i < count; i++) {
            textBlock = new TextBlock();
            textBlock.setText("TextBlock" + i);
            textBlocks.add(textBlock);
        }
        return textBlocks;
    }
}
