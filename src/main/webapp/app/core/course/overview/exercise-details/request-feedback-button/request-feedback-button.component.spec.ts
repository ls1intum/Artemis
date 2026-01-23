import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement, TemplateRef } from '@angular/core';
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
import { ParticipationWebsocketService } from 'app/core/course/shared/services/participation-websocket.service';
import { MockParticipationWebsocketService } from 'test/helpers/mocks/service/mock-participation-websocket.service';

describe('RequestFeedbackButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let component: RequestFeedbackButtonComponent;
    let fixture: ComponentFixture<RequestFeedbackButtonComponent>;
    let debugElement: DebugElement;
    let profileService: ProfileService;
    let alertService: AlertService;
    let courseExerciseService: CourseExerciseService;
    let exerciseService: ExerciseService;

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
});
