import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { ComplaintResponseService } from 'app/assessment/manage/services/complaint-response.service';
import { ComplaintsForTutorComponent } from 'app/assessment/manage/complaints-for-tutor/complaints-for-tutor.component';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { TextareaCounterComponent } from 'app/shared-ui/textarea/textarea-counter.component';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/foundation/service/alert.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { FormsModule } from '@angular/forms';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { ComplaintResponse } from 'app/assessment/shared/entities/complaint-response.model';
import { By } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import type { ComplaintResponse as ComplaintResponseDTO } from 'app/openapi/model/complaint-response';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';

describe('ComplaintsForTutorComponent', () => {
    let complaintsForTutorComponent: ComplaintsForTutorComponent;
    let fixture: ComponentFixture<ComplaintsForTutorComponent>;
    let injectedComplaintResponseService: ComplaintResponseService;
    let httpMock: HttpTestingController;
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
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: AccountService, useClass: MockAccountService },
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
                httpMock = TestBed.inject(HttpTestingController);

                course = new Course();
                course.maxComplaintResponseTextLimit = 26;

                exercise = { id: 11, isAtLeastInstructor: true, course: course } as Exercise;
                fixture.componentRef.setInput('exercise', exercise);
                fixture.componentRef.setInput('submission', undefined);
                fixture.componentRef.setInput('complaint', undefined);
            });
    });

    const lockedResponse = (overrides: Partial<ComplaintResponseDTO> = {}): ComplaintResponseDTO => ({ id: 1, complaintId: 1, isCurrentlyLocked: true, ...overrides });

    afterEach(() => {
        httpMock.verify();
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

    it('should render the section heading and both column headings in the same heading style', async () => {
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
        await fixture.whenStable();
        fixture.detectChanges();

        const sectionHeading = fixture.nativeElement.querySelector('h3') as HTMLElement;
        const columnHeadings = Array.from(fixture.nativeElement.querySelectorAll('h4')) as HTMLElement[];
        // The complaint and its response are sibling columns, so their headings must be the same element, otherwise the textareas start at different heights.
        expect(columnHeadings).toHaveLength(2);
        expect(fixture.nativeElement.querySelectorAll('h3')).toHaveLength(1);

        for (const heading of [sectionHeading, ...columnHeadings]) {
            expect(['mb-2', 'text-base', 'leading-tight', 'font-semibold'].every((utility) => heading.classList.contains(utility))).toBe(true);
        }
        expect(columnHeadings[0].className).toBe(columnHeadings[1].className);
        // The textareas follow their heading directly in both columns.
        expect(columnHeadings[0].nextElementSibling).toBe(fixture.nativeElement.querySelector('#complaintTextArea'));
        expect(columnHeadings[1].nextElementSibling?.querySelector('#responseTextArea')).not.toBeNull();
    });

    it('should create a new complaint response for a unhandled complaint without a connected complaint response', () => {
        const unhandledComplaint = new Complaint();
        unhandledComplaint.id = 1;
        unhandledComplaint.accepted = undefined;
        unhandledComplaint.complaintText = 'please check again';
        unhandledComplaint.complaintResponse = undefined;
        unhandledComplaint.complaintType = ComplaintType.COMPLAINT;

        fixture.componentRef.setInput('complaint', unhandledComplaint);
        fixture.componentRef.setInput('isAssessor', false);
        fixture.detectChanges();

        const createRequest = httpMock.expectOne({ method: 'POST', url: '/api/assessment/complaints/1/response' });
        expect(createRequest.request.body).toBeNull();
        createRequest.flush(lockedResponse({ reviewer: { id: 2, login: 'tutor' } }));

        expect(complaintsForTutorComponent.complaint()).toBe(unhandledComplaint);
        expect(complaintsForTutorComponent.complaintResponse.id).toBe(1);
        expect(complaintsForTutorComponent.complaintResponse.isCurrentlyLocked).toBe(true);
        expect(complaintsForTutorComponent.complaintResponse.reviewer?.login).toBe('tutor');
        expect(complaintsForTutorComponent.complaintResponse.complaint).toBe(unhandledComplaint);
        expect(complaintsForTutorComponent.lockedByCurrentUser()).toBe(true);
        fixture.detectChanges();
        const lockButton = fixture.debugElement.query(By.css('#lockButton')).nativeElement;
        const lockDuration = fixture.debugElement.query(By.css('#lockDuration')).nativeElement;

        expect(lockButton).not.toBeNull();
        expect(lockDuration).not.toBeNull();

        const navigateBackSpy = vi.spyOn(complaintsForTutorComponent, 'navigateBack').mockImplementation(() => {});
        lockButton.click();
        httpMock.expectOne({ method: 'DELETE', url: '/api/assessment/complaints/1/response' }).flush(null);
        expect(navigateBackSpy).toHaveBeenCalledOnce();
    });

    it('should handle error when creating a new complaint response for an unhandled complaint', () => {
        const unhandledComplaint = new Complaint();
        unhandledComplaint.id = 1;
        unhandledComplaint.accepted = undefined;
        unhandledComplaint.complaintText = 'please check again';
        unhandledComplaint.complaintResponse = undefined;
        unhandledComplaint.complaintType = ComplaintType.COMPLAINT;

        const alertServiceErrorSpy = vi.spyOn(injectedAlertService, 'error');

        fixture.componentRef.setInput('complaint', unhandledComplaint);
        fixture.componentRef.setInput('isAssessor', false);
        fixture.detectChanges();

        httpMock.expectOne({ method: 'POST', url: '/api/assessment/complaints/1/response' }).flush(null, { status: 404, statusText: 'Not Found' });

        expect(complaintsForTutorComponent.isLoading()).toBe(false);
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

        fixture.componentRef.setInput('isAssessor', false);
        fixture.componentRef.setInput('complaint', unhandledComplaint);
        fixture.detectChanges();

        const refreshRequest = httpMock.expectOne({ method: 'PATCH', url: '/api/assessment/complaints/1/response' });
        expect(refreshRequest.request.body).toEqual({ action: 'REFRESH_LOCK' });
        refreshRequest.flush(lockedResponse());

        expect(complaintsForTutorComponent.complaint()).toBe(unhandledComplaint);
        expect(complaintsForTutorComponent.complaintResponse.id).toBe(1);
        expect(complaintsForTutorComponent.complaintResponse.isCurrentlyLocked).toBe(true);
        fixture.detectChanges();
        const lockButton = fixture.debugElement.query(By.css('#lockButton')).nativeElement;
        const lockDuration = fixture.debugElement.query(By.css('#lockDuration')).nativeElement;

        expect(lockButton).not.toBeNull();
        expect(lockDuration).not.toBeNull();

        const navigateBackSpy = vi.spyOn(complaintsForTutorComponent, 'navigateBack').mockImplementation(() => {});
        lockButton.click();
        httpMock.expectOne({ method: 'DELETE', url: '/api/assessment/complaints/1/response' }).flush(null);
        expect(navigateBackSpy).toHaveBeenCalledOnce();
    });

    it('should send event when accepting a complaint', () => {
        fixture.detectChanges();
        complaintsForTutorComponent.isLockedForLoggedInUser.set(false);

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
        complaintsForTutorComponent.isLockedForLoggedInUser.set(false);

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

        fixture.changeDetectorRef.detectChanges();

        const rejectComplaintButton = fixture.debugElement.query(By.css('#rejectComplaintButton')).nativeElement;
        rejectComplaintButton.click();

        const resolveRequest = httpMock.expectOne({ method: 'PATCH', url: '/api/assessment/complaints/1/response' });
        expect(resolveRequest.request.body).toEqual({ action: 'RESOLVE_COMPLAINT', responseText: 'rejected', complaintIsAccepted: false });
        resolveRequest.flush(lockedResponse({ isCurrentlyLocked: false, responseText: 'rejected', submittedTime: '2026-01-01T10:00:00Z' }));

        expect(complaintsForTutorComponent.handled()).toBe(true);
        expect(complaintsForTutorComponent.complaintResponse.submittedTime?.toISOString()).toBe('2026-01-01T10:00:00.000Z');
        expect(complaintsForTutorComponent.complaint().accepted).toBe(false);
    });

    it('should just display disabled accept and reject complaint button', () => {
        const unhandledComplaint = new Complaint();
        unhandledComplaint.id = 1;
        unhandledComplaint.accepted = undefined;
        unhandledComplaint.complaintText = 'please check again';
        unhandledComplaint.complaintResponse = new ComplaintResponse();
        unhandledComplaint.complaintResponse.id = 1;
        unhandledComplaint.complaintType = ComplaintType.COMPLAINT;

        fixture.componentRef.setInput('isAssessor', false);
        fixture.componentRef.setInput('complaint', unhandledComplaint);

        // Update fixture
        fixture.detectChanges();
        httpMock.expectOne({ method: 'PATCH', url: '/api/assessment/complaints/1/response' }).flush(lockedResponse());
        fixture.detectChanges();

        const responseTextArea = fixture.debugElement.query(By.css('#responseTextArea')).nativeElement;
        // Drive the value through the [(ngModel)] input event so the (zoneless) change detection
        // re-evaluates the buttons' [disabled] bindings; directly mutating the DOM value no longer
        // marks those bindings dirty in Angular's zoneless reactivity model.
        responseTextArea.value = 'abcdefghijklmnopqrstuvwxyz';
        responseTextArea.dispatchEvent(new Event('input'));
        expect(responseTextArea.value).toHaveLength(26);
        expect(complaintsForTutorComponent.maxComplaintResponseTextLimit()).toBe(26);

        fixture.changeDetectorRef.detectChanges();

        const rejectComplaintButton = fixture.debugElement.query(By.css('#rejectComplaintButton')).nativeElement;
        const acceptComplaintButton = fixture.debugElement.query(By.css('#acceptComplaintButton')).nativeElement;
        expect(rejectComplaintButton.disabled).toBe(false);
        expect(acceptComplaintButton.disabled).toBe(false);

        responseTextArea.value = responseTextArea.value + 'A';
        responseTextArea.dispatchEvent(new Event('input'));
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

        fixture.componentRef.setInput('isAssessor', false);
        fixture.componentRef.setInput('complaint', unhandledComplaint);

        // Update fixture
        fixture.detectChanges();
        httpMock.expectOne({ method: 'PATCH', url: '/api/assessment/complaints/1/response' }).flush(lockedResponse());
        fixture.detectChanges();

        const responseTextArea = fixture.debugElement.query(By.css('#responseTextArea')).nativeElement;
        expect(responseTextArea.maxLength).toBe(26);
    });

    it('should calculate the correct maximum text length for exam exercises', () => {
        exercise.course = undefined;
        exercise.exerciseGroup = { exam: { course: course } } as ExerciseGroup;

        fixture.changeDetectorRef.detectChanges();

        // use the default value if the course would define a lower maximum for exam exercises
        expect(complaintsForTutorComponent.maxComplaintResponseTextLimit()).toBe(2000);
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

        complaintsForTutorComponent.isLoading.set(false);
        complaintsForTutorComponent.handled.set(false);
        complaintsForTutorComponent.showLockDuration.set(true);
        complaintsForTutorComponent.lockedByCurrentUser.set(true);

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

        expect(complaintsForTutorComponent.isLoading()).toBe(true);
        expect(complaintsForTutorComponent.handled()).toBe(false);
        expect(complaintsForTutorComponent.showLockDuration()).toBe(true);
        expect(complaintsForTutorComponent.lockedByCurrentUser()).toBe(true);

        // Run all pending timers to execute the setTimeout callback
        vi.runAllTimers();

        expect(complaintsForTutorComponent.isLoading()).toBe(false);

        expect(complaintsForTutorComponent.handled()).toBe(isSuccess);
        expect(complaintsForTutorComponent.showLockDuration()).toBe(!isSuccess);
        expect(complaintsForTutorComponent.lockedByCurrentUser()).toBe(!isSuccess);

        vi.useRealTimers();
    });
});
