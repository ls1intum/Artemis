import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { Feedback } from 'app/entities/feedback.model';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent, FaLayersComponent } from '@fortawesome/angular-fontawesome';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { AssessmentDetailComponent } from 'app/assessment/assessment-detail/assessment-detail.component';
import { GradingInstructionLinkIconComponent } from 'app/shared/grading-instruction-link-icon/grading-instruction-link-icon.component';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { NgModel } from '@angular/forms';
import { AssessmentCorrectionRoundBadgeComponent } from 'app/assessment/assessment-detail/assessment-correction-round-badge/assessment-correction-round-badge.component';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';

describe('Assessment Detail Component', () => {
    let comp: AssessmentDetailComponent;
    let fixture: ComponentFixture<AssessmentDetailComponent>;
    let sgiService: StructuredGradingCriterionService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(NgbTooltipModule)],
            declarations: [
                AssessmentDetailComponent,
                MockComponent(GradingInstructionLinkIconComponent),
                MockComponent(FaIconComponent),
                MockComponent(FaLayersComponent),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(NgModel),
                MockComponent(AssessmentCorrectionRoundBadgeComponent),
            ],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, MockProvider(StructuredGradingCriterionService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AssessmentDetailComponent);
                comp = fixture.componentInstance;
                sgiService = fixture.debugElement.injector.get(StructuredGradingCriterionService);
            });
    });

    it('should update feedback with SGI and emit to parent', () => {
        const instruction: GradingInstruction = { id: 1, credits: 2, feedback: 'test', gradingScale: 'good', instructionDescription: 'description of instruction', usageCount: 0 };
        comp.assessment = {
            id: 1,
            detailText: 'feedback1',
            credits: 1.5,
        } as Feedback;
        // Fake call as a DragEvent
        jest.spyOn(sgiService, 'updateFeedbackWithStructuredGradingInstructionEvent').mockImplementation(() => {
            comp.assessment.gradingInstruction = instruction;
            comp.assessment.credits = instruction.credits;
        });
        // Call spy function with empty event
        comp.updateAssessmentOnDrop(new Event(''));

        expect(comp.assessment.gradingInstruction).toBe(instruction);
        expect(comp.assessment.credits).toBe(instruction.credits);
    });

    it('should emit the assessment change after deletion', () => {
        comp.assessment = {
            id: 1,
            detailText: 'feedback1',
            credits: 1.5,
        } as Feedback;
        const emitSpy = jest.spyOn(comp.deleteAssessment, 'emit');
        const confirmStub = jest.spyOn(window, 'confirm').mockReturnValue(true);
        comp.delete();
        fixture.detectChanges();

        expect(emitSpy).toHaveBeenCalledOnce();
        expect(confirmStub).toHaveBeenCalledOnce();
    });
});
