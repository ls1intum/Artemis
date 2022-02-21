import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { buildFeedbackTextForReview, Feedback } from 'app/entities/feedback.model';

describe('Feedback', () => {
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
});
