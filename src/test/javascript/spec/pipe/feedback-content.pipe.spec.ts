import { TestBed } from '@angular/core/testing';
import { FeedbackContentPipe } from 'app/shared/pipes/feedback-content.pipe';
import { Feedback } from 'app/entities/feedback.model';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';

describe('FeedbackContentPipe', () => {
    let pipe: FeedbackContentPipe;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [FeedbackContentPipe],
        })
            .compileComponents()
            .then(() => {
                pipe = new FeedbackContentPipe();
            });
    });

    it('should return the detail text if present', () => {
        const feedback: Feedback = { detailText: 'content' };
        expect(pipe.transform(feedback)).toBe('content');
    });

    it.each([undefined, ''])('should return the grading instruction feedback if no direct detail text is present', (detailText) => {
        const gradingInstruction: GradingInstruction = {
            credits: 0,
            gradingScale: '',
            instructionDescription: '',
            feedback: 'grading instruction feedback',
        };
        const feedback: Feedback = { detailText: detailText, gradingInstruction: gradingInstruction };
        expect(pipe.transform(feedback)).toBe('grading instruction feedback');
    });
});
