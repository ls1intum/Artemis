import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { AlertService } from 'app/core/util/alert.service';
import dayjs from 'dayjs/esm';
import { AssessmentHeaderComponent } from 'app/assessment/assessment-header/assessment-header.component';
import { ArtemisTestModule } from '../../test.module';
import { Result } from 'app/entities/result.model';
import { AlertOverlayComponent } from 'app/shared/alert/alert-overlay.component';
import { RouterTestingModule } from '@angular/router/testing';
import { AssessmentWarningComponent } from 'app/assessment/assessment-warning/assessment-warning.component';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateDirective, TranslateService } from '@ngx-translate/core';
import { TextAssessmentEventType } from 'app/entities/text-assesment-event.model';
import { NgbAlert, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradingScale } from 'app/entities/grading-scale.model';
import { HttpResponse } from '@angular/common/http';
import { GradeStep } from 'app/entities/grade-step.model';
import { of } from 'rxjs';
import { MockTranslateValuesDirective } from '../../helpers/mocks/directive/mock-translate-values.directive';

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
            imports: [ArtemisTestModule, RouterTestingModule],
            declarations: [
                AssessmentHeaderComponent,
                AssessmentWarningComponent,
                AlertOverlayComponent,
                MockComponent(NgbAlert),
                MockDirective(NgbTooltip),
                TranslateDirective,
                ArtemisTranslatePipe,
                MockTranslateValuesDirective,
                MockComponent(FaIconComponent),
            ],
            providers: [
                {
                    provide: AlertService,
                    useClass: AlertService, // use the real one
                },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
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
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(AssessmentHeaderComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should display warning when assessment due date has not passed', () => {
        component.exercise = {
            id: 16,
            dueDate: dayjs().subtract(2, 'days'),
        } as Exercise;
        // @ts-ignore
        component.result = undefined;
        fixture.detectChanges();
        const warningComponent = fixture.debugElement.query(By.directive(AssessmentWarningComponent));
        expect(warningComponent).toBeTruthy();
    });

    it('should display alert when assessment due date has passed', () => {
        component.hasAssessmentDueDatePassed = true;
        // @ts-ignore
        component.result = undefined;
        fixture.detectChanges();

        const alertComponent = fixture.debugElement.query(By.css('ngb-alert'));
        expect(alertComponent).not.toBe(undefined);
        expect(alertComponent.nativeElement.innerHTML).toContain('Assessment Due Date is over, the assessment will be published immediately after submitting');
    });

    it('should hide right side row-container if loading', () => {
        component.isLoading = false;
        fixture.detectChanges();
        let container = fixture.debugElement.query(By.css('.row-container:nth-of-type(2)'));
        expect(container).toBeTruthy();

        component.isLoading = true;
        fixture.detectChanges();
        container = fixture.debugElement.query(By.css('.row-container:nth-of-type(2)'));
        expect(container).toBeFalsy();
    });

    it('should show if submission is locked by other user', () => {
        component.isLoading = false;
        component.isAssessor = true;
        fixture.detectChanges();

        let assessmentLocked = fixture.debugElement.query(By.css('[jhiTranslate$=assessmentLocked]'));
        expect(assessmentLocked).toBeFalsy();

        let assessmentLockedCurrentUser = fixture.debugElement.query(By.css('[jhiTranslate$=assessmentLockedCurrentUser]'));
        expect(assessmentLockedCurrentUser).toBeTruthy();

        component.isAssessor = false;
        fixture.detectChanges();

        assessmentLocked = fixture.debugElement.query(By.css('[jhiTranslate$=assessmentLocked]'));
        expect(assessmentLocked).toBeTruthy();

        assessmentLockedCurrentUser = fixture.debugElement.query(By.css('[jhiTranslate$=assessmentLockedCurrentUser]'));
        expect(assessmentLockedCurrentUser).toBeFalsy();
    });

    it('should show save/submit buttons when no result present', () => {
        component.isLoading = false;
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

        jest.spyOn(component.submit, 'emit');
        submitButtonSpan.nativeElement.click();
        expect(component.submit.emit).toHaveBeenCalledOnce();

        const cancelButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=cancel]'));
        jest.spyOn(component.cancel, 'emit');
        cancelButtonSpan.nativeElement.click();
        expect(component.cancel.emit).toHaveBeenCalledOnce();
    });

    it('should show override button when result is present', () => {
        component.isLoading = false;
        component.result = new Result();
        component.result.completionDate = dayjs();
        fixture.detectChanges();

        const saveButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=save]'));
        expect(saveButtonSpan).toBeFalsy();
        const submitButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=submit]'));
        expect(submitButtonSpan).toBeFalsy();

        let overrideAssessmentButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=overrideAssessment]'));
        expect(overrideAssessmentButtonSpan).toBeFalsy();

        component.canOverride = true;
        fixture.detectChanges();

        overrideAssessmentButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=overrideAssessment]'));
        expect(overrideAssessmentButtonSpan).toBeTruthy();

        jest.spyOn(component.submit, 'emit');
        overrideAssessmentButtonSpan.nativeElement.click();
        expect(component.submit.emit).toHaveBeenCalledOnce();
    });

    it('should show next submission if assessor or instructor, result is present and no complaint', () => {
        jest.spyOn(component.nextSubmission, 'emit');
        component.isLoading = false;
        component.isAssessor = false;
        component.hasComplaint = false;
        component.exercise = {
            id: 1,
        } as Exercise;
        fixture.detectChanges();

        let nextSubmissionButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=nextSubmission]'));
        expect(nextSubmissionButtonSpan).toBeFalsy();

        component.result = new Result();
        component.result.completionDate = dayjs();
        fixture.detectChanges();
        nextSubmissionButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=nextSubmission]'));
        expect(nextSubmissionButtonSpan).toBeFalsy();

        component.exercise.isAtLeastInstructor = true;
        fixture.detectChanges();
        nextSubmissionButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=nextSubmission]'));
        expect(nextSubmissionButtonSpan).toBeTruthy();

        component.isAssessor = true;
        fixture.detectChanges();
        nextSubmissionButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=nextSubmission]'));
        expect(nextSubmissionButtonSpan).toBeTruthy();

        component.exercise.isAtLeastInstructor = false;
        fixture.detectChanges();
        nextSubmissionButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=nextSubmission]'));
        expect(nextSubmissionButtonSpan).toBeTruthy();

        component.hasComplaint = true;
        fixture.detectChanges();
        nextSubmissionButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=nextSubmission]'));
        expect(nextSubmissionButtonSpan).toBeFalsy();

        component.hasComplaint = false;
        component.nextSubmissionBusy = true;
        fixture.detectChanges();
        nextSubmissionButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=nextSubmission]'));
        expect(nextSubmissionButtonSpan).toBeTruthy();
        const nextSubmissionButton = nextSubmissionButtonSpan.parent;
        expect(nextSubmissionButton).toBeTruthy();
        expect(nextSubmissionButton!.nativeElement.disabled).toBeTruthy();

        component.nextSubmissionBusy = false;
        fixture.detectChanges();
        nextSubmissionButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=nextSubmission]'));
        nextSubmissionButtonSpan.nativeElement.click();
        expect(component.nextSubmission.emit).toHaveBeenCalledOnce();
    });
    it('should not show assess next button if is test run mode', () => {
        component.isTestRun = true;
        component.isLoading = false;
        fixture.detectChanges();
        const nextSubmissionButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=nextSubmission]'));
        expect(nextSubmissionButtonSpan).toBeFalsy();
    });

    it('should set highlightDifferences to true', () => {
        component.highlightDifferences = false;
        jest.spyOn(component.highlightDifferencesChange, 'emit');

        component.toggleHighlightDifferences();

        expect(component.highlightDifferencesChange.emit).toHaveBeenCalled();
        expect(component.highlightDifferences).toBeTrue();
    });

    it('should set highlightDifferences to false', () => {
        component.highlightDifferences = true;
        jest.spyOn(component.highlightDifferencesChange, 'emit');

        component.toggleHighlightDifferences();

        expect(component.highlightDifferencesChange.emit).toHaveBeenCalled();
        expect(component.highlightDifferences).toBeFalse();
    });

    it('should send assessment event on assess next button click when exercise set to Text', () => {
        component.exercise = {
            type: ExerciseType.TEXT,
        } as Exercise;
        const sendAssessmentEvent = jest.spyOn<any, any>(component.textAssessmentAnalytics, 'sendAssessmentEvent');
        component.sendAssessNextEventToAnalytics();
        fixture.detectChanges();
        expect(sendAssessmentEvent).toHaveBeenCalledWith(TextAssessmentEventType.ASSESS_NEXT_SUBMISSION);
    });

    it('should not send assessment event on assess next button click when exercise is not Text', () => {
        component.exercise = {
            type: ExerciseType.FILE_UPLOAD,
        } as Exercise;
        const sendAssessmentEvent = jest.spyOn<any, any>(component.textAssessmentAnalytics, 'sendAssessmentEvent');
        component.sendAssessNextEventToAnalytics();
        fixture.detectChanges();
        expect(sendAssessmentEvent).toHaveBeenCalledTimes(0);
    });

    it('should send assessment event on submit button click when exercise set to Text', () => {
        component.exercise = {
            type: ExerciseType.TEXT,
        } as Exercise;
        const sendAssessmentEvent = jest.spyOn<any, any>(component.textAssessmentAnalytics, 'sendAssessmentEvent');
        component.sendSubmitAssessmentEventToAnalytics();
        fixture.detectChanges();
        expect(sendAssessmentEvent).toHaveBeenCalledWith(TextAssessmentEventType.SUBMIT_ASSESSMENT);
    });

    it('should not send assessment event on submit button click when exercise is not Text', () => {
        component.exercise = {
            type: ExerciseType.FILE_UPLOAD,
        } as Exercise;
        const sendAssessmentEvent = jest.spyOn<any, any>(component.textAssessmentAnalytics, 'sendAssessmentEvent');
        component.sendSubmitAssessmentEventToAnalytics();
        fixture.detectChanges();
        expect(sendAssessmentEvent).toHaveBeenCalledTimes(0);
    });

    it('should save assessment on control and s', () => {
        component.isLoading = false;
        component.assessmentsAreValid = true;
        component.isAssessor = true;
        component.saveBusy = false;
        component.submitBusy = false;
        component.cancelBusy = false;
        fixture.detectChanges();

        const eventMock = new KeyboardEvent('keydown', { ctrlKey: true, key: 's' });
        const spyOnControlAndS = jest.spyOn(component, 'saveOnControlAndS');
        const saveSpy = jest.spyOn(component.save, 'emit');
        document.dispatchEvent(eventMock);

        expect(spyOnControlAndS).toHaveBeenCalledOnce();
        expect(saveSpy).toHaveBeenCalledOnce();
    });

    it('should submit assessment on control and enter', () => {
        component.isLoading = false;
        component.assessmentsAreValid = true;
        component.isAssessor = true;
        component.saveBusy = false;
        component.submitBusy = false;
        component.cancelBusy = false;
        fixture.detectChanges();

        const eventMock = new KeyboardEvent('keydown', { ctrlKey: true, key: 'Enter' });
        const spyOnControlAndEnter = jest.spyOn(component, 'submitOnControlAndEnter');
        const submitSpy = jest.spyOn(component.submit, 'emit');
        document.dispatchEvent(eventMock);

        expect(spyOnControlAndEnter).toHaveBeenCalledOnce();
        expect(submitSpy).toHaveBeenCalledOnce();
    });

    it('should override assessment on control and enter', () => {
        component.result = new Result();
        component.result.completionDate = dayjs();
        component.canOverride = true;
        component.assessmentsAreValid = true;
        component.submitBusy = false;
        fixture.detectChanges();

        const eventMock = new KeyboardEvent('keydown', { ctrlKey: true, key: 'Enter' });
        const spyOnControlAndEnter = jest.spyOn(component, 'submitOnControlAndEnter');
        const submitSpy = jest.spyOn(component.submit, 'emit');
        document.dispatchEvent(eventMock);

        expect(spyOnControlAndEnter).toHaveBeenCalledOnce();
        expect(submitSpy).toHaveBeenCalledOnce();
    });

    it('should assess next submission on control, shift and arrow right', () => {
        component.isAssessor = true;
        component.result = new Result();
        component.result.completionDate = dayjs();
        component.isTeamMode = false;
        component.isTestRun = false;
        fixture.detectChanges();

        const eventMock = new KeyboardEvent('keydown', { ctrlKey: true, shiftKey: true, key: 'ArrowRight' });
        const spyOnControlShiftAndArrowRight = jest.spyOn(component, 'assessNextOnControlShiftAndArrowRight');
        const nextSpy = jest.spyOn(component.nextSubmission, 'emit');
        document.dispatchEvent(eventMock);

        expect(spyOnControlShiftAndArrowRight).toHaveBeenCalledOnce();
        expect(nextSpy).toHaveBeenCalledOnce();
    });
});
