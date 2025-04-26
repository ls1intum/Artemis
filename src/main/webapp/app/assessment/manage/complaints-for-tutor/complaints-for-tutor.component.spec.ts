import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
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
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ComplaintsForTutorComponent', () => {
    let complaintsForTutorComponent: ComplaintsForTutorComponent;
    let fixture: ComponentFixture<ComplaintsForTutorComponent>;
    let injectedComplaintResponseService: ComplaintResponseService;

    let course: Course;
    let exercise: Exercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FormsModule],
            declarations: [MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe), MockComponent(TextareaCounterComponent), MockDirective(TranslateDirective)],
            providers: [
                provideRouter([]),
                MockProvider(ComplaintResponseService),
                MockProvider(ComplaintService),
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ComplaintsForTutorComponent);
                complaintsForTutorComponent = fixture.componentInstance;
                injectedComplaintResponseService = TestBed.inject(ComplaintResponseService);

                course = new Course();
                course.maxComplaintResponseTextLimit = 26;

                exercise = { id: 11, isAtLeastInstructor: true, course: course } as Exercise;
                complaintsForTutorComponent.exercise = exercise;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should instantiate', () => {
        fixture.detectChanges();
        expect(complaintsForTutorComponent).not.toBeNull();
    });

    it('should just display an already handled complaint', fakeAsync(() => {
        const handledComplaint = new Complaint();
        handledComplaint.id = 1;
        handledComplaint.accepted = true;
        handledComplaint.complaintText = 'please check again';
        handledComplaint.complaintResponse = new ComplaintResponse();
        handledComplaint.complaintResponse.id = 1;
        handledComplaint.complaintResponse.responseText = 'gj';
        handledComplaint.complaintType = ComplaintType.COMPLAINT;
        complaintsForTutorComponent.isAssessor = false;
        complaintsForTutorComponent.complaint = handledComplaint;
        fixture.detectChanges();
        // We need the tick as `ngModel` writes data asynchronously into the DOM!
        tick();

        const responseTextArea = fixture.debugElement.query(By.css('#responseTextArea')).nativeElement;
        const complainTextArea = fixture.debugElement.query(By.css('#complaintTextArea')).nativeElement;
        expect(responseTextArea.value).toEqual(handledComplaint.complaintResponse.responseText);
        expect(responseTextArea.disabled).toBeTrue();
        expect(responseTextArea.readOnly).toBeTrue();
        expect(complainTextArea.readOnly).toBeTrue();
        expect(complainTextArea.value).toEqual(handledComplaint.complaintText);
    }));

    it('should create a new complaint response for a unhandled complaint without a connected complaint response', fakeAsync(() => {
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

        const createLockStub = jest.spyOn(injectedComplaintResponseService, 'createLock').mockReturnValue(
            of(
                new HttpResponse({
                    body: freshlyCreatedComplaintResponse,
                    status: 201,
                }),
            ),
        );

        complaintsForTutorComponent.complaint = unhandledComplaint;
        complaintsForTutorComponent.isAssessor = false;
        fixture.detectChanges();
        // We need the tick as `ngModel` writes data asynchronously into the DOM!
        tick();

        expect(createLockStub).toHaveBeenCalledOnce();
        expect(complaintsForTutorComponent.complaint).toEqual(freshlyCreatedComplaintResponse.complaint);
        expect(complaintsForTutorComponent.complaintResponse).toEqual(freshlyCreatedComplaintResponse);
        const lockButton = fixture.debugElement.query(By.css('#lockButton')).nativeElement;
        const lockDuration = fixture.debugElement.query(By.css('#lockDuration')).nativeElement;

        expect(lockButton).not.toBeNull();
        expect(lockDuration).not.toBeNull();

        // now we test if we can give up the lock
        const removeLockStub = jest.spyOn(injectedComplaintResponseService, 'removeLock').mockReturnValue(of());
        lockButton.click();
        expect(removeLockStub).toHaveBeenCalledOnce();
    }));

    it('should refresh a complaint response for a unhandled complaint with a connected complaint response', fakeAsync(() => {
        const unhandledComplaint = new Complaint();
        unhandledComplaint.id = 1;
        unhandledComplaint.accepted = undefined;
        unhandledComplaint.complaintText = 'please check again';
        unhandledComplaint.complaintResponse = undefined;
        unhandledComplaint.complaintResponse = new ComplaintResponse();
        unhandledComplaint.complaintResponse.id = 1;
        unhandledComplaint.complaintType = ComplaintType.COMPLAINT;
        jest.spyOn(injectedComplaintResponseService, 'isComplaintResponseLockedForLoggedInUser').mockReturnValue(false);

        const freshlyCreatedComplaintResponse = new ComplaintResponse();
        freshlyCreatedComplaintResponse.id = 1;
        freshlyCreatedComplaintResponse.isCurrentlyLocked = true;
        freshlyCreatedComplaintResponse.complaint = unhandledComplaint;

        const createLockStub = jest.spyOn(injectedComplaintResponseService, 'refreshLockOrResolveComplaint').mockReturnValue(
            of(
                new HttpResponse({
                    body: freshlyCreatedComplaintResponse,
                    status: 201,
                }),
            ),
        );

        complaintsForTutorComponent.isAssessor = false;
        complaintsForTutorComponent.complaint = unhandledComplaint;
        fixture.detectChanges();
        // We need the tick as `ngModel` writes data asynchronously into the DOM!
        tick();

        expect(createLockStub).toHaveBeenCalledOnce();
        expect(complaintsForTutorComponent.complaint).toEqual(freshlyCreatedComplaintResponse.complaint);
        expect(complaintsForTutorComponent.complaintResponse).toEqual(freshlyCreatedComplaintResponse);
        const lockButton = fixture.debugElement.query(By.css('#lockButton')).nativeElement;
        const lockDuration = fixture.debugElement.query(By.css('#lockDuration')).nativeElement;

        expect(lockButton).not.toBeNull();
        expect(lockDuration).not.toBeNull();

        // now we test if we can give up the lock
        const removeLockStub = jest.spyOn(injectedComplaintResponseService, 'removeLock').mockReturnValue(of());
        lockButton.click();
        expect(removeLockStub).toHaveBeenCalledOnce();
    }));

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
        complaintsForTutorComponent.complaint = unhandledComplaint;

        const emitSpy = jest.spyOn(complaintsForTutorComponent.updateAssessmentAfterComplaint, 'emit');

        fixture.detectChanges();

        const acceptComplaintButton = fixture.debugElement.query(By.css('#acceptComplaintButton')).nativeElement;
        acceptComplaintButton.click();
        expect(emitSpy).toHaveBeenCalledOnce();
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
        complaintsForTutorComponent.complaint = unhandledComplaint;

        const freshlyCreatedComplaintResponse = new ComplaintResponse();
        freshlyCreatedComplaintResponse.id = 1;
        freshlyCreatedComplaintResponse.isCurrentlyLocked = true;
        freshlyCreatedComplaintResponse.complaint = unhandledComplaint;

        const resolveStub = jest.spyOn(injectedComplaintResponseService, 'refreshLockOrResolveComplaint').mockReturnValue(
            of(
                new HttpResponse({
                    body: freshlyCreatedComplaintResponse,
                    status: 201,
                }),
            ),
        );

        fixture.detectChanges();

        const rejectComplaintButton = fixture.debugElement.query(By.css('#rejectComplaintButton')).nativeElement;
        rejectComplaintButton.click();

        expect(resolveStub).toHaveBeenCalledOnce();
    });

    it('should just display disabled accept and reject complaint button', fakeAsync(() => {
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

        jest.spyOn(injectedComplaintResponseService, 'refreshLockOrResolveComplaint').mockReturnValue(
            of(
                new HttpResponse({
                    body: freshlyCreatedComplaintResponse,
                    status: 201,
                }),
            ),
        );

        complaintsForTutorComponent.isAssessor = false;
        complaintsForTutorComponent.complaint = unhandledComplaint;

        // Update fixture
        fixture.detectChanges();
        tick();

        const responseTextArea = fixture.debugElement.query(By.css('#responseTextArea')).nativeElement;
        responseTextArea.value = 'abcdefghijklmnopqrstuvwxyz';
        expect(responseTextArea.value).toHaveLength(26);
        expect(complaintsForTutorComponent.maxComplaintResponseTextLimit).toBe(26);

        const rejectComplaintButton = fixture.debugElement.query(By.css('#rejectComplaintButton')).nativeElement;
        const acceptComplaintButton = fixture.debugElement.query(By.css('#acceptComplaintButton')).nativeElement;
        expect(rejectComplaintButton.disabled).toBeFalse();
        expect(acceptComplaintButton.disabled).toBeFalse();

        responseTextArea.value = responseTextArea.value + 'A';
        expect(responseTextArea.value).toHaveLength(27);

        // Update fixture
        fixture.detectChanges();
        tick();

        expect(rejectComplaintButton.disabled).toBeTrue();
        expect(acceptComplaintButton.disabled).toBeTrue();
    }));

    it('text area should have the correct max length', fakeAsync(() => {
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

        jest.spyOn(injectedComplaintResponseService, 'refreshLockOrResolveComplaint').mockReturnValue(
            of(
                new HttpResponse({
                    body: freshlyCreatedComplaintResponse,
                    status: 201,
                }),
            ),
        );

        complaintsForTutorComponent.isAssessor = false;
        complaintsForTutorComponent.complaint = unhandledComplaint;

        // Update fixture
        fixture.detectChanges();
        tick();

        const responseTextArea = fixture.debugElement.query(By.css('#responseTextArea')).nativeElement;
        expect(responseTextArea.maxLength).toBe(26);
    }));

    it('should calculate the correct maximum text length for exam exercises', () => {
        exercise.course = undefined;
        exercise.exerciseGroup = { exam: { course: course } } as ExerciseGroup;

        fixture.detectChanges();

        // use the default value if the course would define a lower maximum for exam exercises
        expect(complaintsForTutorComponent.maxComplaintResponseTextLimit).toBe(2000);
    });

    it.each(['success', 'failure'])(
        'should handle %s after updating assessment after complaint',
        fakeAsync((successOrFailure: string) => {
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

            complaintsForTutorComponent.complaint = unhandledComplaint;
            complaintsForTutorComponent.complaintResponse = newComplaintResponse;

            complaintsForTutorComponent.isLoading = false;
            complaintsForTutorComponent.handled = false;
            complaintsForTutorComponent.showLockDuration = true;
            complaintsForTutorComponent.lockedByCurrentUser = true;

            complaintsForTutorComponent.updateAssessmentAfterComplaint.subscribe((assessmentAfterComplaint) =>
                // setTimeout is used so that the code below does not execute until tick() is called.
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

            expect(complaintsForTutorComponent.isLoading).toBeTrue();
            expect(complaintsForTutorComponent.handled).toBeFalse();
            expect(complaintsForTutorComponent.showLockDuration).toBeTrue();
            expect(complaintsForTutorComponent.lockedByCurrentUser).toBeTrue();

            tick();

            expect(complaintsForTutorComponent.isLoading).toBeFalse();

            expect(complaintsForTutorComponent.handled).toBe(isSuccess);
            expect(complaintsForTutorComponent.showLockDuration).toBe(!isSuccess);
            expect(complaintsForTutorComponent.lockedByCurrentUser).toBe(!isSuccess);
        }),
    );
});
