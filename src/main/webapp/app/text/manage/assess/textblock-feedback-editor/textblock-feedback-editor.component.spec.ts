import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { TextBlockFeedbackEditorComponent } from 'app/text/manage/assess/textblock-feedback-editor/text-block-feedback-editor.component';
import { Feedback, FeedbackCorrectionErrorType, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { TextBlock, TextBlockType } from 'app/text/shared/entities/text-block.model';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NgbModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockDirective, MockProvider } from 'ng-mocks';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { ChangeDetectorRef } from '@angular/core';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TextAssessmentEventType } from 'app/text/shared/entities/text-assesment-event.model';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { provideHttpClient } from '@angular/common/http';
import { AssessmentCorrectionRoundBadgeComponent } from 'app/assessment/manage/unreferenced-feedback-detail/assessment-correction-round-badge/assessment-correction-round-badge.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { SessionStorageService } from 'app/shared/service/session-storage.service';

/**
 * Test suite for TextBlockFeedbackEditorComponent.
 * Tests component creation, feedback editing, delete button behavior,
 * keyboard events, grading instruction links, and assessment event tracking.
 */
describe('TextBlockFeedbackEditorComponent', () => {
    setupTestBed({ zoneless: true });
    let component: TextBlockFeedbackEditorComponent;
    let fixture: ComponentFixture<TextBlockFeedbackEditorComponent>;
    let compiled: any;

    const textBlock = { id: '1' } as TextBlock;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), MockDirective(NgbTooltip), FaIconComponent, TextBlockFeedbackEditorComponent, AssessmentCorrectionRoundBadgeComponent],
            providers: [
                MockProvider(ChangeDetectorRef),
                { provide: NgbModal, useClass: MockNgbModalService },
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                LocalStorageService,
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ id: 123 }) },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
            ],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(TextBlockFeedbackEditorComponent);
        component = fixture.componentInstance;
        const feedback = Feedback.forText(textBlock);
        feedback.gradingInstruction = new GradingInstruction();
        feedback.gradingInstruction.usageCount = 0;
        fixture.componentRef.setInput('textBlock', textBlock);
        fixture.componentRef.setInput('feedback', feedback);
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
        let button = compiled.querySelector('#dismiss-icon');
        let confirm = compiled.querySelector('#confirm-icon');
        expect(button).toBeTruthy();
        expect(confirm).toBeFalsy();

        component.feedback().credits = 1;
        fixture.changeDetectorRef.detectChanges();
        button = compiled.querySelector('#dismiss-icon');
        confirm = compiled.querySelector('#confirm-icon');
        expect(button).toBeFalsy();
        expect(confirm).toBeTruthy();

        component.feedback().detailText = 'Lorem Ipsum';
        fixture.changeDetectorRef.detectChanges();
        button = compiled.querySelector('#dismiss-icon');
        confirm = compiled.querySelector('#confirm-icon');
        expect(button).toBeFalsy();
        expect(confirm).toBeTruthy();

        component.feedback().credits = 0;
        fixture.changeDetectorRef.detectChanges();
        button = compiled.querySelector('#dismiss-icon');
        confirm = compiled.querySelector('#confirm-icon');
        expect(button).toBeFalsy();
        expect(confirm).toBeTruthy();

        component.feedback().detailText = '';
        fixture.changeDetectorRef.detectChanges();

        button = compiled.querySelector('#dismiss-icon');
        confirm = compiled.querySelector('#confirm-icon');
        expect(button).toBeTruthy();
        expect(confirm).toBeFalsy();
    });

    it('should call escKeyup when keyEvent', () => {
        component.feedback().credits = 0;
        component.feedback().detailText = '';
        vi.spyOn(component, 'escKeyup');
        const event = new KeyboardEvent('keydown', {
            key: 'Esc',
        });
        const textarea = fixture.nativeElement.querySelector('textarea');
        textarea.dispatchEvent(event);
        fixture.changeDetectorRef.detectChanges();
        expect(component.escKeyup).toHaveBeenCalledOnce();
    });

    it('should show confirmIcon if feedback dismission needs to be confirmed', () => {
        // Set feedback credits to non-zero which requires confirmation
        component.feedback().credits = 1;
        fixture.changeDetectorRef.detectChanges();

        // Verify confirm icon is displayed when feedback has credits
        const confirmIcon = compiled.querySelector('#confirm-icon');
        expect(confirmIcon).toBeTruthy();
    });

    it('should show link icon when feedback is associated with grading instruction', () => {
        component.feedback().gradingInstruction = new GradingInstruction();
        fixture.changeDetectorRef.detectChanges();
        const linkIcon = compiled.querySelector('.form-group jhi-grading-instruction-link-icon');
        expect(linkIcon).toBeTruthy();
    });

    it('should not show link icon when feedback is not associated with grading instruction', () => {
        component.feedback().gradingInstruction = undefined;
        fixture.changeDetectorRef.detectChanges();
        const linkIcon = compiled.querySelector('.form-group jhi-grading-instruction-link-icon');
        expect(linkIcon).toBeFalsy();
    });

    it('should send assessment event on dismiss button click', () => {
        component.feedback().type = FeedbackType.MANUAL;
        component.textBlock().type = TextBlockType.MANUAL;
        //@ts-ignore
        const sendAssessmentEvent = vi.spyOn<any, any>(component.textAssessmentAnalytics, 'sendAssessmentEvent');
        component.dismiss();
        fixture.changeDetectorRef.detectChanges();
        expect(sendAssessmentEvent).toHaveBeenCalledWith(TextAssessmentEventType.DELETE_FEEDBACK, FeedbackType.MANUAL, TextBlockType.MANUAL);
    });

    it('should set correctionStatus of the feedback to undefined on score click', () => {
        // given
        component.feedback().correctionStatus = FeedbackCorrectionErrorType.UNNECESSARY_FEEDBACK;

        // when
        component.onScoreClick(new MouseEvent(''));

        // then
        expect(component.feedback().correctionStatus).toBeUndefined();
    });

    it('should set correctionStatus of the feedback to undefined on connection of feedback with the grading instruction', () => {
        // given
        component.feedback().correctionStatus = FeedbackCorrectionErrorType.MISSING_GRADING_INSTRUCTION;
        //@ts-ignore
        vi.spyOn(component.structuredGradingCriterionService, 'updateFeedbackWithStructuredGradingInstructionEvent').mockImplementation();

        // when
        const mockEvent = { preventDefault: vi.fn(), dataTransfer: { getData: vi.fn().mockReturnValue('{}') } } as unknown as Event;
        component.connectFeedbackWithInstruction(mockEvent);

        // then
        expect(component.feedback().correctionStatus).toBeUndefined();
    });

    it('should send assessment event if feedback type changed', () => {
        component.feedback().text = 'FeedbackSuggestion:accepted:Test';
        //@ts-ignore
        const typeSpy = vi.spyOn(component.textAssessmentAnalytics, 'sendAssessmentEvent');
        component.didChange();
        expect(typeSpy).toHaveBeenCalledOnce();
    });
});
