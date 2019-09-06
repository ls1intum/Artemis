import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ActivatedRouteSnapshot } from '@angular/router';
import { By } from '@angular/platform-browser';
import { JhiAlertService } from 'ng-jhipster';
import * as moment from 'moment';

import { AssessmentHeaderComponent } from 'app/assessment-shared';
import { ArtemisSharedModule, JhiAlertComponent } from 'app/shared';
import { ArtemisTestModule } from '../../test.module';
import { Result } from 'app/entities/result';
import { AccountService } from 'app/core';
import { MockAccountService } from '../../helpers/mock-account.service';
import { mockedActivatedRoute } from '../../helpers/mock-activated-route-query-param-map';
import { Mutable } from '../../helpers/mutable';

describe('AssessmentHeaderComponent', () => {
    let component: AssessmentHeaderComponent;
    let fixture: ComponentFixture<AssessmentHeaderComponent>;
    let mockAuth: MockAccountService;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule],
            declarations: [AssessmentHeaderComponent],
            providers: [JhiAlertService, mockedActivatedRoute({}, { showBackButton: 'false' })],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(AssessmentHeaderComponent);
        component = fixture.componentInstance;
        mockAuth = (fixture.debugElement.injector.get(AccountService) as any) as MockAccountService;
        mockAuth.hasAnyAuthorityDirectSpy.and.returnValue(false);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should include jhi-alert', () => {
        const jhiAlertComponent = fixture.debugElement.query(By.directive(JhiAlertComponent));
        expect(jhiAlertComponent).toBeTruthy();
    });

    it('should display alerts', () => {
        const alertService = TestBed.get(JhiAlertService);
        alertService.success('test-alert-string');
        fixture.detectChanges();

        const jhiAlertComponent = fixture.debugElement.query(By.directive(JhiAlertComponent));
        const jhiAlertContent = jhiAlertComponent.nativeElement.textContent;
        expect(jhiAlertContent).toContain('test-alert-string');
    });

    it('should show or hide a back button', () => {
        const routeSnapshot = fixture.debugElement.injector.get(ActivatedRoute).snapshot as Mutable<ActivatedRouteSnapshot>;
        routeSnapshot.queryParamMap = convertToParamMap({ showBackButton: 'true' });
        component.ngOnInit();
        expect(component.showBackButton).toBeTruthy();
        fixture.detectChanges();
        let backButton = fixture.debugElement.query(By.css('fa-icon.back-button'));
        expect(backButton).toBeTruthy();

        routeSnapshot.queryParamMap = convertToParamMap({ showBackButton: 'false' });
        component.ngOnInit();
        expect(component.showBackButton).toBeFalsy();
        fixture.detectChanges();
        backButton = fixture.debugElement.query(By.css('fa-icon.back-button'));
        expect(backButton).toBeFalsy();
    });

    it('should emit event on back button', () => {
        spyOn(component.navigateBack, 'emit');
        const routeSnapshot = fixture.debugElement.injector.get(ActivatedRoute).snapshot as Mutable<ActivatedRouteSnapshot>;
        routeSnapshot.queryParamMap = convertToParamMap({ showBackButton: 'true' });
        component.ngOnInit();
        fixture.detectChanges();

        const backButton = fixture.debugElement.query(By.css('fa-icon.back-button'));

        backButton.nativeElement.click();
        expect(component.navigateBack.emit).toHaveBeenCalledTimes(1);
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

    it('should show resolve conflict button if hasConflict', () => {
        component.isLoading = false;
        component.hasConflict = false;
        fixture.detectChanges();

        let resolveConflictButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=resolveConflict]'));
        expect(resolveConflictButtonSpan).toBeFalsy();

        component.hasConflict = true;
        fixture.detectChanges();

        resolveConflictButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=resolveConflict]'));
        expect(resolveConflictButtonSpan).toBeTruthy();

        spyOn(component.resolveConflict, 'emit');
        resolveConflictButtonSpan.nativeElement.click();
        expect(component.resolveConflict.emit).toHaveBeenCalledTimes(1);
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

        mockAuth.hasAnyAuthorityDirectSpy.and.returnValue(true);
        component.ngOnInit();
        fixture.detectChanges();
        nextSubmissionButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=nextSubmission]'));
        expect(nextSubmissionButtonSpan).toBeTruthy();

        component.isAssessor = true;
        fixture.detectChanges();
        nextSubmissionButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=nextSubmission]'));
        expect(nextSubmissionButtonSpan).toBeTruthy();

        mockAuth.hasAnyAuthorityDirectSpy.and.returnValue(false);
        component.ngOnInit();
        fixture.detectChanges();
        nextSubmissionButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=nextSubmission]'));
        expect(nextSubmissionButtonSpan).toBeTruthy();

        component.hasComplaint = true;
        fixture.detectChanges();
        nextSubmissionButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=nextSubmission]'));
        expect(nextSubmissionButtonSpan).toBeFalsy();

        component.hasComplaint = false;
        component.busy = true;
        fixture.detectChanges();
        nextSubmissionButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=nextSubmission]'));
        expect(nextSubmissionButtonSpan).toBeTruthy();
        const nextSubmissionButton = nextSubmissionButtonSpan.parent;
        expect(nextSubmissionButton).toBeTruthy();
        expect(nextSubmissionButton!.nativeElement.disabled).toBeTruthy();

        component.busy = false;
        fixture.detectChanges();
        nextSubmissionButtonSpan = fixture.debugElement.query(By.css('[jhiTranslate$=nextSubmission]'));
        nextSubmissionButtonSpan.nativeElement.click();
        expect(component.nextSubmission.emit).toHaveBeenCalledTimes(1);
    });
});
