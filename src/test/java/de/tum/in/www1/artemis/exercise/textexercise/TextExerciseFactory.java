package de.tum.in.www1.artemis.exercise.textexercise;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.analytics.TextAssessmentEvent;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.TextAssessmentEventType;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.exercise.ExerciseFactory;

/**
 * Factory for creating TextExercises and related objects.
 */
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
}
