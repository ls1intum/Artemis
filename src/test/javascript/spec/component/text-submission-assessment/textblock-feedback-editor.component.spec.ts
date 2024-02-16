import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { TextblockFeedbackEditorComponent } from 'app/exercises/text/assess/textblock-feedback-editor/textblock-feedback-editor.component';
import { Feedback, FeedbackCorrectionErrorType, FeedbackType } from 'app/entities/feedback.model';
import { TextBlock, TextBlockType } from 'app/entities/text-block.model';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NgbModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { FaLayersComponent } from '@fortawesome/angular-fontawesome';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { AssessmentCorrectionRoundBadgeComponent } from 'app/assessment/unreferenced-feedback-detail/assessment-correction-round-badge/assessment-correction-round-badge.component';
import { GradingInstructionLinkIconComponent } from 'app/shared/grading-instruction-link-icon/grading-instruction-link-icon.component';
import { ChangeDetectorRef } from '@angular/core';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockTranslateService, TranslateTestingModule } from '../../helpers/mocks/service/mock-translate.service';
import { TextAssessmentEventType } from 'app/entities/text-assesment-event.model';
import { NgModel } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TextblockFeedbackDropdownComponent } from 'app/exercises/text/assess/textblock-feedback-editor/dropdown/textblock-feedback-dropdown.component';

describe('TextblockFeedbackEditorComponent', () => {
    let component: TextblockFeedbackEditorComponent;
    let fixture: ComponentFixture<TextblockFeedbackEditorComponent>;
    let compiled: any;

    const textBlock = { id: '1' } as TextBlock;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateModule.forRoot(), TranslateTestingModule, MockDirective(NgbTooltip)],
            declarations: [
                TextblockFeedbackEditorComponent,
                AssessmentCorrectionRoundBadgeComponent,
                MockComponent(TextblockFeedbackDropdownComponent),
                MockComponent(ConfirmIconComponent),
                MockComponent(FaLayersComponent),
                MockComponent(GradingInstructionLinkIconComponent),
                MockDirective(TranslateDirective),
                MockDirective(NgModel),
            ],
            providers: [
                MockProvider(ChangeDetectorRef),
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
            ],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(TextblockFeedbackEditorComponent);
        component = fixture.componentInstance;
        component.textBlock = textBlock;
        component.feedback = Feedback.forText(textBlock);
        component.feedback.gradingInstruction = new GradingInstruction();
        component.feedback.gradingInstruction.usageCount = 0;
        compiled = fixture.debugElement.nativeElement;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();

        const textarea = compiled.querySelector('textarea');
        expect(textarea).toBeTruthy();

        const input = compiled.querySelector('input');
        expect(input).toBeTruthy();
    });

    it('should show delete button for empty feedback only', () => {
        let button = compiled.querySelector('.close fa-icon[ng-reflect-icon="[object Object]"]');
        let confirm = compiled.querySelector('.close jhi-confirm-icon');
        expect(button).toBeTruthy();
        expect(confirm).toBeFalsy();

        component.feedback.credits = 1;
        fixture.detectChanges();
        button = compiled.querySelector('.close fa-icon[ng-reflect-icon="[object Object]"]');
        confirm = compiled.querySelector('.close jhi-confirm-icon');
        expect(button).toBeFalsy();
        expect(confirm).toBeTruthy();

        component.feedback.detailText = 'Lorem Ipsum';
        fixture.detectChanges();
        button = compiled.querySelector('.close fa-icon[ng-reflect-icon="[object Object]"]');
        confirm = compiled.querySelector('.close jhi-confirm-icon');
        expect(button).toBeFalsy();
        expect(confirm).toBeTruthy();

        component.feedback.credits = 0;
        fixture.detectChanges();
        button = compiled.querySelector('.close fa-icon[ng-reflect-icon="[object Object]"]');
        confirm = compiled.querySelector('.close jhi-confirm-icon');
        expect(button).toBeFalsy();
        expect(confirm).toBeTruthy();

        component.feedback.detailText = '';
        fixture.detectChanges();

        button = compiled.querySelector('.close fa-icon[ng-reflect-icon="[object Object]"]');
        confirm = compiled.querySelector('.close jhi-confirm-icon');
        expect(button).toBeTruthy();
        expect(confirm).toBeFalsy();
    });

    it('should call escKeyup when keyEvent', () => {
        component.feedback.credits = 0;
        component.feedback.detailText = '';
        jest.spyOn(component, 'escKeyup');
        const event = new KeyboardEvent('keydown', {
            key: 'Esc',
        });
        const textarea = fixture.nativeElement.querySelector('textarea');
        textarea.dispatchEvent(event);
        fixture.detectChanges();
        expect(component.escKeyup).toHaveBeenCalledOnce();
    });

    it('should show confirmIcon if feedback dismission needs to be confirmed', () => {
        component.confirmIconComponent = new ConfirmIconComponent();
        component.feedback.credits = 1;
        jest.spyOn(component, 'escKeyup');
        const confirmSpy = jest.spyOn(component.confirmIconComponent, 'toggle');

        component.escKeyup();
        fixture.detectChanges();
        expect(confirmSpy).toHaveBeenCalledOnce();
    });

    it('should show link icon when feedback is associated with grading instruction', () => {
        component.feedback.gradingInstruction = new GradingInstruction();
        fixture.detectChanges();
        const linkIcon = compiled.querySelector('.form-group jhi-grading-instruction-link-icon');
        expect(linkIcon).toBeTruthy();
    });

    it('should not show link icon when feedback is not associated with grading instruction', () => {
        component.feedback.gradingInstruction = undefined;
        fixture.detectChanges();
        const linkIcon = compiled.querySelector('.form-group jhi-grading-instruction-link-icon');
        expect(linkIcon).toBeFalsy();
    });

    it('should send assessment event on dismiss button click', () => {
        component.feedback.type = FeedbackType.MANUAL;
        component.textBlock.type = TextBlockType.MANUAL;
        const sendAssessmentEvent = jest.spyOn<any, any>(component.textAssessmentAnalytics, 'sendAssessmentEvent');
        component.dismiss();
        fixture.detectChanges();
        expect(sendAssessmentEvent).toHaveBeenCalledWith(TextAssessmentEventType.DELETE_FEEDBACK, FeedbackType.MANUAL, TextBlockType.MANUAL);
    });

    it('should set correctionStatus of the feedback to undefined on score click', () => {
        // given
        component.feedback.correctionStatus = FeedbackCorrectionErrorType.UNNECESSARY_FEEDBACK;

        // when
        component.onScoreClick(new MouseEvent(''));

        // then
        expect(component.feedback.correctionStatus).toBeUndefined();
    });

    it('should set correctionStatus of the feedback to undefined on connection of feedback with the grading instruction', () => {
        // given
        component.feedback.correctionStatus = FeedbackCorrectionErrorType.MISSING_GRADING_INSTRUCTION;
        jest.spyOn(component.structuredGradingCriterionService, 'updateFeedbackWithStructuredGradingInstructionEvent').mockImplementation();

        // when
        component.connectFeedbackWithInstruction(new Event(''));

        // then
        expect(component.feedback.correctionStatus).toBeUndefined();
    });

    it('should send assessment event if feedback type changed', () => {
        component.feedback.text = 'FeedbackSuggestion:accepted:Test';
        const typeSpy = jest.spyOn(component.textAssessmentAnalytics, 'sendAssessmentEvent');
        component.didChange();
        expect(typeSpy).toHaveBeenCalledOnce();
    });
});
