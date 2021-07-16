import * as sinon from 'sinon';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { TextblockFeedbackEditorComponent } from 'app/exercises/text/assess/textblock-feedback-editor/textblock-feedback-editor.component';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { TextBlock } from 'app/entities/text-block.model';
import { ArtemisConfirmIconModule } from 'app/shared/confirm-icon/confirm-icon.module';
import { TranslateModule } from '@ngx-translate/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockProvider } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { FeedbackConflict } from 'app/entities/feedback-conflict';
import { AssessmentCorrectionRoundBadgeComponent } from 'app/assessment/assessment-detail/assessment-correction-round-badge/assessment-correction-round-badge.component';
import { ArtemisGradingInstructionLinkIconModule } from 'app/shared/grading-instruction-link-icon/grading-instruction-link-icon.module';
import { ChangeDetectorRef } from '@angular/core';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';

describe('TextblockFeedbackEditorComponent', () => {
    let component: TextblockFeedbackEditorComponent;
    let fixture: ComponentFixture<TextblockFeedbackEditorComponent>;
    let compiled: any;

    const textBlock = { id: 'f6773c4b3c2d057fd3ac11f02df31c0a3e75f800' } as TextBlock;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule, TranslateModule.forRoot(), ArtemisConfirmIconModule, ArtemisGradingInstructionLinkIconModule],
            declarations: [TextblockFeedbackEditorComponent, AssessmentCorrectionRoundBadgeComponent],
            providers: [MockProvider(ChangeDetectorRef), { provide: NgbModal, useClass: MockNgbModalService }],
        })
            .overrideModule(ArtemisTestModule, {
                remove: {
                    declarations: [MockComponent(FaIconComponent)],
                    exports: [MockComponent(FaIconComponent)],
                },
            })
            .compileComponents();
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
        let button = compiled.querySelector('.close fa-icon[icon="times"]');
        let confirm = compiled.querySelector('.close jhi-confirm-icon');
        expect(button).toBeTruthy();
        expect(confirm).toBeFalsy();

        component.feedback.credits = 1;
        fixture.detectChanges();
        button = compiled.querySelector('.close fa-icon[icon="times"]');
        confirm = compiled.querySelector('.close jhi-confirm-icon');
        expect(button).toBeFalsy();
        expect(confirm).toBeTruthy();

        component.feedback.detailText = 'Lorem Ipsum';
        fixture.detectChanges();
        button = compiled.querySelector('.close fa-icon[icon="times"]');
        confirm = compiled.querySelector('.close jhi-confirm-icon');
        expect(button).toBeFalsy();
        expect(confirm).toBeTruthy();

        component.feedback.credits = 0;
        fixture.detectChanges();
        button = compiled.querySelector('.close fa-icon[icon="times"]');
        confirm = compiled.querySelector('.close jhi-confirm-icon');
        expect(button).toBeFalsy();
        expect(confirm).toBeTruthy();

        component.feedback.detailText = '';
        fixture.detectChanges();

        button = compiled.querySelector('.close fa-icon[icon="times"]');
        confirm = compiled.querySelector('.close jhi-confirm-icon');
        expect(button).toBeTruthy();
        expect(confirm).toBeFalsy();
    });

    it('should put the badge and the text correctly for feedback conflicts', () => {
        component.feedback.conflictingTextAssessments = [new FeedbackConflict()];
        fixture.detectChanges();
        const badge = compiled.querySelector('.bg-warning fa-icon[ng-reflect-icon="balance-scale-right"]');
        expect(badge).toBeTruthy();
        const text = compiled.querySelector('[jhiTranslate$=conflictingAssessments]');
        expect(text).toBeTruthy();
    });

    it('should focus to the text area if it is left conflicting feedback', () => {
        component.feedback.credits = 0;
        component.feedback.detailText = 'Lorem Ipsum';
        component.conflictMode = true;
        component.isConflictingFeedback = true;
        component.isLeftConflictingFeedback = true;
        fixture.detectChanges();

        spyOn(component['textareaElement'], 'focus');
        component.focus();

        expect(component['textareaElement'].focus).toHaveBeenCalled();
    });

    it('should not focus to the text area if it is right conflicting feedback', () => {
        component.feedback.credits = 0;
        component.feedback.detailText = 'Lorem Ipsum';
        component.conflictMode = true;
        component.isConflictingFeedback = true;
        component.isLeftConflictingFeedback = false;
        fixture.detectChanges();

        spyOn(component['textareaElement'], 'focus');
        component.focus();

        expect(component['textareaElement'].focus).toHaveBeenCalledTimes(0);
    });

    it('should call escKeyup when keyEvent', () => {
        component.feedback.credits = 0;
        component.feedback.detailText = '';
        spyOn(component, 'escKeyup');
        const event = new KeyboardEvent('keydown', {
            key: 'Esc',
        });
        const textarea = fixture.nativeElement.querySelector('textarea');
        textarea.dispatchEvent(event);
        fixture.detectChanges();
        expect(component.escKeyup).toHaveBeenCalled();
    });

    it('should show feedback impact warning when numberOfAffectedSubmissions > 0', () => {
        // additionally component needs to have some credits, have no conflicts and be a Manual type feedback
        component.feedback.credits = 1;
        component.conflictMode = false;
        textBlock.numberOfAffectedSubmissions = 5;
        component.feedback.type = FeedbackType.MANUAL;
        fixture.detectChanges();

        const warningIcon = compiled.querySelector('fa-icon[ng-reflect-icon="info-circle"]');
        expect(warningIcon).toBeTruthy();
        const text = compiled.querySelector('[jhiTranslate$=impactWarning]');
        expect(text).toBeTruthy();
    });

    it('should not show warning when numberOfAffectedSubmissions = 0', () => {
        textBlock.numberOfAffectedSubmissions = 0;
        fixture.detectChanges();

        const warningIcon = compiled.querySelector('fa-icon[ng-reflect-icon="exclamation-triangle"]');
        expect(warningIcon).toBeFalsy();

        const text = compiled.querySelector('[jhiTranslate$=feedbackImpactWarning]');
        expect(text).toBeFalsy();
    });

    it('should show view origin icon when there is an automatic feedback label', () => {
        component.feedback.type = FeedbackType.AUTOMATIC;
        fixture.detectChanges();

        const searchOriginIcon = compiled.querySelector('fa-icon[ng-reflect-icon="search"]');
        expect(searchOriginIcon).toBeTruthy();
    });

    it('should open modal when open origin of feedback function is called', () => {
        const modalService: NgbModal = TestBed.inject(NgbModal);
        const content = {};
        const modalServiceSpy = sinon.spy(modalService, 'open');

        component.openOriginOfFeedbackModal(content).then(() => {
            expect(modalServiceSpy).toHaveBeenCalledTimes(1);
        });
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
});
