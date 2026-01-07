import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { ComplaintResponseService } from 'app/assessment/manage/services/complaint-response.service';
import { ComplaintsForTutorComponent } from 'app/assessment/manage/complaints-for-tutor/complaints-for-tutor.component';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { TextareaCounterComponent } from 'app/shared/textarea/textarea-counter.component';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { FormsModule } from '@angular/forms';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { ComplaintResponse } from 'app/assessment/shared/entities/complaint-response.model';
import { By } from '@angular/platform-browser';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';

describe('ComplaintsForTutorComponent', () => {
    setupTestBed({ zoneless: true });
    let complaintsForTutorComponent: ComplaintsForTutorComponent;
    let fixture: ComponentFixture<ComplaintsForTutorComponent>;
    let injectedComplaintResponseService: ComplaintResponseService;
    let injectedAlertService: AlertService;

    let course: Course;
    let exercise: Exercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                FormsModule,
                ComplaintsForTutorComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(TextareaCounterComponent),
                MockDirective(TranslateDirective),
            ],
            providers: [
                provideRouter([]),
                MockProvider(ComplaintResponseService),
                MockProvider(ComplaintService),
                { provide: AlertService, useClass: MockAlertService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ComplaintsForTutorComponent);
                complaintsForTutorComponent = fixture.componentInstance;
                injectedComplaintResponseService = TestBed.inject(ComplaintResponseService);
                injectedAlertService = TestBed.inject(AlertService);

                course = new Course();
                course.maxComplaintResponseTextLimit = 26;

                exercise = { id: 11, isAtLeastInstructor: true, course: course } as Exercise;
                fixture.componentRef.setInput('exercise', exercise);
                fixture.componentRef.setInput('submission', undefined);
                fixture.componentRef.setInput('complaint', undefined);
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should instantiate', () => {
        fixture.detectChanges();
        expect(complaintsForTutorComponent).not.toBeNull();
    });

    it('should just display an already handled complaint', async () => {
        const handledComplaint = new Complaint();
        handledComplaint.id = 1;
        handledComplaint.accepted = true;
        handledComplaint.complaintText = 'please check again';
        handledComplaint.complaintResponse = new ComplaintResponse();
        handledComplaint.complaintResponse.id = 1;
        handledComplaint.complaintResponse.responseText = 'gj';
        handledComplaint.complaintType = ComplaintType.COMPLAINT;
        fixture.componentRef.setInput('isAssessor', false);
        fixture.componentRef.setInput('complaint', handledComplaint);
        fixture.detectChanges();
        // ngModel writes data asynchronously into the DOM, wait for stability
        await fixture.whenStable();
        fixture.detectChanges();

        const responseTextArea = fixture.debugElement.query(By.css('#responseTextArea')).nativeElement;
        const complainTextArea = fixture.debugElement.query(By.css('#complaintTextArea')).nativeElement;
        expect(responseTextArea.value).toEqual(handledComplaint.complaintResponse.responseText);
        expect(responseTextArea.disabled).toBe(true);
        expect(responseTextArea.readOnly).toBe(true);
        expect(complainTextArea.readOnly).toBe(true);
        expect(complainTextArea.value).toEqual(handledComplaint.complaintText);
    });

    it('should create a new complaint response for a unhandled complaint without a connected complaint response', () => {
        const unhandledComplaint = new Complaint();
        unhandledComplaint.id = 1;
        unhandledComplaint.accepted = undefined;
        unhandledComplaint.complaintText = 'please check again';
        unhandledComplaint.complaintResponse = undefined;
        unhandledComplaint.complaintType = ComplaintType.COMPLAINT;

        const freshlyCreatedComplaintResponse = new ComplaintResponse();
        freshlyCreatedComplaintResponse.id = 1;
        freshlyCreatedComplaintResponse.isCurrentlyLocked = true;
        freshlyCreatedComplaintResponse.complaint = unhandledComplaint;

        const createLockStub = vi.spyOn(injectedComplaintResponseService, 'createLock').mockReturnValue(
            of(
                new HttpResponse({
                    body: freshlyCreatedComplaintResponse,
                    status: 201,
                }),
            ),
        );

        fixture.componentRef.setInput('complaint', unhandledComplaint);
        fixture.componentRef.setInput('isAssessor', false);
        fixture.detectChanges();
        // We need the tick as `ngModel` writes data asynchronously into the DOM!

        expect(createLockStub).toHaveBeenCalledTimes(1);
        expect(complaintsForTutorComponent.complaint()).toEqual(freshlyCreatedComplaintResponse.complaint);
        expect(complaintsForTutorComponent.complaintResponse).toEqual(freshlyCreatedComplaintResponse);
        const lockButton = fixture.debugElement.query(By.css('#lockButton')).nativeElement;
        const lockDuration = fixture.debugElement.query(By.css('#lockDuration')).nativeElement;

        expect(lockButton).not.toBeNull();
        expect(lockDuration).not.toBeNull();

        // now we test if we can give up the lock
        const removeLockStub = vi.spyOn(injectedComplaintResponseService, 'removeLock').mockReturnValue(of());
        lockButton.click();
        expect(removeLockStub).toHaveBeenCalledTimes(1);
    });

    it('should handle error when creating a new complaint response for an unhandled complaint', () => {
        const unhandledComplaint = new Complaint();
        unhandledComplaint.id = 1;
        unhandledComplaint.accepted = undefined;
        unhandledComplaint.complaintText = 'please check again';
        unhandledComplaint.complaintResponse = undefined;
        unhandledComplaint.complaintType = ComplaintType.COMPLAINT;

        const error = { status: 404 };
        const createLockStub = vi.spyOn(injectedComplaintResponseService, 'createLock').mockReturnValue(throwError(() => new HttpErrorResponse(error)));
        const alertServiceErrorSpy = vi.spyOn(injectedAlertService, 'error');

        fixture.componentRef.setInput('complaint', unhandledComplaint);
        fixture.componentRef.setInput('isAssessor', false);
        fixture.detectChanges();

        expect(createLockStub).toHaveBeenCalledTimes(1);
        expect(complaintsForTutorComponent.isLoading).toBe(false);
        expect(alertServiceErrorSpy).toHaveBeenCalledTimes(1);
    });

    it('should refresh a complaint response for a unhandled complaint with a connected complaint response', () => {
        const unhandledComplaint = new Complaint();
        unhandledComplaint.id = 1;
        unhandledComplaint.accepted = undefined;
        unhandledComplaint.complaintText = 'please check again';
        unhandledComplaint.complaintResponse = undefined;
        unhandledComplaint.complaintResponse = new ComplaintResponse();
        unhandledComplaint.complaintResponse.id = 1;
        unhandledComplaint.complaintType = ComplaintType.COMPLAINT;
        vi.spyOn(injectedComplaintResponseService, 'isComplaintResponseLockedForLoggedInUser').mockReturnValue(false);

        const freshlyCreatedComplaintResponse = new ComplaintResponse();
        freshlyCreatedComplaintResponse.id = 1;
        freshlyCreatedComplaintResponse.isCurrentlyLocked = true;
        freshlyCreatedComplaintResponse.complaint = unhandledComplaint;

        const createLockStub = vi.spyOn(injectedComplaintResponseService, 'refreshLockOrResolveComplaint').mockReturnValue(
            of(
                new HttpResponse({
                    body: freshlyCreatedComplaintResponse,
                    status: 201,
                }),
            ),
        );

        fixture.componentRef.setInput('isAssessor', false);
        fixture.componentRef.setInput('complaint', unhandledComplaint);
        fixture.detectChanges();
        // We need the tick as `ngModel` writes data asynchronously into the DOM!

        expect(createLockStub).toHaveBeenCalledTimes(1);
        expect(complaintsForTutorComponent.complaint()).toEqual(freshlyCreatedComplaintResponse.complaint);
        expect(complaintsForTutorComponent.complaintResponse).toEqual(freshlyCreatedComplaintResponse);
        const lockButton = fixture.debugElement.query(By.css('#lockButton')).nativeElement;
        const lockDuration = fixture.debugElement.query(By.css('#lockDuration')).nativeElement;

        expect(lockButton).not.toBeNull();
        expect(lockDuration).not.toBeNull();

        // now we test if we can give up the lock
        const removeLockStub = vi.spyOn(injectedComplaintResponseService, 'removeLock').mockReturnValue(of());
        lockButton.click();
        expect(removeLockStub).toHaveBeenCalledTimes(1);
    });

    it('should send event when accepting a complaint', () => {
        fixture.detectChanges();
        complaintsForTutorComponent.isLockedForLoggedInUser = false;

        const unhandledComplaint = new Complaint();
        unhandledComplaint.id = 1;
        unhandledComplaint.accepted = undefined;
        unhandledComplaint.complaintType = ComplaintType.COMPLAINT;
        unhandledComplaint.complaintText = 'please check again';
        unhandledComplaint.complaintResponse = undefined;
        unhandledComplaint.complaintResponse = new ComplaintResponse();
        unhandledComplaint.complaintResponse.responseText = 'accepted';
        unhandledComplaint.complaintResponse.id = 1;
        complaintsForTutorComponent.complaintResponse = unhandledComplaint.complaintResponse;
        fixture.componentRef.setInput('complaint', unhandledComplaint);

        const emitSpy = vi.spyOn(complaintsForTutorComponent.updateAssessmentAfterComplaint, 'emit');

        fixture.changeDetectorRef.detectChanges();

        const acceptComplaintButton = fixture.debugElement.query(By.css('#acceptComplaintButton')).nativeElement;
        acceptComplaintButton.click();
        expect(emitSpy).toHaveBeenCalledTimes(1);
        const event = emitSpy.mock.calls[0][0];
        expect(event).not.toBeNull();
    });

    it('should directly resolve when rejecting a complaint', () => {
        fixture.detectChanges();
        complaintsForTutorComponent.isLockedForLoggedInUser = false;

        const unhandledComplaint = new Complaint();
        unhandledComplaint.id = 1;
        unhandledComplaint.accepted = undefined;
        unhandledComplaint.complaintType = ComplaintType.COMPLAINT;
        unhandledComplaint.complaintText = 'please check again';
        unhandledComplaint.complaintResponse = undefined;
        unhandledComplaint.complaintResponse = new ComplaintResponse();
        unhandledComplaint.complaintResponse.responseText = 'rejected';
        unhandledComplaint.complaintResponse.id = 1;
        complaintsForTutorComponent.complaintResponse = unhandledComplaint.complaintResponse;
        fixture.componentRef.setInput('complaint', unhandledComplaint);

        const freshlyCreatedComplaintResponse = new ComplaintResponse();
        freshlyCreatedComplaintResponse.id = 1;
        freshlyCreatedComplaintResponse.isCurrentlyLocked = true;
        freshlyCreatedComplaintResponse.complaint = unhandledComplaint;

        const resolveStub = vi.spyOn(injectedComplaintResponseService, 'refreshLockOrResolveComplaint').mockReturnValue(
            of(
                new HttpResponse({
                    body: freshlyCreatedComplaintResponse,
                    status: 201,
                }),
            ),
        );

        fixture.changeDetectorRef.detectChanges();

        const rejectComplaintButton = fixture.debugElement.query(By.css('#rejectComplaintButton')).nativeElement;
        rejectComplaintButton.click();

        expect(resolveStub).toHaveBeenCalledTimes(1);
    });

    it('should just display disabled accept and reject complaint button', () => {
        const unhandledComplaint = new Complaint();
        unhandledComplaint.id = 1;
        unhandledComplaint.accepted = undefined;
        unhandledComplaint.complaintText = 'please check again';
        unhandledComplaint.complaintResponse = new ComplaintResponse();
        unhandledComplaint.complaintResponse.id = 1;
        unhandledComplaint.complaintType = ComplaintType.COMPLAINT;

        const freshlyCreatedComplaintResponse = new ComplaintResponse();
        freshlyCreatedComplaintResponse.id = 1;
        freshlyCreatedComplaintResponse.isCurrentlyLocked = true;
        freshlyCreatedComplaintResponse.complaint = unhandledComplaint;

        vi.spyOn(injectedComplaintResponseService, 'refreshLockOrResolveComplaint').mockReturnValue(
            of(
                new HttpResponse({
                    body: freshlyCreatedComplaintResponse,
                    status: 201,
                }),
            ),
        );

        fixture.componentRef.setInput('isAssessor', false);
        fixture.componentRef.setInput('complaint', unhandledComplaint);

        // Update fixture
        fixture.detectChanges();

        const responseTextArea = fixture.debugElement.query(By.css('#responseTextArea')).nativeElement;
        responseTextArea.value = 'abcdefghijklmnopqrstuvwxyz';
        expect(responseTextArea.value).toHaveLength(26);
        expect(complaintsForTutorComponent.maxComplaintResponseTextLimit).toBe(26);

        const rejectComplaintButton = fixture.debugElement.query(By.css('#rejectComplaintButton')).nativeElement;
        const acceptComplaintButton = fixture.debugElement.query(By.css('#acceptComplaintButton')).nativeElement;
        expect(rejectComplaintButton.disabled).toBe(false);
        expect(acceptComplaintButton.disabled).toBe(false);

        responseTextArea.value = responseTextArea.value + 'A';
        expect(responseTextArea.value).toHaveLength(27);

        // Update fixture
        fixture.changeDetectorRef.detectChanges();

        expect(rejectComplaintButton.disabled).toBe(true);
        expect(acceptComplaintButton.disabled).toBe(true);
    });

    it('text area should have the correct max length', () => {
        const unhandledComplaint = new Complaint();
        unhandledComplaint.id = 1;
        unhandledComplaint.accepted = undefined;
        unhandledComplaint.complaintText = 'please check again';
        unhandledComplaint.complaintResponse = new ComplaintResponse();
        unhandledComplaint.complaintResponse.id = 1;
        unhandledComplaint.complaintType = ComplaintType.COMPLAINT;

        const freshlyCreatedComplaintResponse = new ComplaintResponse();
        freshlyCreatedComplaintResponse.id = 1;
        freshlyCreatedComplaintResponse.isCurrentlyLocked = true;
        freshlyCreatedComplaintResponse.complaint = unhandledComplaint;

        vi.spyOn(injectedComplaintResponseService, 'refreshLockOrResolveComplaint').mockReturnValue(
            of(
                new HttpResponse({
                    body: freshlyCreatedComplaintResponse,
                    status: 201,
                }),
            ),
        );

        fixture.componentRef.setInput('isAssessor', false);
        fixture.componentRef.setInput('complaint', unhandledComplaint);

        // Update fixture
        fixture.detectChanges();

        const responseTextArea = fixture.debugElement.query(By.css('#responseTextArea')).nativeElement;
        expect(responseTextArea.maxLength).toBe(26);
    });

    it('should calculate the correct maximum text length for exam exercises', () => {
        exercise.course = undefined;
        exercise.exerciseGroup = { exam: { course: course } } as ExerciseGroup;

        fixture.changeDetectorRef.detectChanges();

        // use the default value if the course would define a lower maximum for exam exercises
        expect(complaintsForTutorComponent.maxComplaintResponseTextLimit).toBe(2000);
    });

    it.each(['success', 'failure'])('should handle %s after updating assessment after complaint', (successOrFailure: string) => {
        vi.useFakeTimers();
        const isSuccess = successOrFailure === 'success';

        const unhandledComplaint = new Complaint();
        unhandledComplaint.id = 2;
        unhandledComplaint.accepted = undefined;
        unhandledComplaint.complaintText = 'please check again';
        unhandledComplaint.complaintResponse = undefined;
        unhandledComplaint.complaintType = ComplaintType.COMPLAINT;

        const newComplaintResponse = new ComplaintResponse();
        newComplaintResponse.id = 3;
        newComplaintResponse.isCurrentlyLocked = true;
        newComplaintResponse.complaint = unhandledComplaint;
        newComplaintResponse.responseText = 'accepted';

        fixture.componentRef.setInput('complaint', unhandledComplaint);
        complaintsForTutorComponent.complaintResponse = newComplaintResponse;

        complaintsForTutorComponent.isLoading = false;
        complaintsForTutorComponent.handled = false;
        complaintsForTutorComponent.showLockDuration = true;
        complaintsForTutorComponent.lockedByCurrentUser = true;

        complaintsForTutorComponent.updateAssessmentAfterComplaint.subscribe((assessmentAfterComplaint) =>
            // setTimeout is used to defer the callback execution
            setTimeout(() => {
                expect(assessmentAfterComplaint.complaintResponse).toEqual(newComplaintResponse);
                if (isSuccess) {
                    assessmentAfterComplaint.onSuccess();
                } else {
                    assessmentAfterComplaint.onError();
                }
            }),
        );

        complaintsForTutorComponent.respondToComplaint(true);

        expect(complaintsForTutorComponent.isLoading).toBe(true);
        expect(complaintsForTutorComponent.handled).toBe(false);
        expect(complaintsForTutorComponent.showLockDuration).toBe(true);
        expect(complaintsForTutorComponent.lockedByCurrentUser).toBe(true);

        // Run all pending timers to execute the setTimeout callback
        vi.runAllTimers();

        expect(complaintsForTutorComponent.isLoading).toBe(false);

        expect(complaintsForTutorComponent.handled).toBe(isSuccess);
        expect(complaintsForTutorComponent.showLockDuration).toBe(!isSuccess);
        expect(complaintsForTutorComponent.lockedByCurrentUser).toBe(!isSuccess);

        vi.useRealTimers();
    });
});
