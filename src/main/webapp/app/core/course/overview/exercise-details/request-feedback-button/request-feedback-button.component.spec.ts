import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement, TemplateRef } from '@angular/core';
import { PROFILE_ATHENA } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { BehaviorSubject, Observable, of, throwError } from 'rxjs';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { By } from '@angular/platform-browser';
import { AlertService } from 'app/shared/service/alert.service';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { RequestFeedbackButtonComponent } from 'app/core/course/overview/exercise-details/request-feedback-button/request-feedback-button.component';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { NgbModal, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { ParticipationWebsocketService } from 'app/core/course/shared/services/participation-websocket.service';
import { MockParticipationWebsocketService } from 'test/helpers/mocks/service/mock-participation-websocket.service';
import { UserService } from 'app/core/user/shared/user.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';

describe('RequestFeedbackButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let component: RequestFeedbackButtonComponent;
    let fixture: ComponentFixture<RequestFeedbackButtonComponent>;
    let debugElement: DebugElement;
    let profileService: ProfileService;
    let alertService: AlertService;
    let courseExerciseService: CourseExerciseService;
    let exerciseService: ExerciseService;
    let userService: UserService;
    let accountService: AccountService;
    let participationWebsocketService: ParticipationWebsocketService;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [RequestFeedbackButtonComponent, NgbTooltipModule],
            providers: [
                { provide: ProfileService, useClass: MockProfileService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                provideHttpClient(),
            ],
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(RequestFeedbackButtonComponent);
        component = fixture.componentInstance;
        debugElement = fixture.debugElement;
        courseExerciseService = TestBed.inject(CourseExerciseService);
        exerciseService = TestBed.inject(ExerciseService);
        profileService = TestBed.inject(ProfileService);
        alertService = TestBed.inject(AlertService);
        userService = TestBed.inject(UserService);
        accountService = TestBed.inject(AccountService);
        participationWebsocketService = TestBed.inject(ParticipationWebsocketService);
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    function setAthenaEnabled(enabled: boolean) {
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeProfiles: enabled ? [PROFILE_ATHENA] : [] } as ProfileInfo);
    }

    function mockExerciseDetails(exercise: Exercise) {
        vi.spyOn(exerciseService, 'getExerciseDetails').mockReturnValue(of(new HttpResponse({ body: { exercise: exercise } })));
    }

    async function initAndTick() {
        component.ngOnInit();
        await vi.advanceTimersByTimeAsync(0);
        fixture.detectChanges();
    }

    function createBaseExercise(type: ExerciseType, isExam = false, participation?: StudentParticipation): Exercise {
        return {
            id: 1,
            type,
            course: isExam ? undefined : {},
            studentParticipations: participation ? [participation] : undefined,
            allowFeedbackRequests: true,
        } as Exercise;
    }

    function createParticipation(submitted = true): StudentParticipation {
        return {
            id: 1,
            submissions: [{ id: 1, submitted }],
            testRun: false,
        } as StudentParticipation;
    }

    function setupComponentInputs(exercise: Exercise, isSubmitted?: boolean, isGeneratingFeedback?: boolean) {
        fixture.componentRef.setInput('exercise', exercise);
        if (isSubmitted !== undefined) {
            fixture.componentRef.setInput('isSubmitted', isSubmitted);
        }
        if (isGeneratingFeedback !== undefined) {
            fixture.componentRef.setInput('isGeneratingFeedback', isGeneratingFeedback);
        }
        mockExerciseDetails(exercise);
    }

    it('should handle errors when requestAIFeedback fails', async () => {
        vi.useFakeTimers();
        setAthenaEnabled(true);
        const participation = createParticipation();
        const exercise = createBaseExercise(ExerciseType.TEXT, true, participation);
        setupComponentInputs(exercise);
        component.hasUserAcceptedExternalLLMUsage = true;

        vi.spyOn(courseExerciseService, 'requestFeedback').mockReturnValue(
            new Observable<StudentParticipation>((subscriber) => {
                subscriber.error({ error: { errorKey: 'someError' } });
            }),
        );
        vi.spyOn(alertService, 'error');

        const mockTemplateRef = {} as TemplateRef<any>;
        component.requestAIFeedback(mockTemplateRef);
        await vi.advanceTimersByTimeAsync(0);

        expect(alertService.error).toHaveBeenCalledWith('artemisApp.exercise.someError');
    });

    it('should display the button when Athena is enabled and it is not an exam exercise', async () => {
        vi.useFakeTimers();
        setAthenaEnabled(true);
        const exercise = createBaseExercise(ExerciseType.TEXT, false);
        setupComponentInputs(exercise);

        await initAndTick();

        const button = debugElement.query(By.css('button'));
        expect(button).not.toBeNull();
        expect(button.nativeElement.disabled).toBe(true);
    });

    it('should not display the button when it is an exam exercise', async () => {
        vi.useFakeTimers();
        setAthenaEnabled(true);
        const exercise = createBaseExercise(ExerciseType.TEXT, true);
        setupComponentInputs(exercise);

        await initAndTick();

        const button = debugElement.query(By.css('button'));
        const link = debugElement.query(By.css('a'));
        expect(button).toBeNull();
        expect(link).toBeNull();
    });

    it('should disable the button when participation is missing', async () => {
        vi.useFakeTimers();
        setAthenaEnabled(true);
        const exercise = createBaseExercise(ExerciseType.TEXT, false);
        setupComponentInputs(exercise);

        await initAndTick();

        const button = debugElement.query(By.css('button'));
        expect(button).not.toBeNull();
        expect(button.nativeElement.disabled).toBe(true);
    });

    it('should display the correct button label and style when Athena is enabled', async () => {
        vi.useFakeTimers();
        setAthenaEnabled(true);
        const participation = createParticipation();
        const exercise = createBaseExercise(ExerciseType.TEXT, false, participation);
        setupComponentInputs(exercise);
        component.isExamExercise = false;

        await initAndTick();

        const button = debugElement.query(By.css('button'));
        expect(button).not.toBeNull();

        const span = button.query(By.css('span'));
        expect(span.nativeElement.textContent).toContain('artemisApp.exerciseActions.requestAutomaticFeedback');
    });

    it('should call requestAIFeedback() when button is clicked', async () => {
        vi.useFakeTimers();
        setAthenaEnabled(true);
        const participation = createParticipation();
        const exercise = createBaseExercise(ExerciseType.PROGRAMMING, false, participation);
        setupComponentInputs(exercise);
        component.hasUserAcceptedExternalLLMUsage = true;

        await initAndTick();

        vi.spyOn(component, 'requestAIFeedback');
        vi.spyOn(courseExerciseService, 'requestFeedback').mockReturnValue(of({} as StudentParticipation));

        const button = debugElement.query(By.css('button'));
        expect(button).not.toBeNull();
        button.nativeElement.click();
        await vi.advanceTimersByTimeAsync(0);

        expect(component.requestAIFeedback).toHaveBeenCalled();
    });

    it('should show an alert when requestAIFeedback() is called and conditions are not satisfied', async () => {
        vi.useFakeTimers();
        setAthenaEnabled(true);
        const exercise = createBaseExercise(ExerciseType.TEXT, false);
        setupComponentInputs(exercise);
        component.hasUserAcceptedExternalLLMUsage = true;

        vi.spyOn(component, 'hasAthenaResultForLatestSubmission').mockReturnValue(true);
        vi.spyOn(alertService, 'warning');

        component.requestAIFeedback({} as any);

        expect(alertService.warning).toHaveBeenCalled();
    });

    it('should disable the button if latest submission is not submitted or feedback is generating', async () => {
        vi.useFakeTimers();
        setAthenaEnabled(true);
        const participation = createParticipation();
        const exercise = createBaseExercise(ExerciseType.TEXT, false, participation);
        setupComponentInputs(exercise, false, false);

        await initAndTick();

        const button = debugElement.query(By.css('button'));
        expect(button).not.toBeNull();
        expect(button.nativeElement.disabled).toBe(true);
    });

    it('should enable the button if latest submission is submitted and feedback is not generating', async () => {
        vi.useFakeTimers();
        setAthenaEnabled(true);
        const participation = createParticipation();
        const exercise = createBaseExercise(ExerciseType.TEXT, false, participation);
        setupComponentInputs(exercise, true, false);

        await initAndTick();

        const button = debugElement.query(By.css('button'));
        expect(button).not.toBeNull();
        expect(button.nativeElement.disabled).toBe(false);
    });

    it('should open modal when hasUserAcceptedExternalLLMUsage is false and requestAIFeedback is clicked', async () => {
        vi.useFakeTimers();
        setAthenaEnabled(true);
        const participation = createParticipation();
        const exercise = createBaseExercise(ExerciseType.TEXT, false, participation);
        setupComponentInputs(exercise, true, false);
        component.hasUserAcceptedExternalLLMUsage = false;

        const modalService = TestBed.inject(NgbModal);
        const modalSpy = vi.spyOn(modalService, 'open').mockReturnValue({} as any);

        await initAndTick();

        const button = debugElement.query(By.css('button'));
        expect(button).not.toBeNull();
        button.nativeElement.click();
        await vi.advanceTimersByTimeAsync(0);

        expect(modalSpy).toHaveBeenCalled();
    });

    it('should not open modal when hasUserAcceptedExternalLLMUsage is true and requestAIFeedback is clicked', async () => {
        vi.useFakeTimers();
        setAthenaEnabled(true);
        const participation = createParticipation();
        const exercise = createBaseExercise(ExerciseType.TEXT, false, participation);
        setupComponentInputs(exercise, true, false);
        component.hasUserAcceptedExternalLLMUsage = true;

        const modalService = TestBed.inject(NgbModal);
        const modalSpy = vi.spyOn(modalService, 'open');
        const processFeedbackSpy = vi.spyOn(courseExerciseService, 'requestFeedback').mockReturnValue(of({} as StudentParticipation));

        const mockTemplateRef = {} as TemplateRef<any>;
        component.requestAIFeedback(mockTemplateRef);
        await vi.advanceTimersByTimeAsync(0);

        expect(modalSpy).not.toHaveBeenCalled();
        expect(processFeedbackSpy).toHaveBeenCalledWith(exercise.id);
    });

    it('should unsubscribe from listeners on destroy', async () => {
        vi.useFakeTimers();
        setAthenaEnabled(true);
        const participation = createParticipation();
        const exercise = createBaseExercise(ExerciseType.TEXT, false, participation);
        setupComponentInputs(exercise);

        await initAndTick();

        // Manually set up subscriptions
        const mockSubscription = { unsubscribe: vi.fn() };
        (component as any).athenaResultUpdateListener = mockSubscription;
        (component as any).acceptSubscription = mockSubscription;

        component.ngOnDestroy();

        expect(mockSubscription.unsubscribe).toHaveBeenCalledTimes(2);
    });

    it('should handle error when getExerciseDetails fails', async () => {
        vi.useFakeTimers();
        setAthenaEnabled(true);
        const exercise = createBaseExercise(ExerciseType.TEXT, false);
        fixture.componentRef.setInput('exercise', exercise);

        const errorResponse = new HttpErrorResponse({
            error: { entityName: 'exercise', errorKey: 'notFound' },
            status: 404,
        });
        vi.spyOn(exerciseService, 'getExerciseDetails').mockReturnValue(throwError(() => errorResponse));
        vi.spyOn(alertService, 'error');

        await initAndTick();

        expect(alertService.error).toHaveBeenCalledWith('artemisApp.exercise.errors.notFound');
    });

    it('should set hasUserAcceptedExternalLLMUsage based on account service', () => {
        vi.spyOn(accountService, 'userIdentity').mockReturnValue({ externalLLMUsageAccepted: true } as any);

        component.setUserAcceptedExternalLLMUsage();

        expect(component.hasUserAcceptedExternalLLMUsage).toBe(true);
    });

    it('should set hasUserAcceptedExternalLLMUsage to false when user identity is undefined', () => {
        vi.spyOn(accountService, 'userIdentity').mockReturnValue(undefined);

        component.setUserAcceptedExternalLLMUsage();

        expect(component.hasUserAcceptedExternalLLMUsage).toBe(false);
    });

    it('should accept external LLM usage and close modal', async () => {
        vi.useFakeTimers();
        setAthenaEnabled(true);
        const participation = createParticipation();
        const exercise = createBaseExercise(ExerciseType.PROGRAMMING, false, participation);
        setupComponentInputs(exercise);
        await initAndTick();
        component.hasUserAcceptedExternalLLMUsage = false;

        const mockModal = { close: vi.fn() };
        vi.spyOn(userService, 'updateExternalLLMUsageConsent').mockReturnValue(of({}));
        vi.spyOn(accountService, 'setUserAcceptedExternalLLMUsage');
        vi.spyOn(courseExerciseService, 'requestFeedback').mockReturnValue(of({} as StudentParticipation));

        component.acceptExternalLLMUsage(mockModal);
        await vi.advanceTimersByTimeAsync(0);

        expect(userService.updateExternalLLMUsageConsent).toHaveBeenCalledWith(true);
        expect(component.hasUserAcceptedExternalLLMUsage).toBe(true);
        expect(accountService.setUserAcceptedExternalLLMUsage).toHaveBeenCalled();
        expect(mockModal.close).toHaveBeenCalled();
    });

    it('should handle Athena assessment result and increment feedback count', async () => {
        vi.useFakeTimers();
        setAthenaEnabled(true);
        const participation = createParticipation();
        const exercise = createBaseExercise(ExerciseType.TEXT, false, participation);
        setupComponentInputs(exercise);

        await initAndTick();

        const initialCount = component.currentFeedbackRequestCount;

        // Simulate receiving an Athena assessment result
        const athenaResult: Result = {
            id: 1,
            assessmentType: AssessmentType.AUTOMATIC_ATHENA,
            completionDate: new Date(),
            successful: true,
        } as Result;

        // Call the private method directly
        (component as any).handleAthenaAssessment(athenaResult);

        expect(component.currentFeedbackRequestCount).toBe(initialCount + 1);
    });

    it('should not increment feedback count for unsuccessful Athena assessment', async () => {
        vi.useFakeTimers();
        setAthenaEnabled(true);
        const participation = createParticipation();
        const exercise = createBaseExercise(ExerciseType.TEXT, false, participation);
        setupComponentInputs(exercise);

        await initAndTick();

        const initialCount = component.currentFeedbackRequestCount;

        const athenaResult: Result = {
            id: 1,
            assessmentType: AssessmentType.AUTOMATIC_ATHENA,
            completionDate: new Date(),
            successful: false,
        } as Result;

        (component as any).handleAthenaAssessment(athenaResult);

        expect(component.currentFeedbackRequestCount).toBe(initialCount);
    });

    it('should subscribe to result updates when participation has id', async () => {
        vi.useFakeTimers();
        setAthenaEnabled(true);
        const participation = createParticipation();
        participation.id = 123;
        const exercise = createBaseExercise(ExerciseType.TEXT, false, participation);
        setupComponentInputs(exercise);

        const resultSubject = new BehaviorSubject<Result | undefined>(undefined);
        vi.spyOn(participationWebsocketService, 'subscribeForLatestResultOfParticipation').mockReturnValue(resultSubject);

        await initAndTick();

        expect(participationWebsocketService.subscribeForLatestResultOfParticipation).toHaveBeenCalled();
    });

    it('should return true for programming exercises in assureConditionsSatisfied', () => {
        const exercise = createBaseExercise(ExerciseType.PROGRAMMING, false);
        fixture.componentRef.setInput('exercise', exercise);

        const result = component.assureConditionsSatisfied();

        expect(result).toBe(true);
    });

    it('should show warning for pending changes in text exercises', () => {
        vi.useFakeTimers();
        setAthenaEnabled(true);
        const exercise = createBaseExercise(ExerciseType.TEXT, false);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('pendingChanges', true);

        vi.spyOn(alertService, 'warning');

        const result = component.assureTextModelingConditions();

        expect(result).toBe(false);
        expect(alertService.warning).toHaveBeenCalled();
    });

    it('should show link button when Athena is disabled', async () => {
        vi.useFakeTimers();
        setAthenaEnabled(false);
        const participation = createParticipation();
        const exercise = createBaseExercise(ExerciseType.TEXT, false, participation);
        setupComponentInputs(exercise);

        await initAndTick();

        const link = debugElement.query(By.css('a.btn'));
        expect(link).not.toBeNull();
    });

    it('should call requestFeedback when link is clicked with Athena disabled', async () => {
        vi.useFakeTimers();
        setAthenaEnabled(false);
        const participation = createParticipation();
        const exercise = createBaseExercise(ExerciseType.TEXT, false, participation);
        setupComponentInputs(exercise);

        vi.spyOn(courseExerciseService, 'requestFeedback').mockReturnValue(of({} as StudentParticipation));

        await initAndTick();

        const link = debugElement.query(By.css('a.btn'));
        expect(link).not.toBeNull();

        vi.spyOn(component, 'requestFeedback');
        link.nativeElement.click();
        await vi.advanceTimersByTimeAsync(0);

        expect(component.requestFeedback).toHaveBeenCalled();
    });

    it('should return early from ngOnInit if exercise has no id', async () => {
        vi.useFakeTimers();
        setAthenaEnabled(true);
        const exercise = { type: ExerciseType.TEXT, course: {} } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);

        vi.spyOn(exerciseService, 'getExerciseDetails');

        await initAndTick();

        expect(exerciseService.getExerciseDetails).not.toHaveBeenCalled();
    });

    it('should emit generatingFeedback event when feedback request succeeds', async () => {
        vi.useFakeTimers();
        setAthenaEnabled(true);
        const participation = createParticipation();
        const exercise = createBaseExercise(ExerciseType.PROGRAMMING, false, participation);
        setupComponentInputs(exercise);
        component.hasUserAcceptedExternalLLMUsage = true;

        const generatingFeedbackSpy = vi.fn();
        component.generatingFeedback.subscribe(generatingFeedbackSpy);

        vi.spyOn(courseExerciseService, 'requestFeedback').mockReturnValue(of(participation));
        vi.spyOn(alertService, 'success');

        const mockTemplateRef = {} as TemplateRef<any>;
        component.requestAIFeedback(mockTemplateRef);
        await vi.advanceTimersByTimeAsync(0);

        expect(generatingFeedbackSpy).toHaveBeenCalled();
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.exercise.feedbackRequestSent');
    });

    it('should display programming exercise button without disabled attribute', async () => {
        vi.useFakeTimers();
        setAthenaEnabled(true);
        const participation = createParticipation();
        const exercise = createBaseExercise(ExerciseType.PROGRAMMING, false, participation);
        setupComponentInputs(exercise);

        await initAndTick();

        const button = debugElement.query(By.css('button'));
        expect(button).not.toBeNull();
        // Programming exercises don't have the disabled attribute based on isSubmitted
    });

    it('should display modeling exercise button with correct disabled logic', async () => {
        vi.useFakeTimers();
        setAthenaEnabled(true);
        const participation = createParticipation();
        const exercise = createBaseExercise(ExerciseType.MODELING, false, participation);
        setupComponentInputs(exercise, true, false);

        await initAndTick();

        const button = debugElement.query(By.css('button'));
        expect(button).not.toBeNull();
        expect(button.nativeElement.disabled).toBe(false);
    });

    it('should count existing Athena results in participation', async () => {
        vi.useFakeTimers();
        setAthenaEnabled(true);
        const participation: StudentParticipation = {
            id: 1,
            submissions: [
                {
                    id: 1,
                    submitted: true,
                    results: [
                        { id: 1, assessmentType: AssessmentType.AUTOMATIC_ATHENA, successful: true } as Result,
                        { id: 2, assessmentType: AssessmentType.AUTOMATIC_ATHENA, successful: true } as Result,
                    ],
                },
            ],
            testRun: false,
        } as StudentParticipation;
        const exercise = createBaseExercise(ExerciseType.TEXT, false, participation);
        setupComponentInputs(exercise);

        await initAndTick();

        expect(component.currentFeedbackRequestCount).toBe(2);
    });

    it('should not subscribe to result updates when participation has no id', async () => {
        vi.useFakeTimers();
        setAthenaEnabled(true);
        const participation = createParticipation();
        participation.id = undefined as any;
        const exercise = createBaseExercise(ExerciseType.TEXT, false, participation);

        vi.spyOn(exerciseService, 'getExerciseDetails').mockReturnValue(
            of(
                new HttpResponse({
                    body: {
                        exercise: {
                            ...exercise,
                            studentParticipations: [{ ...participation, id: undefined }],
                        },
                    },
                }),
            ),
        );
        fixture.componentRef.setInput('exercise', exercise);

        vi.spyOn(participationWebsocketService, 'subscribeForLatestResultOfParticipation');

        await initAndTick();

        expect(participationWebsocketService.subscribeForLatestResultOfParticipation).not.toHaveBeenCalled();
    });
});
