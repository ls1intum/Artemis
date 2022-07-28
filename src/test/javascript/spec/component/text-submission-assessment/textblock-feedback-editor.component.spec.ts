import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { TextblockFeedbackEditorComponent } from 'app/exercises/text/assess/textblock-feedback-editor/textblock-feedback-editor.component';
import { Feedback, FeedbackCorrectionErrorType, FeedbackType } from 'app/entities/feedback.model';
import { TextBlock, TextBlockType } from 'app/entities/text-block.model';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NgbModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { FaIconComponent, FaLayersComponent } from '@fortawesome/angular-fontawesome';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { FeedbackConflict } from 'app/entities/feedback-conflict';
import { AssessmentCorrectionRoundBadgeComponent } from 'app/assessment/assessment-detail/assessment-correction-round-badge/assessment-correction-round-badge.component';
import { GradingInstructionLinkIconComponent } from 'app/shared/grading-instruction-link-icon/grading-instruction-link-icon.component';
import { ChangeDetectorRef } from '@angular/core';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockTranslateService, TranslateTestingModule } from '../../helpers/mocks/service/mock-translate.service';
import { TextAssessmentEventType } from 'app/entities/text-assesment-event.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgModel } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { getLatestSubmissionResult, SubmissionExerciseType } from 'app/entities/submission.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { Result } from 'app/entities/result.model';
import { TextblockFeedbackDropdownComponent } from 'app/exercises/text/assess/textblock-feedback-editor/dropdown/textblock-feedback-dropdown.component';

describe('TextblockFeedbackEditorComponent', () => {
    let component: TextblockFeedbackEditorComponent;
    let fixture: ComponentFixture<TextblockFeedbackEditorComponent>;
    let compiled: any;

    const textBlock = { id: '1' } as TextBlock;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateModule.forRoot(), TranslateTestingModule],
            declarations: [
                TextblockFeedbackEditorComponent,
                AssessmentCorrectionRoundBadgeComponent,
                MockComponent(TextblockFeedbackDropdownComponent),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(ConfirmIconComponent),
                MockComponent(FaIconComponent),
                MockComponent(FaLayersComponent),
                MockComponent(GradingInstructionLinkIconComponent),
                MockDirective(TranslateDirective),
                MockDirective(NgbTooltip),
                MockDirective(NgModel),
            ],
            providers: [
                MockProvider(ChangeDetectorRef),
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
            ],
        })
            .overrideModule(ArtemisTestModule, {
                remove: {
                    declarations: [MockComponent(FaIconComponent), MockComponent(FaLayersComponent)],
                    exports: [MockComponent(FaIconComponent), MockComponent(FaLayersComponent)],
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

    it('should put the badge and the text correctly for feedback conflicts', () => {
        component.feedback.conflictingTextAssessments = [new FeedbackConflict()];
        fixture.detectChanges();
        const badge = compiled.querySelector('.bg-warning fa-icon[ng-reflect-icon="[object Object]"]');
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

        jest.spyOn(component['textareaElement'], 'focus');
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

        jest.spyOn(component['textareaElement'], 'focus');
        component.focus();

        expect(component['textareaElement'].focus).toHaveBeenCalledTimes(0);
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
        expect(component.escKeyup).toHaveBeenCalled();
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

    it('should show feedback impact warning when numberOfAffectedSubmissions > 0', () => {
        // additionally component needs to have some credits, have no conflicts and be a Manual type feedback
        component.feedback.credits = 1;
        component.conflictMode = false;
        textBlock.numberOfAffectedSubmissions = 5;
        component.feedback.type = FeedbackType.MANUAL;
        fixture.detectChanges();

        const warningIcon = compiled.querySelector('fa-icon[ng-reflect-icon="[object Object]"]');
        expect(warningIcon).toBeTruthy();
        const text = compiled.querySelector('[jhiTranslate$=impactWarning]');
        expect(text).toBeTruthy();
    });

    it('should not show warning when numberOfAffectedSubmissions = 0', () => {
        textBlock.numberOfAffectedSubmissions = 0;
        fixture.detectChanges();

        const text = compiled.querySelector('[jhiTranslate$=feedbackImpactWarning]');
        expect(text).toBeFalsy();
    });

    it('should show view origin icon when there is an automatic feedback label', () => {
        component.feedback.type = FeedbackType.AUTOMATIC;
        fixture.detectChanges();

        const searchOriginIcon = compiled.querySelector('fa-icon[ng-reflect-icon="[object Object]"]');
        expect(searchOriginIcon).toBeTruthy();
    });

    it('should open modal when open origin of feedback function is called', async () => {
        const modalService: NgbModal = TestBed.inject(NgbModal);
        const content = {};
        const modalServiceSpy = jest.spyOn(modalService, 'open');

        await expect(component.openOriginOfFeedbackModal(content))
            .toResolve()
            .then(() => {
                expect(modalServiceSpy).toHaveBeenCalledOnce();
            });
    });

    it('should connect automatic feedback origin blocks with current feedback', fakeAsync(() => {
        component.feedback.suggestedFeedbackOriginSubmissionReference = 1;
        component.feedback.suggestedFeedbackParticipationReference = 1;
        const textAssessmentService = TestBed.inject(TextAssessmentService);

        const participation: StudentParticipation = {
            type: ParticipationType.STUDENT,
        } as unknown as StudentParticipation;

        const textSubmission = {
            submissionExerciseType: SubmissionExerciseType.TEXT,
            id: 1,
            submissionDate: dayjs('2019-07-09T10:47:33.244Z'),
            text: 'First text. Second text.',
            participation,
        } as unknown as TextSubmission;

        textSubmission.results = [
            {
                id: 2374,
                completionDate: dayjs('2019-07-09T11:51:23.251Z'),
                textSubmission,
            } as unknown as Result,
        ];
        textSubmission.latestResult = getLatestSubmissionResult(textSubmission);
        textSubmission.latestResult!.feedbacks = [
            {
                id: 1,
                detailText: 'text',
                reference: 'First text id',
                credits: 1.5,
            } as Feedback,
        ];
        textSubmission.blocks = [
            {
                id: 'First text id',
                text: 'First text.',
                textSubmission,
                numberOfAffectedSubmissions: 3,
            } as unknown as TextBlock,
        ];
        participation.submissions = [textSubmission];

        const participationStub = jest.spyOn(textAssessmentService, 'getFeedbackDataForExerciseSubmission').mockReturnValue(of(participation));

        component.connectAutomaticFeedbackOriginBlocksWithFeedback();
        tick();

        expect(participationStub).toHaveBeenCalledOnce();
        expect(component.listOfBlocksWithFeedback).toEqual([
            {
                text: 'First text.',
                feedback: 'text',
                credits: 1.5,
                reusedCount: 3,
                type: 'MANUAL',
            },
        ]);
    }));

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

    it('should send assessment event on conflict button click', () => {
        component.feedback.type = FeedbackType.AUTOMATIC;
        component.textBlock.type = TextBlockType.AUTOMATIC;
        const sendAssessmentEvent = jest.spyOn<any, any>(component.textAssessmentAnalytics, 'sendAssessmentEvent');
        component.onConflictClicked(1);
        fixture.detectChanges();
        expect(sendAssessmentEvent).toHaveBeenCalledWith(TextAssessmentEventType.CLICK_TO_RESOLVE_CONFLICT, FeedbackType.AUTOMATIC, TextBlockType.AUTOMATIC);
    });

    it('should send assessment event on dismiss button click', () => {
        component.feedback.type = FeedbackType.MANUAL;
        component.textBlock.type = TextBlockType.MANUAL;
        const sendAssessmentEvent = jest.spyOn<any, any>(component.textAssessmentAnalytics, 'sendAssessmentEvent');
        component.dismiss();
        fixture.detectChanges();
        expect(sendAssessmentEvent).toHaveBeenCalledWith(TextAssessmentEventType.DELETE_FEEDBACK, FeedbackType.MANUAL, TextBlockType.MANUAL);
    });

    it('should send assessment event on hovering over warning', () => {
        component.feedback.type = FeedbackType.MANUAL;
        component.textBlock.type = TextBlockType.AUTOMATIC;
        const sendAssessmentEvent = jest.spyOn<any, any>(component.textAssessmentAnalytics, 'sendAssessmentEvent');
        component.mouseEnteredWarningLabel();
        fixture.detectChanges();
        expect(sendAssessmentEvent).toHaveBeenCalledWith(TextAssessmentEventType.HOVER_OVER_IMPACT_WARNING, FeedbackType.MANUAL, TextBlockType.AUTOMATIC);
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
        component.feedback.type = FeedbackType.AUTOMATIC;
        const typeSpy = jest.spyOn(component.textAssessmentAnalytics, 'sendAssessmentEvent');
        component.didChange();
        expect(typeSpy).toHaveBeenCalledOnce();
    });
});
