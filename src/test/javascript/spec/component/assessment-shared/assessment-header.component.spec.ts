import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { JhiAlertService } from 'ng-jhipster';
import * as moment from 'moment';
import { AssessmentHeaderComponent } from 'app/assessment/assessment-header/assessment-header.component';
import { ArtemisTestModule } from '../../test.module';
import { Result } from 'app/entities/result.model';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { RouterTestingModule } from '@angular/router/testing';
import { AssessmentWarningComponent } from 'app/assessment/assessment-warning/assessment-warning.component';
import { MockComponent } from 'ng-mocks';
import { Exercise } from 'app/entities/exercise.model';

describe('AssessmentHeaderComponent', () => {
    let component: AssessmentHeaderComponent;
    let fixture: ComponentFixture<AssessmentHeaderComponent>;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule, RouterTestingModule],
            declarations: [AssessmentHeaderComponent, MockComponent(AssessmentWarningComponent)],
            providers: [
                {
                    provide: JhiAlertService,
                    useClass: JhiAlertService, // use the real one
                },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(AssessmentHeaderComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should include jhi-alert', () => {
        const jhiAlertComponent = fixture.debugElement.query(By.directive(AlertComponent));
        expect(jhiAlertComponent).toBeTruthy();
    });

    it('should display alerts', () => {
        const alertService = TestBed.inject(JhiAlertService);
        alertService.success('test-alert-string');
        fixture.detectChanges();

        const jhiAlertComponent = fixture.debugElement.query(By.directive(AlertComponent));
        const jhiAlertContent = jhiAlertComponent.nativeElement.textContent;
        expect(jhiAlertContent).toContain('test-alert-string');
    });

    it('should display warning when assessment due date has not passed', () => {
        component.exercise = {
            id: 16,
            dueDate: moment().subtract(2, 'days'),
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
        expect(alertComponent).toBeTruthy();
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

        spyOn(component.save, 'emit');
        saveButtonSpan.nativeElement.click();
        expect(component.save.emit).toHaveBeenCalledTimes(1);

        spyOn(component.submit, 'emit');
        submitButtonSpan.nativeElement.click();
        expect(component.submit.emit).toHaveBeenCalledTimes(1);

        const cancelButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=cancel]'));
        spyOn(component.cancel, 'emit');
        cancelButtonSpan.nativeElement.click();
        expect(component.cancel.emit).toHaveBeenCalledTimes(1);
    });

    it('should show override button when result is present', () => {
        component.isLoading = false;
        component.result = new Result();
        component.result.completionDate = moment();
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

        spyOn(component.submit, 'emit');
        overrideAssessmentButtonSpan.nativeElement.click();
        expect(component.submit.emit).toHaveBeenCalledTimes(1);
    });

    it('should show next submission if assessor or instructur, result is present and no complaint', () => {
        spyOn(component.nextSubmission, 'emit');
        component.isLoading = false;
        component.isAssessor = false;
        component.hasComplaint = false;
        fixture.detectChanges();

        let nextSubmissionButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=nextSubmission]'));
        expect(nextSubmissionButtonSpan).toBeFalsy();

        component.result = new Result();
        component.result.completionDate = moment();
        fixture.detectChanges();
        nextSubmissionButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=nextSubmission]'));
        expect(nextSubmissionButtonSpan).toBeFalsy();

        component.isAtLeastInstructor = true;
        fixture.detectChanges();
        nextSubmissionButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=nextSubmission]'));
        expect(nextSubmissionButtonSpan).toBeTruthy();

        component.isAssessor = true;
        fixture.detectChanges();
        nextSubmissionButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=nextSubmission]'));
        expect(nextSubmissionButtonSpan).toBeTruthy();

        component.isAtLeastInstructor = false;
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
        expect(component.nextSubmission.emit).toHaveBeenCalledTimes(1);
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
        spyOn(component.highlightDifferencesChange, 'emit');

        component.toggleHighlightDifferences();

        expect(component.highlightDifferencesChange.emit).toHaveBeenCalled();
        expect(component.highlightDifferences).toEqual(true);
    });

    it('should set highlightDifferences to false', () => {
        component.highlightDifferences = true;
        spyOn(component.highlightDifferencesChange, 'emit');

        component.toggleHighlightDifferences();

        expect(component.highlightDifferencesChange.emit).toHaveBeenCalled();
        expect(component.highlightDifferences).toEqual(false);
    });
});
