import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { ManualTextSelectionComponent } from 'app/exercises/text/shared/manual-text-selection/manual-text-selection.component';
import { TextAssessmentEventType } from 'app/entities/text-assesment-event.model';
import { FeedbackType } from 'app/entities/feedback.model';
import { TextBlock, TextBlockType } from 'app/entities/text-block.model';
import { MockProvider } from 'ng-mocks';
import { TextAssessmentAnalytics } from 'app/exercises/text/assess/analytics/text-assesment-analytics.service';
import { ActivatedRoute } from '@angular/router';
import { SubmissionExerciseType, SubmissionType } from 'app/entities/submission.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { TextBlockRefGroup } from 'app/exercises/text/assess/manual-textblock-selection/manual-textblock-selection.component';

describe('ManualTextSelectionComponent', () => {
    let component: ManualTextSelectionComponent;
    let fixture: ComponentFixture<ManualTextSelectionComponent>;

    const submission = {
        submissionExerciseType: SubmissionExerciseType.TEXT,
        id: 2278,
        submitted: true,
        type: SubmissionType.MANUAL,
        text: 'First last text. Second text.',
    } as unknown as TextSubmission;

    const blocks = [
        {
            text: 'First last text. Second text.',
            startIndex: 0,
            endIndex: 16,
            submissionId: submission.id,
        } as any as TextBlock,
    ];
    const textBlockRefs = new TextBlockRef(blocks[0]);

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ManualTextSelectionComponent],
            providers: [MockProvider(TextAssessmentAnalytics), MockProvider(ActivatedRoute)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ManualTextSelectionComponent);
                component = fixture.componentInstance;
                component.textBlockRefGroup = new TextBlockRefGroup(textBlockRefs);
                fixture.detectChanges();
            });
    });

    it('should set words correctly', () => {
        component.submission = submission;
        component.words = new TextBlockRefGroup(textBlockRefs);

        expect(component.submissionWords).toEqual(['First', 'last', 'text.']);
    });

    it('should calculate word indices correctly', () => {
        component.submission = submission;
        component.words = new TextBlockRefGroup(textBlockRefs);
        component.calculateIndex(1);

        expect(component.currentWordIndex).toBe(6);
    });

    it('should reverse word entries if the tutor clicks on the last word first', () => {
        component.selectedWords = [{ word: 'last', index: 6 }];
        component.currentWordIndex = 0;
        component.selectWord('first');

        expect(component.selectedWords[0]).toEqual({ word: 'first', index: 0 });
        expect(component.selectedWords[1]).toEqual({ word: 'last', index: 6 });
    });

    it('should send assessment event when selecting text block manually', () => {
        component.ready = true;
        const sendAssessmentEventSpy = jest.spyOn(component.textAssessmentAnalytics, 'sendAssessmentEvent');
        component.selectWord('lastWord');
        fixture.detectChanges();
        expect(sendAssessmentEventSpy).toHaveBeenCalledOnce();
        expect(sendAssessmentEventSpy).toHaveBeenCalledWith(TextAssessmentEventType.ADD_FEEDBACK_MANUALLY_SELECTED_BLOCK, FeedbackType.MANUAL, TextBlockType.MANUAL);
    });
});
