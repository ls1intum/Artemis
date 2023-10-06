import {
    Feedback,
    FeedbackSuggestionType,
    FeedbackType,
    STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER,
    SUBMISSION_POLICY_FEEDBACK_IDENTIFIER,
    buildFeedbackTextForReview,
} from 'app/entities/feedback.model';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';

describe('Feedback', () => {
    const createFeedback = (text: string, type: FeedbackType, reference?: string): Feedback => {
        const feedback = new Feedback();
        feedback.text = text;
        feedback.type = type;
        feedback.reference = reference;
        return feedback;
    };

    describe('buildFeedbackTextForReview', () => {
        const gradingInstruction = new GradingInstruction();
        gradingInstruction.feedback = 'Grading instruction feedback';

        it('should return the detailed text if no grading instruction is connected to the feedback', () => {
            const feedback = new Feedback();
            feedback.detailText = 'detail\nline2';

            const expectedText = 'detail<br>line2';
            expect(buildFeedbackTextForReview(feedback)).toBe(expectedText);
        });

        it('should return the text if no detail text and no grading instruction is connected to the feedback', () => {
            const feedback = new Feedback();
            feedback.text = 'simple text\nline2';

            const expectedText = 'simple text<br>line2';
            expect(buildFeedbackTextForReview(feedback)).toBe(expectedText);
        });

        it('should ignore the simple text if requested and no grading instruction is set', () => {
            const feedback = new Feedback();
            feedback.text = 'simple text\nline2';

            expect(buildFeedbackTextForReview(feedback, false)).toBe('');
        });

        it('should only return the the grading instruction feedback if no other text is set', () => {
            const feedback = new Feedback();
            feedback.gradingInstruction = gradingInstruction;

            expect(buildFeedbackTextForReview(feedback)).toBe(gradingInstruction.feedback);
        });

        it('should add grading instruction feedback and detail text and text if all are available', () => {
            const feedback = new Feedback();
            feedback.gradingInstruction = gradingInstruction;
            feedback.detailText = 'multi\nline\ndetail';
            feedback.text = 'simple text';

            const expectedText = 'Grading instruction feedback<br>multi<br>line<br>detail<br>simple text';
            expect(buildFeedbackTextForReview(feedback)).toBe(expectedText);

            const expectedTextIgnoreSimpleText = 'Grading instruction feedback<br>multi<br>line<br>detail';
            expect(buildFeedbackTextForReview(feedback, false)).toBe(expectedTextIgnoreSimpleText);
        });
    });

    describe('static detection functions', () => {
        it('should detect Static Code Analysis feedback', () => {
            const feedback1 = createFeedback('SCAFeedbackIdentifier: Code smells detected', FeedbackType.AUTOMATIC);
            const feedback2 = createFeedback('Test Case 1 Failed', FeedbackType.AUTOMATIC);

            expect(Feedback.isStaticCodeAnalysisFeedback(feedback1)).toBeTrue();
            expect(Feedback.isStaticCodeAnalysisFeedback(feedback2)).toBeFalse();
        });

        it('should detect Submission Policy feedback', () => {
            const feedback1 = createFeedback('SubPolFeedbackIdentifier: Submission rejected', FeedbackType.AUTOMATIC);
            const feedback2 = createFeedback('Test Case 1 Failed', FeedbackType.AUTOMATIC);

            expect(Feedback.isSubmissionPolicyFeedback(feedback1)).toBeTrue();
            expect(Feedback.isSubmissionPolicyFeedback(feedback2)).toBeFalse();
        });

        it('should detect Feedback Suggestion', () => {
            const feedback1 = createFeedback('FeedbackSuggestion: Consider refactoring', FeedbackType.MANUAL);
            const feedback2 = createFeedback('Test Case 1 Failed', FeedbackType.AUTOMATIC);

            expect(Feedback.isFeedbackSuggestion(feedback1)).toBeTrue();
            expect(Feedback.isFeedbackSuggestion(feedback2)).toBeFalse();
        });

        it('should determine Feedback Suggestion Type', () => {
            const feedback1 = createFeedback('FeedbackSuggestion:accepted: Consider refactoring', FeedbackType.MANUAL);
            const feedback2 = createFeedback('FeedbackSuggestion:adapted: Partially accepted', FeedbackType.MANUAL);
            const feedback3 = createFeedback('FeedbackSuggestion: Consider refactoring', FeedbackType.MANUAL);
            const feedback4 = createFeedback('Test Case 1 Failed', FeedbackType.AUTOMATIC);

            expect(Feedback.getFeedbackSuggestionType(feedback1)).toBe(FeedbackSuggestionType.ACCEPTED);
            expect(Feedback.getFeedbackSuggestionType(feedback2)).toBe(FeedbackSuggestionType.ADAPTED);
            expect(Feedback.getFeedbackSuggestionType(feedback3)).toBe(FeedbackSuggestionType.SUGGESTED);
            expect(Feedback.getFeedbackSuggestionType(feedback4)).toBe(FeedbackSuggestionType.NO_SUGGESTION);
        });
    });

    describe('reference extraction functions', () => {
        it('should extract the file path from the feedback reference', () => {
            const feedback1 = createFeedback('Feedback with reference', FeedbackType.AUTOMATIC, 'file:src/com/example/package/MyClass.java_line:13');
            const feedback2 = createFeedback('Feedback without reference', FeedbackType.AUTOMATIC);
            const feedback3 = createFeedback('Feedback with invalid reference', FeedbackType.AUTOMATIC, 'invalid:src/com/example/package/MyClass.java_line:13');

            expect(Feedback.getReferenceFilePath(feedback1)).toBe('src/com/example/package/MyClass.java');
            expect(Feedback.getReferenceFilePath(feedback2)).toBeUndefined();
            expect(Feedback.getReferenceFilePath(feedback3)).toBeUndefined();
        });

        it('should extract the file path from the feedback reference for file names with an underscore', () => {
            const feedback = createFeedback('Feedback with reference', FeedbackType.AUTOMATIC, 'file:src/com/example/package/with_an_underscore.java_line:13');
            expect(Feedback.getReferenceFilePath(feedback)).toBe('src/com/example/package/with_an_underscore.java');
        });

        it('should extract the line number from the feedback reference', () => {
            const feedback1 = createFeedback('Feedback with reference', FeedbackType.AUTOMATIC, 'file:src/com/example/package/MyClass.java_line:13');
            const feedback2 = createFeedback('Feedback without reference', FeedbackType.AUTOMATIC);
            const feedback3 = createFeedback('Feedback with invalid reference', FeedbackType.AUTOMATIC, 'invalid:src/com/example/package/MyClass.java_line:13');
            const feedback4 = createFeedback('Feedback with malformed reference', FeedbackType.AUTOMATIC, 'file:src/com/example/package/MyClass.java_invalid:13');
            const feedback5 = createFeedback('Feedback with line that is not a number', FeedbackType.AUTOMATIC, 'file:src/com/example/package/MyClass.java_line:xyz');

            expect(Feedback.getReferenceLine(feedback1)).toBe(13);
            expect(Feedback.getReferenceLine(feedback2)).toBeUndefined();
            expect(Feedback.getReferenceLine(feedback3)).toBeUndefined();
            expect(Feedback.getReferenceLine(feedback4)).toBeUndefined();
            expect(Feedback.getReferenceLine(feedback5)).toBeUndefined();
        });

        it('should extract the line number from the feedback reference for file names with an underscore', () => {
            const feedback = createFeedback('Feedback with reference', FeedbackType.AUTOMATIC, 'file:src/com/example/package/file_with_an_underscore.java_line:13');
            expect(Feedback.getReferenceLine(feedback)).toBe(13);
        });
    });

    describe('isTestCaseFeedback', () => {
        it('should correctly detect manual feedback', () => {
            const feedback: Feedback = { type: FeedbackType.MANUAL, detailText: 'content' };
            expect(Feedback.isTestCaseFeedback(feedback)).toBeFalse();
        });

        it('should correctly detect sca feedback', () => {
            const feedback: Feedback = { type: FeedbackType.AUTOMATIC, text: STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER };
            expect(Feedback.isTestCaseFeedback(feedback)).toBeFalse();
        });

        it('should correctly detect submission policy feedback', () => {
            const feedback: Feedback = { type: FeedbackType.AUTOMATIC, text: SUBMISSION_POLICY_FEEDBACK_IDENTIFIER };
            expect(Feedback.isTestCaseFeedback(feedback)).toBeFalse();
        });

        it('should correctly detect test case feedback', () => {
            const feedback: Feedback = { type: FeedbackType.AUTOMATIC, detailText: 'content', testCase: { testName: 'test1' } };
            expect(Feedback.isTestCaseFeedback(feedback)).toBeTrue();
        });
    });
});
