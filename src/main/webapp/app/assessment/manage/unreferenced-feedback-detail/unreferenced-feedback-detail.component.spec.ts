import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { UnreferencedFeedbackDetailComponent } from 'app/assessment/manage/unreferenced-feedback-detail/unreferenced-feedback-detail.component';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { FeedbackService } from 'app/exercise/feedback/services/feedback.service';
import { MockTranslateService } from '../../../../../../test/javascript/spec/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('Unreferenced Feedback Detail Component', () => {
    let comp: UnreferencedFeedbackDetailComponent;
    let fixture: ComponentFixture<UnreferencedFeedbackDetailComponent>;
    let feedbackService: FeedbackService;
    let sgiService: StructuredGradingCriterionService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [MockProvider(StructuredGradingCriterionService), MockProvider(FeedbackService), { provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(UnreferencedFeedbackDetailComponent);
                comp = fixture.componentInstance;
                feedbackService = TestBed.inject(FeedbackService);
                sgiService = TestBed.inject(StructuredGradingCriterionService); // Add this line to inject sgiService
            });
    });

    it('should call getLongFeedbackText on init if feedback has long text', async () => {
        const feedbackId = 42;
        const resultId = 1;
        const exampleText = 'This is a long feedback text';

        comp.feedback = { id: feedbackId, hasLongFeedbackText: true } as Feedback;
        fixture.componentRef.setInput('resultId', resultId);
        const getLongFeedbackTextSpy = jest.spyOn(feedbackService, 'getLongFeedbackText').mockResolvedValue(exampleText);

        comp.ngOnInit();
        expect(getLongFeedbackTextSpy).toHaveBeenCalledWith(feedbackId);
    });

    it('should update feedback with SGI and emit to parent', () => {
        const instruction: GradingInstruction = { id: 1, credits: 2, feedback: 'test', gradingScale: 'good', instructionDescription: 'description of instruction', usageCount: 0 };
        comp.feedback = {
            id: 1,
            detailText: 'feedback1',
            credits: 1.5,
        } as Feedback;

        jest.spyOn(sgiService, 'updateFeedbackWithStructuredGradingInstructionEvent').mockImplementation(() => {
            comp.feedback.gradingInstruction = instruction;
            comp.feedback.credits = instruction.credits;
        });

        comp.updateFeedbackOnDrop(new Event(''));

        expect(comp.feedback.gradingInstruction).toBe(instruction);
        expect(comp.feedback.credits).toBe(instruction.credits);
    });

    it('should emit the assessment change after deletion', () => {
        comp.feedback = {
            id: 1,
            detailText: 'feedback1',
            credits: 1.5,
        } as Feedback;
        const emitSpy = jest.spyOn(comp.onFeedbackDelete, 'emit');
        comp.delete();
        fixture.detectChanges();

        expect(emitSpy).toHaveBeenCalledOnce();
    });

    it('should mark automatic feedback and feedback suggestions as adapted when they are modified', () => {
        comp.feedback = {
            id: 1,
            type: FeedbackType.AUTOMATIC,
            text: 'FeedbackSuggestion:accepted:feedback1',
            detailText: 'feedback1',
            credits: 1.5,
        } as Feedback;
        const emitSpy = jest.spyOn(comp.onFeedbackChange, 'emit');
        comp.emitChanges();
        expect(emitSpy).toHaveBeenCalledWith({
            id: 1,
            type: FeedbackType.AUTOMATIC_ADAPTED,
            text: 'FeedbackSuggestion:adapted:feedback1',
            detailText: 'feedback1',
            credits: 1.5,
        } as Feedback);
    });
});
