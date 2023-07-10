import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ComplaintService } from 'app/complaints/complaint.service';
import { ComplaintResponseService } from 'app/complaints/complaint-response.service';
import { ComplaintsForTutorComponent } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.component';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { TextareaCounterComponent } from 'app/shared/textarea/textarea-counter.component';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { RouterTestingModule } from '@angular/router/testing';
import { AlertService } from 'app/core/util/alert.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { FormsModule } from '@angular/forms';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { By } from '@angular/platform-browser';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';

describe('ComplaintsForTutorComponent', () => {
    let complaintsForTutorComponent: ComplaintsForTutorComponent;
    let complaintForTutorComponentFixture: ComponentFixture<ComplaintsForTutorComponent>;
    let injectedComplaintResponseService: ComplaintResponseService;

    let course: Course;
    let exercise: Exercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [RouterTestingModule.withRoutes([]), FormsModule],
            declarations: [ComplaintsForTutorComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe), MockComponent(TextareaCounterComponent)],
            providers: [MockProvider(ComplaintResponseService), MockProvider(ComplaintService), MockProvider(AlertService)],
        })
            .compileComponents()
            .then(() => {
                complaintForTutorComponentFixture = TestBed.createComponent(ComplaintsForTutorComponent);
                complaintsForTutorComponent = complaintForTutorComponentFixture.componentInstance;
                injectedComplaintResponseService = complaintForTutorComponentFixture.debugElement.injector.get(ComplaintResponseService);

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
        complaintForTutorComponentFixture.detectChanges();
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
        complaintForTutorComponentFixture.detectChanges();
        // We need the tick as `ngModel` writes data asynchronously into the DOM!
        tick();

        const responseTextArea = complaintForTutorComponentFixture.debugElement.query(By.css('#responseTextArea')).nativeElement;
        const complainTextArea = complaintForTutorComponentFixture.debugElement.query(By.css('#complaintTextArea')).nativeElement;
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
        complaintForTutorComponentFixture.detectChanges();
        // We need the tick as `ngModel` writes data asynchronously into the DOM!
        tick();

        expect(createLockStub).toHaveBeenCalledOnce();
        expect(complaintsForTutorComponent.complaint).toEqual(freshlyCreatedComplaintResponse.complaint);
        expect(complaintsForTutorComponent.complaintResponse).toEqual(freshlyCreatedComplaintResponse);
        const lockButton = complaintForTutorComponentFixture.debugElement.query(By.css('#lockButton')).nativeElement;
        const lockDuration = complaintForTutorComponentFixture.debugElement.query(By.css('#lockDuration')).nativeElement;

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

        const createLockStub = jest.spyOn(injectedComplaintResponseService, 'refreshLock').mockReturnValue(
            of(
                new HttpResponse({
                    body: freshlyCreatedComplaintResponse,
                    status: 201,
                }),
            ),
        );

        complaintsForTutorComponent.isAssessor = false;
        complaintsForTutorComponent.complaint = unhandledComplaint;
        complaintForTutorComponentFixture.detectChanges();
        // We need the tick as `ngModel` writes data asynchronously into the DOM!
        tick();

        expect(createLockStub).toHaveBeenCalledOnce();
        expect(complaintsForTutorComponent.complaint).toEqual(freshlyCreatedComplaintResponse.complaint);
        expect(complaintsForTutorComponent.complaintResponse).toEqual(freshlyCreatedComplaintResponse);
        const lockButton = complaintForTutorComponentFixture.debugElement.query(By.css('#lockButton')).nativeElement;
        const lockDuration = complaintForTutorComponentFixture.debugElement.query(By.css('#lockDuration')).nativeElement;

        expect(lockButton).not.toBeNull();
        expect(lockDuration).not.toBeNull();

        // now we test if we can give up the lock
        const removeLockStub = jest.spyOn(injectedComplaintResponseService, 'removeLock').mockReturnValue(of());
        lockButton.click();
        expect(removeLockStub).toHaveBeenCalledOnce();
    }));

    it('should send event when accepting a complaint', () => {
        complaintForTutorComponentFixture.detectChanges();
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

        complaintForTutorComponentFixture.detectChanges();

        const acceptComplaintButton = complaintForTutorComponentFixture.debugElement.query(By.css('#acceptComplaintButton')).nativeElement;
        acceptComplaintButton.click();
        expect(emitSpy).toHaveBeenCalledOnce();
        const event = emitSpy.mock.calls[0][0];
        expect(event).not.toBeNull();
    });

    it('should directly resolve when rejecting a complaint', () => {
        complaintForTutorComponentFixture.detectChanges();
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

        const resolveStub = jest.spyOn(injectedComplaintResponseService, 'resolveComplaint').mockReturnValue(
            of(
                new HttpResponse({
                    body: freshlyCreatedComplaintResponse,
                    status: 201,
                }),
            ),
        );

        complaintForTutorComponentFixture.detectChanges();

        const rejectComplaintButton = complaintForTutorComponentFixture.debugElement.query(By.css('#rejectComplaintButton')).nativeElement;
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

        jest.spyOn(injectedComplaintResponseService, 'refreshLock').mockReturnValue(
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
        complaintForTutorComponentFixture.detectChanges();
        tick();

        const responseTextArea = complaintForTutorComponentFixture.debugElement.query(By.css('#responseTextArea')).nativeElement;
        responseTextArea.value = 'abcdefghijklmnopqrstuvwxyz';
        expect(responseTextArea.value).toHaveLength(26);
        expect(complaintsForTutorComponent.maxComplaintResponseTextLimit).toBe(26);

        const rejectComplaintButton = complaintForTutorComponentFixture.debugElement.query(By.css('#rejectComplaintButton')).nativeElement;
        const acceptComplaintButton = complaintForTutorComponentFixture.debugElement.query(By.css('#acceptComplaintButton')).nativeElement;
        expect(rejectComplaintButton.disabled).toBeFalse();
        expect(acceptComplaintButton.disabled).toBeFalse();

        responseTextArea.value = responseTextArea.value + 'A';
        expect(responseTextArea.value).toHaveLength(27);

        // Update fixture
        complaintForTutorComponentFixture.detectChanges();
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

        jest.spyOn(injectedComplaintResponseService, 'refreshLock').mockReturnValue(
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
        complaintForTutorComponentFixture.detectChanges();
        tick();

        const responseTextArea = complaintForTutorComponentFixture.debugElement.query(By.css('#responseTextArea')).nativeElement;
        expect(responseTextArea.maxLength).toBe(26);
    }));

    it('should calculate the correct maximum text length for exam exercises', () => {
        exercise.course = undefined;
        exercise.exerciseGroup = { exam: { course: course } } as ExerciseGroup;

        complaintForTutorComponentFixture.detectChanges();

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
