import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { AlertService } from 'app/shared/service/alert.service';
import dayjs from 'dayjs/esm';
import { AssessmentHeaderComponent } from 'app/assessment/manage/assessment-header/assessment-header.component';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { AssessmentWarningComponent } from 'app/assessment/manage/assessment-warning/assessment-warning.component';
import { MockProvider } from 'ng-mocks';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { TextAssessmentEventType } from 'app/text/shared/entities/text-assesment-event.model';
import { GradingSystemService } from 'app/assessment/manage/grading-system/grading-system.service';
import { GradingScale } from 'app/assessment/shared/entities/grading-scale.model';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { GradeStep } from 'app/assessment/shared/entities/grade-step.model';
import { of } from 'rxjs';
import { AssessmentNote } from 'app/assessment/shared/entities/assessment-note.model';
import '@angular/localize/init';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

describe('AssessmentHeaderComponent', () => {
    let component: AssessmentHeaderComponent;
    let fixture: ComponentFixture<AssessmentHeaderComponent>;

    const gradeStep1: GradeStep = {
        isPassingGrade: false,
        lowerBoundInclusive: true,
        lowerBoundPercentage: 0,
        upperBoundInclusive: false,
        upperBoundPercentage: 40,
        gradeName: 'D',
    };
    const gradeStep2: GradeStep = {
        isPassingGrade: true,
        lowerBoundInclusive: true,
        lowerBoundPercentage: 40,
        upperBoundInclusive: false,
        upperBoundPercentage: 60,
        gradeName: 'C',
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                {
                    provide: AlertService,
                    useClass: AlertService, // use the real one
                },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(GradingSystemService, {
                    findGradingScaleForExam: () => {
                        return of(
                            new HttpResponse({
                                body: new GradingScale(),
                                status: 200,
                            }),
                        );
                    },
                    findMatchingGradeStep: () => {
                        return gradeStep1;
                    },
                    sortGradeSteps: () => {
                        return [gradeStep1, gradeStep2];
                    },
                }),
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                provideHttpClient(),
            ],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(AssessmentHeaderComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('isLoading', false);
        fixture.componentRef.setInput('saveBusy', false);
        fixture.componentRef.setInput('submitBusy', false);
        fixture.componentRef.setInput('cancelBusy', false);
        fixture.componentRef.setInput('nextSubmissionBusy', false);
        fixture.componentRef.setInput('isTeamMode', false);
        fixture.componentRef.setInput('isAssessor', true);
        fixture.componentRef.setInput('exerciseDashboardLink', []);
        fixture.componentRef.setInput('canOverride', false);
        fixture.componentRef.setInput('assessmentsAreValid', false);
        fixture.componentRef.setInput('hasAssessmentDueDatePassed', true);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should display warning when assessment due date has not passed', () => {
        fixture.componentRef.setInput('exercise', {
            id: 16,
            dueDate: dayjs().subtract(2, 'days'),
        } as Exercise);
        fixture.componentRef.setInput('result', undefined);
        fixture.detectChanges();
        const warningComponent = fixture.debugElement.query(By.directive(AssessmentWarningComponent));
        expect(warningComponent).toBeTruthy();
    });

    it('should display alert when assessment due date has passed', () => {
        fixture.componentRef.setInput('hasAssessmentDueDatePassed', true);
        fixture.componentRef.setInput('result', undefined);
        fixture.detectChanges();

        const alertComponent = fixture.debugElement.query(By.css('ngb-alert'));
        expect(alertComponent).toBeDefined();
        expect(alertComponent.nativeElement.getAttribute('jhiTranslate')).toBe('artemisApp.assessment.assessmentDueDateIsOver');
    });

    it('should hide right side row-container if loading', () => {
        fixture.componentRef.setInput('isLoading', false);
        fixture.detectChanges();
        let container = fixture.debugElement.query(By.css('.row-container:nth-of-type(2)'));
        expect(container).toBeTruthy();

        fixture.componentRef.setInput('isLoading', true);
        fixture.detectChanges();
        container = fixture.debugElement.query(By.css('.row-container:nth-of-type(2)'));
        expect(container).toBeFalsy();
    });

    it('should show if submission is locked by other user', () => {
        fixture.componentRef.setInput('isLoading', false);
        fixture.componentRef.setInput('isAssessor', true);
        fixture.detectChanges();

        let assessmentLocked = fixture.debugElement.query(By.css('[jhiTranslate$=assessmentLocked]'));
        expect(assessmentLocked).toBeFalsy();

        let assessmentLockedCurrentUser = fixture.debugElement.query(By.css('[jhiTranslate$=assessmentLockedCurrentUser]'));
        expect(assessmentLockedCurrentUser).toBeTruthy();

        fixture.componentRef.setInput('isAssessor', false);
        fixture.detectChanges();

        assessmentLocked = fixture.debugElement.query(By.css('[jhiTranslate$=assessmentLocked]'));
        expect(assessmentLocked).toBeTruthy();

        assessmentLockedCurrentUser = fixture.debugElement.query(By.css('[jhiTranslate$=assessmentLockedCurrentUser]'));
        expect(assessmentLockedCurrentUser).toBeFalsy();
    });

    it('should show save/submit buttons when no result present', () => {
        fixture.componentRef.setInput('isLoading', false);
        fixture.detectChanges();

        const saveButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=save]'));
        expect(saveButtonSpan).toBeTruthy();
        const submitButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=submit]'));
        expect(submitButtonSpan).toBeTruthy();

        const overrideAssessmentButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=overrideAssessment]'));
        expect(overrideAssessmentButtonSpan).toBeFalsy();

        jest.spyOn(component.save, 'emit');
        saveButtonSpan.nativeElement.click();
        expect(component.save.emit).toHaveBeenCalledOnce();

        jest.spyOn(component.onSubmit, 'emit');
        submitButtonSpan.nativeElement.click();
        expect(component.onSubmit.emit).toHaveBeenCalledOnce();

        const cancelButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=cancel]'));
        jest.spyOn(component.onCancel, 'emit');
        cancelButtonSpan.nativeElement.click();
        expect(component.onCancel.emit).toHaveBeenCalledOnce();
    });

    it('should show override button when result is present', () => {
        fixture.componentRef.setInput('isLoading', false);
        const result = new Result();
        result.completionDate = dayjs();
        fixture.componentRef.setInput('result', result);
        fixture.detectChanges();

        const saveButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=save]'));
        expect(saveButtonSpan).toBeFalsy();
        const submitButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=submit]'));
        expect(submitButtonSpan).toBeFalsy();

        let overrideAssessmentButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=overrideAssessment]'));
        expect(overrideAssessmentButtonSpan).toBeFalsy();

        fixture.componentRef.setInput('canOverride', true);
        fixture.detectChanges();

        overrideAssessmentButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=overrideAssessment]'));
        expect(overrideAssessmentButtonSpan).toBeTruthy();

        jest.spyOn(component.onSubmit, 'emit');
        overrideAssessmentButtonSpan.nativeElement.click();
        expect(component.onSubmit.emit).toHaveBeenCalledOnce();
    });

    it('should show next submission if assessor or instructor, result is present and no complaint', () => {
        jest.spyOn(component.nextSubmission, 'emit');
        fixture.componentRef.setInput('isLoading', false);
        fixture.componentRef.setInput('isAssessor', false);
        fixture.componentRef.setInput('hasComplaint', false);
        fixture.componentRef.setInput('exercise', {
            id: 1,
        } as Exercise);
        fixture.detectChanges();

        let nextSubmissionButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=nextSubmission]'));
        expect(nextSubmissionButtonSpan).toBeFalsy();

        const result = new Result();
        result.completionDate = dayjs();
        fixture.componentRef.setInput('result', result);
        fixture.detectChanges();
        nextSubmissionButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=nextSubmission]'));
        expect(nextSubmissionButtonSpan).toBeFalsy();

        fixture.componentRef.setInput('exercise', {
            id: 1,
            isAtLeastInstructor: true,
        } as Exercise);
        fixture.detectChanges();
        nextSubmissionButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=nextSubmission]'));
        expect(nextSubmissionButtonSpan).toBeTruthy();

        fixture.componentRef.setInput('isAssessor', true);
        fixture.detectChanges();
        nextSubmissionButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=nextSubmission]'));
        expect(nextSubmissionButtonSpan).toBeTruthy();

        fixture.componentRef.setInput('exercise', {
            id: 1,
            isAtLeastInstructor: false,
        } as Exercise);
        fixture.detectChanges();
        nextSubmissionButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=nextSubmission]'));
        expect(nextSubmissionButtonSpan).toBeTruthy();

        fixture.componentRef.setInput('hasComplaint', true);
        fixture.detectChanges();
        nextSubmissionButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=nextSubmission]'));
        expect(nextSubmissionButtonSpan).toBeFalsy();

        fixture.componentRef.setInput('hasComplaint', false);
        fixture.componentRef.setInput('nextSubmissionBusy', true);
        fixture.detectChanges();
        nextSubmissionButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=nextSubmission]'));
        expect(nextSubmissionButtonSpan).toBeTruthy();
        const nextSubmissionButton = nextSubmissionButtonSpan.parent;
        expect(nextSubmissionButton).toBeTruthy();
        expect(nextSubmissionButton!.nativeElement.disabled).toBeTruthy();

        fixture.componentRef.setInput('nextSubmissionBusy', false);
        fixture.detectChanges();
        nextSubmissionButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=nextSubmission]'));
        nextSubmissionButtonSpan.nativeElement.click();
        expect(component.nextSubmission.emit).toHaveBeenCalledOnce();
    });
    it('should not show assess next button if is test run mode', () => {
        fixture.componentRef.setInput('isTestRun', true);
        fixture.componentRef.setInput('isLoading', false);
        fixture.detectChanges();
        const nextSubmissionButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=nextSubmission]'));
        expect(nextSubmissionButtonSpan).toBeFalsy();
    });

    it('should set highlightDifferences to true', () => {
        component.highlightDifferences = false;
        jest.spyOn(component.highlightDifferencesChange, 'emit');

        component.toggleHighlightDifferences();

        expect(component.highlightDifferencesChange.emit).toHaveBeenCalledTimes(2);
        expect(component.highlightDifferences).toBeTrue();
    });

    it('should set highlightDifferences to false', () => {
        component.highlightDifferences = true;
        jest.spyOn(component.highlightDifferencesChange, 'emit');

        component.toggleHighlightDifferences();

        expect(component.highlightDifferencesChange.emit).toHaveBeenCalledTimes(2);
        expect(component.highlightDifferences).toBeFalse();
    });

    it('should send assessment event on assess next button click when exercise set to Text', () => {
        fixture.componentRef.setInput('exercise', {
            type: ExerciseType.TEXT,
        } as Exercise);
        const sendAssessmentEvent = jest.spyOn<any, any>(component.textAssessmentAnalytics, 'sendAssessmentEvent');
        component.sendAssessNextEventToAnalytics();
        fixture.detectChanges();
        expect(sendAssessmentEvent).toHaveBeenCalledWith(TextAssessmentEventType.ASSESS_NEXT_SUBMISSION);
    });

    it('should not send assessment event on assess next button click when exercise is not Text', () => {
        fixture.componentRef.setInput('exercise', {
            type: ExerciseType.FILE_UPLOAD,
        } as Exercise);
        const sendAssessmentEvent = jest.spyOn<any, any>(component.textAssessmentAnalytics, 'sendAssessmentEvent');
        component.sendAssessNextEventToAnalytics();
        fixture.detectChanges();
        expect(sendAssessmentEvent).not.toHaveBeenCalled();
    });

    it('should send assessment event on submit button click when exercise set to Text', () => {
        fixture.componentRef.setInput('exercise', {
            type: ExerciseType.TEXT,
        } as Exercise);
        const sendAssessmentEvent = jest.spyOn<any, any>(component.textAssessmentAnalytics, 'sendAssessmentEvent');
        component.sendSubmitAssessmentEventToAnalytics();
        fixture.detectChanges();
        expect(sendAssessmentEvent).toHaveBeenCalledWith(TextAssessmentEventType.SUBMIT_ASSESSMENT);
    });

    it('should not send assessment event on submit button click when exercise is not Text', () => {
        fixture.componentRef.setInput('exercise', {
            type: ExerciseType.FILE_UPLOAD,
        } as Exercise);
        const sendAssessmentEvent = jest.spyOn<any, any>(component.textAssessmentAnalytics, 'sendAssessmentEvent');
        component.sendSubmitAssessmentEventToAnalytics();
        fixture.detectChanges();
        expect(sendAssessmentEvent).not.toHaveBeenCalled();
    });

    it('should save assessment on control and s', () => {
        fixture.componentRef.setInput('isLoading', false);
        fixture.componentRef.setInput('assessmentsAreValid', true);
        fixture.componentRef.setInput('isAssessor', true);
        fixture.componentRef.setInput('saveBusy', false);
        fixture.componentRef.setInput('submitBusy', false);
        fixture.componentRef.setInput('cancelBusy', false);
        fixture.detectChanges();

        const eventMock = new KeyboardEvent('keydown', { ctrlKey: true, key: 's' });
        const spyOnControlAndS = jest.spyOn(component, 'saveOnControlAndS');
        const saveSpy = jest.spyOn(component.save, 'emit');
        document.dispatchEvent(eventMock);

        expect(spyOnControlAndS).toHaveBeenCalledOnce();
        expect(saveSpy).toHaveBeenCalledOnce();
    });

    it('should submit assessment on control and enter', () => {
        fixture.componentRef.setInput('isLoading', false);
        fixture.componentRef.setInput('assessmentsAreValid', true);
        fixture.componentRef.setInput('isAssessor', true);
        fixture.componentRef.setInput('saveBusy', false);
        fixture.componentRef.setInput('submitBusy', false);
        fixture.componentRef.setInput('cancelBusy', false);
        fixture.detectChanges();

        const eventMock = new KeyboardEvent('keydown', { ctrlKey: true, key: 'Enter' });
        const spyOnControlAndEnter = jest.spyOn(component, 'submitOnControlAndEnter');
        const submitSpy = jest.spyOn(component.onSubmit, 'emit');
        document.dispatchEvent(eventMock);

        expect(spyOnControlAndEnter).toHaveBeenCalledOnce();
        expect(submitSpy).toHaveBeenCalledOnce();
    });

    it('should override assessment on control and enter', () => {
        const result = new Result();
        result.completionDate = dayjs();
        fixture.componentRef.setInput('result', result);
        fixture.componentRef.setInput('canOverride', true);
        fixture.componentRef.setInput('assessmentsAreValid', true);
        fixture.componentRef.setInput('submitBusy', false);
        fixture.detectChanges();

        const eventMock = new KeyboardEvent('keydown', { ctrlKey: true, key: 'Enter' });
        const spyOnControlAndEnter = jest.spyOn(component, 'submitOnControlAndEnter');
        const submitSpy = jest.spyOn(component.onSubmit, 'emit');
        document.dispatchEvent(eventMock);

        expect(spyOnControlAndEnter).toHaveBeenCalledOnce();
        expect(submitSpy).toHaveBeenCalledOnce();
    });

    it('should assess next submission on control, shift and arrow right', () => {
        fixture.componentRef.setInput('isAssessor', true);
        const result = new Result();
        result.completionDate = dayjs();
        fixture.componentRef.setInput('result', result);
        fixture.componentRef.setInput('isTeamMode', false);
        fixture.componentRef.setInput('isTestRun', false);
        fixture.detectChanges();

        const eventMock = new KeyboardEvent('keydown', { ctrlKey: true, shiftKey: true, key: 'ArrowRight' });
        const spyOnControlShiftAndArrowRight = jest.spyOn(component, 'assessNextOnControlShiftAndArrowRight');
        const nextSpy = jest.spyOn(component.nextSubmission, 'emit');
        document.dispatchEvent(eventMock);

        expect(spyOnControlShiftAndArrowRight).toHaveBeenCalledOnce();
        expect(nextSpy).toHaveBeenCalledOnce();
    });

    it('should enable saving after entering an internal assessment note', () => {
        expect(component.saveDisabled).toBeTrue();
        fixture.componentRef.setInput('isAssessor', true);
        const result = new Result();
        result.assessmentNote = new AssessmentNote();
        fixture.componentRef.setInput('result', result);
        expect(component.saveDisabled).toBeTrue();
        result.assessmentNote.note = 'some input';
        fixture.componentRef.setInput('result', result);
        // ToDo: refactor the assessment header component in such a way that the booleans are never undefined
        expect(component.saveDisabled).toBeOneOf([false, undefined]);
    });

    it('should not enable submitting after entering an internal assessment note', () => {
        expect(component.submitDisabled).toBeTrue();
        fixture.componentRef.setInput('isAssessor', true);
        const result = new Result();
        result.assessmentNote = new AssessmentNote();
        result.assessmentNote.note = 'some input';
        fixture.componentRef.setInput('result', result);
        expect(component.submitDisabled).toBeTrue();
    });
});
