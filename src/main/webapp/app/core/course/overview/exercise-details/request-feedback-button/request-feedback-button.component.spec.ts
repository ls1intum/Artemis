import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { PROFILE_ATHENA } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Observable, of } from 'rxjs';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { By } from '@angular/platform-browser';
import { AlertService } from 'app/shared/service/alert.service';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { RequestFeedbackButtonComponent } from 'app/core/course/overview/exercise-details/request-feedback-button/request-feedback-button.component';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { NgbModal, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { LLMSelectionDecision } from 'app/core/user/shared/dto/updateLLMSelectionDecision.dto';

describe('RequestFeedbackButtonComponent', () => {
    let component: RequestFeedbackButtonComponent;
    let fixture: ComponentFixture<RequestFeedbackButtonComponent>;
    let debugElement: DebugElement;
    let profileService: ProfileService;
    let alertService: AlertService;
    let courseExerciseService: CourseExerciseService;
    let exerciseService: ExerciseService;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [RequestFeedbackButtonComponent, NgbTooltipModule],
            providers: [
                { provide: ProfileService, useClass: MockProfileService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(RequestFeedbackButtonComponent);
                component = fixture.componentInstance;
                debugElement = fixture.debugElement;
                courseExerciseService = TestBed.inject(CourseExerciseService);
                exerciseService = TestBed.inject(ExerciseService);
                profileService = TestBed.inject(ProfileService);
                alertService = TestBed.inject(AlertService);
            });
    });

    function setAthenaEnabled(enabled: boolean) {
        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeProfiles: enabled ? [PROFILE_ATHENA] : [] } as ProfileInfo);
    }

    function mockExerciseDetails(exercise: Exercise) {
        jest.spyOn(exerciseService, 'getExerciseDetails').mockReturnValue(of(new HttpResponse({ body: { exercise: exercise } })));
    }

    function initAndTick() {
        component.ngOnInit();
        tick();
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

    it('should handle errors when requestAIFeedback fails', fakeAsync(() => {
        setAthenaEnabled(true);
        const participation = createParticipation();
        const exercise = createBaseExercise(ExerciseType.TEXT, true, participation);
        setupComponentInputs(exercise);
        component.hasUserAcceptedLLMUsage = true;

        jest.spyOn(courseExerciseService, 'requestFeedback').mockReturnValue(
            new Observable<StudentParticipation>((subscriber) => {
                subscriber.error({ error: { errorKey: 'someError' } });
            }),
        );
        jest.spyOn(alertService, 'error');
        component.requestAIFeedback();
        tick();

        expect(alertService.error).toHaveBeenCalledWith('artemisApp.exercise.someError');
    }));

    it('should display the button when Athena is enabled and it is not an exam exercise', fakeAsync(() => {
        setAthenaEnabled(true);
        const exercise = createBaseExercise(ExerciseType.TEXT, false);
        setupComponentInputs(exercise);

        initAndTick();

        const button = debugElement.query(By.css('button'));
        expect(button).not.toBeNull();
        expect(button.nativeElement.disabled).toBeTrue();
    }));

    it('should not display the button when it is an exam exercise', fakeAsync(() => {
        setAthenaEnabled(true);
        const exercise = createBaseExercise(ExerciseType.TEXT, true);
        setupComponentInputs(exercise);

        initAndTick();

        const button = debugElement.query(By.css('button'));
        const link = debugElement.query(By.css('a'));
        expect(button).toBeNull();
        expect(link).toBeNull();
    }));

    it('should disable the button when participation is missing', fakeAsync(() => {
        setAthenaEnabled(true);
        const exercise = createBaseExercise(ExerciseType.TEXT, false);
        setupComponentInputs(exercise);

        initAndTick();

        const button = debugElement.query(By.css('button'));
        expect(button).not.toBeNull();
        expect(button.nativeElement.disabled).toBeTrue();
    }));

    it('should display the correct button label and style when Athena is enabled', fakeAsync(() => {
        setAthenaEnabled(true);
        const participation = createParticipation();
        const exercise = createBaseExercise(ExerciseType.TEXT, false, participation);
        setupComponentInputs(exercise);
        component.isExamExercise = false;

        initAndTick();

        const button = debugElement.query(By.css('button'));
        expect(button).not.toBeNull();

        const span = button.query(By.css('span'));
        expect(span.nativeElement.textContent).toContain('artemisApp.exerciseActions.requestAutomaticFeedback');
    }));

    it('should call requestAIFeedback() when button is clicked', fakeAsync(() => {
        setAthenaEnabled(true);
        const participation = createParticipation();
        const exercise = createBaseExercise(ExerciseType.PROGRAMMING, false, participation);
        setupComponentInputs(exercise);
        component.hasUserAcceptedLLMUsage = true;

        initAndTick();

        jest.spyOn(component, 'requestAIFeedback');
        jest.spyOn(courseExerciseService, 'requestFeedback').mockReturnValue(of({} as StudentParticipation));

        const button = debugElement.query(By.css('button'));
        expect(button).not.toBeNull();
        button.nativeElement.click();
        tick();

        expect(component.requestAIFeedback).toHaveBeenCalled();
    }));

    it('should show an alert when requestAIFeedback() is called and conditions are not satisfied', fakeAsync(() => {
        setAthenaEnabled(true);
        const exercise = createBaseExercise(ExerciseType.TEXT, false);
        setupComponentInputs(exercise);
        component.hasUserAcceptedLLMUsage = true;

        jest.spyOn(component, 'hasAthenaResultForLatestSubmission').mockReturnValue(true);
        jest.spyOn(alertService, 'warning');

        component.requestAIFeedback();
    }));

    it('should disable the button if latest submission is not submitted or feedback is generating', fakeAsync(() => {
        setAthenaEnabled(true);
        const participation = createParticipation();
        const exercise = createBaseExercise(ExerciseType.TEXT, false, participation);
        setupComponentInputs(exercise, false, false);

        initAndTick();

        const button = debugElement.query(By.css('button'));
        expect(button).not.toBeNull();
        expect(button.nativeElement.disabled).toBeTrue();
    }));

    it('should enable the button if latest submission is submitted and feedback is not generating', fakeAsync(() => {
        setAthenaEnabled(true);
        const participation = createParticipation();
        const exercise = createBaseExercise(ExerciseType.TEXT, false, participation);
        setupComponentInputs(exercise, true, false);

        initAndTick();

        const button = debugElement.query(By.css('button'));
        expect(button).not.toBeNull();
        expect(button.nativeElement.disabled).toBeFalse();
    }));

    it('should not open modal when hasUserAcceptedLLMUsage is true and requestAIFeedback is clicked', fakeAsync(() => {
        setAthenaEnabled(true);
        const participation = createParticipation();
        const exercise = createBaseExercise(ExerciseType.TEXT, false, participation);
        setupComponentInputs(exercise, true, false);
        component.hasUserAcceptedLLMUsage = true;

        // Set up spies
        const modalService = TestBed.inject(NgbModal);
        const modalSpy = jest.spyOn(modalService, 'open');
        const processFeedbackSpy = jest.spyOn(courseExerciseService, 'requestFeedback').mockReturnValue(of({} as StudentParticipation));

        component.requestAIFeedback();
        tick();

        expect(modalSpy).not.toHaveBeenCalled();
        expect(processFeedbackSpy).toHaveBeenCalledWith(exercise.id);
    }));

    describe('showLLMSelectionModal', () => {
        it('should call acceptLLMUsage with CLOUD_AI when cloud is chosen', async () => {
            jest.spyOn(component['llmModalService'], 'open').mockResolvedValue('cloud');
            const acceptSpy = jest.spyOn(component, 'acceptLLMUsage');

            await component.showLLMSelectionModal();

            expect(acceptSpy).toHaveBeenCalledWith(LLMSelectionDecision.CLOUD_AI);
        });

        it('should call acceptLLMUsage with LOCAL_AI when local is chosen', async () => {
            jest.spyOn(component['llmModalService'], 'open').mockResolvedValue('local');
            const acceptSpy = jest.spyOn(component, 'acceptLLMUsage');

            await component.showLLMSelectionModal();

            expect(acceptSpy).toHaveBeenCalledWith(LLMSelectionDecision.LOCAL_AI);
        });

        it('should call acceptLLMUsage with NO_AI when no_ai is chosen', async () => {
            jest.spyOn(component['llmModalService'], 'open').mockResolvedValue('no_ai');
            const acceptSpy = jest.spyOn(component, 'acceptLLMUsage');

            await component.showLLMSelectionModal();

            expect(acceptSpy).toHaveBeenCalledWith(LLMSelectionDecision.NO_AI);
        });

        it('should not call acceptLLMUsage when none is chosen', async () => {
            jest.spyOn(component['llmModalService'], 'open').mockResolvedValue('none');
            const acceptSpy = jest.spyOn(component, 'acceptLLMUsage');

            await component.showLLMSelectionModal();

            expect(acceptSpy).not.toHaveBeenCalled();
        });

        it('should open the LLM modal', async () => {
            const openSpy = jest.spyOn(component['llmModalService'], 'open').mockResolvedValue('cloud');
            jest.spyOn(component, 'acceptLLMUsage').mockImplementation();

            await component.showLLMSelectionModal();

            expect(openSpy).toHaveBeenCalledOnce();
        });
    });

    describe('acceptLLMUsage', () => {
        let updateLLMDecisionSpy: jest.SpyInstance;

        beforeEach(() => {
            const accountService = component['accountService'];
            if (!accountService.setUserLLMSelectionDecision) {
                accountService.setUserLLMSelectionDecision = jest.fn();
            }

            updateLLMDecisionSpy = jest.spyOn(component['userService'], 'updateLLMSelectionDecision').mockReturnValue(of(undefined as any));
        });

        it('should unsubscribe from previous acceptSubscription if exists', () => {
            const mockSubscription = { unsubscribe: jest.fn() } as any;
            component['acceptSubscription'] = mockSubscription;

            component.acceptLLMUsage(LLMSelectionDecision.CLOUD_AI);

            expect(mockSubscription.unsubscribe).toHaveBeenCalledOnce();
        });

        it('should not throw error if acceptSubscription is undefined', () => {
            component['acceptSubscription'] = undefined;

            expect(() => component.acceptLLMUsage(LLMSelectionDecision.CLOUD_AI)).not.toThrow();
        });

        it('should call updateLLMSelectionDecision with CLOUD_AI', () => {
            component.acceptLLMUsage(LLMSelectionDecision.CLOUD_AI);

            expect(updateLLMDecisionSpy).toHaveBeenCalledWith(LLMSelectionDecision.CLOUD_AI);
        });

        it('should call updateLLMSelectionDecision with LOCAL_AI', () => {
            component.acceptLLMUsage(LLMSelectionDecision.LOCAL_AI);

            expect(updateLLMDecisionSpy).toHaveBeenCalledWith(LLMSelectionDecision.LOCAL_AI);
        });

        it('should call updateLLMSelectionDecision with NO_AI', () => {
            component.acceptLLMUsage(LLMSelectionDecision.NO_AI);

            expect(updateLLMDecisionSpy).toHaveBeenCalledWith(LLMSelectionDecision.NO_AI);
        });

        it('should set hasUserAcceptedLLMUsage to false for NO_AI', fakeAsync(() => {
            component.acceptLLMUsage(LLMSelectionDecision.NO_AI);
            tick();

            expect(component.hasUserAcceptedLLMUsage).toBeFalse();
        }));

        it('should store the subscription in acceptSubscription', () => {
            component.acceptLLMUsage(LLMSelectionDecision.CLOUD_AI);

            expect(component['acceptSubscription']).toBeDefined();
        });
    });

    describe('requestAIFeedback', () => {
        let showModalSpy: jest.SpyInstance;
        let requestFeedbackSpy: jest.SpyInstance;

        beforeEach(() => {
            showModalSpy = jest.spyOn(component, 'showLLMSelectionModal').mockResolvedValue();
            requestFeedbackSpy = jest.spyOn(component, 'requestFeedback').mockImplementation();
        });

        it('should call showLLMSelectionModal when user has not accepted LLM usage', async () => {
            component.hasUserAcceptedLLMUsage = false;

            await component.requestAIFeedback();

            expect(showModalSpy).toHaveBeenCalledOnce();
            expect(requestFeedbackSpy).not.toHaveBeenCalled();
        });

        it('should call requestFeedback when user has accepted LLM usage', async () => {
            component.hasUserAcceptedLLMUsage = true;

            await component.requestAIFeedback();

            expect(showModalSpy).not.toHaveBeenCalled();
            expect(requestFeedbackSpy).toHaveBeenCalledOnce();
        });

        it('should return early after showing modal when user has not accepted', async () => {
            component.hasUserAcceptedLLMUsage = false;

            await component.requestAIFeedback();

            expect(showModalSpy).toHaveBeenCalledOnce();
            expect(requestFeedbackSpy).not.toHaveBeenCalled();
        });

        it('should not show modal when user has already accepted', async () => {
            component.hasUserAcceptedLLMUsage = true;

            await component.requestAIFeedback();

            expect(showModalSpy).not.toHaveBeenCalled();
        });
    });
});
