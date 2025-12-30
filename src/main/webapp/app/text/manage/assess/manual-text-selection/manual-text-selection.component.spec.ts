import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ManualTextSelectionComponent } from 'app/text/manage/assess/manual-text-selection/manual-text-selection.component';
import { TextAssessmentEventType } from 'app/text/shared/entities/text-assesment-event.model';
import { FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { TextBlock, TextBlockType } from 'app/text/shared/entities/text-block.model';
import { SubmissionExerciseType, SubmissionType } from 'app/exercise/shared/entities/submission/submission.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { TextBlockRef } from 'app/text/shared/entities/text-block-ref.model';
import { TextBlockRefGroup } from 'app/text/manage/assess/manual-textblock-selection/manual-textblock-selection.component';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';

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
            providers: [
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ id: 123 }) },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
            ],
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
        fixture.changeDetectorRef.detectChanges();
        expect(sendAssessmentEventSpy).toHaveBeenCalledOnce();
        expect(sendAssessmentEventSpy).toHaveBeenCalledWith(TextAssessmentEventType.ADD_FEEDBACK_MANUALLY_SELECTED_BLOCK, FeedbackType.MANUAL, TextBlockType.MANUAL);
    });
});
