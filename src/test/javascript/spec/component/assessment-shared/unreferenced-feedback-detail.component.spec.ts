import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent, FaLayersComponent } from '@fortawesome/angular-fontawesome';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { UnreferencedFeedbackDetailComponent } from 'app/assessment/unreferenced-feedback-detail/unreferenced-feedback-detail.component';
import { GradingInstructionLinkIconComponent } from 'app/shared/grading-instruction-link-icon/grading-instruction-link-icon.component';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { NgModel } from '@angular/forms';
import { AssessmentCorrectionRoundBadgeComponent } from 'app/assessment/unreferenced-feedback-detail/assessment-correction-round-badge/assessment-correction-round-badge.component';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { QuotePipe } from 'app/shared/pipes/quote.pipe';
import { FeedbackContentPipe } from 'app/shared/pipes/feedback-content.pipe';

describe('Unreferenced Feedback Detail Component', () => {
    let comp: UnreferencedFeedbackDetailComponent;
    let fixture: ComponentFixture<UnreferencedFeedbackDetailComponent>;
    let sgiService: StructuredGradingCriterionService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(NgbTooltipModule)],
            declarations: [
                UnreferencedFeedbackDetailComponent,
                MockComponent(GradingInstructionLinkIconComponent),
                MockComponent(FaIconComponent),
                MockComponent(FaLayersComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(QuotePipe),
                MockPipe(FeedbackContentPipe),
                MockDirective(NgModel),
                MockDirective(DeleteButtonDirective),
                MockComponent(AssessmentCorrectionRoundBadgeComponent),
            ],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, MockProvider(StructuredGradingCriterionService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(UnreferencedFeedbackDetailComponent);
                comp = fixture.componentInstance;
                sgiService = fixture.debugElement.injector.get(StructuredGradingCriterionService);
            });
    });

    it('should update feedback with SGI and emit to parent', () => {
        const instruction: GradingInstruction = { id: 1, credits: 2, feedback: 'test', gradingScale: 'good', instructionDescription: 'description of instruction', usageCount: 0 };
        comp.feedback = {
            id: 1,
            detailText: 'feedback1',
            credits: 1.5,
        } as Feedback;
        // Fake call as a DragEvent
        jest.spyOn(sgiService, 'updateFeedbackWithStructuredGradingInstructionEvent').mockImplementation(() => {
            comp.feedback.gradingInstruction = instruction;
            comp.feedback.credits = instruction.credits;
        });
        // Call spy function with empty event
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
