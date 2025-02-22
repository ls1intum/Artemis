import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { TemplateRef } from '@angular/core';
import { MockProvider } from 'ng-mocks';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { Observable, of } from 'rxjs';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { By } from '@angular/platform-browser';
import { DebugElement } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { RequestFeedbackButtonComponent } from 'app/overview/exercise-details/request-feedback-button/request-feedback-button.component';
import { MockProfileService } from '../../../../helpers/mocks/service/mock-profile.service';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../../helpers/mocks/service/mock-account.service';
import { NgbModal, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';

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
                courseExerciseService = debugElement.injector.get(CourseExerciseService);
                exerciseService = debugElement.injector.get(ExerciseService);
                profileService = debugElement.injector.get(ProfileService);
                alertService = debugElement.injector.get(AlertService);
            });
    });

    function setAthenaEnabled(enabled: boolean) {
        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(of({ activeProfiles: enabled ? ['athena'] : [] } as ProfileInfo));
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
        component.hasUserAcceptedExternalLLMUsage = true;

        jest.spyOn(courseExerciseService, 'requestFeedback').mockReturnValue(
            new Observable<StudentParticipation>((subscriber) => {
                subscriber.error({ error: { errorKey: 'someError' } });
            }),
        );
        jest.spyOn(alertService, 'error');

        // component.requestAIFeedback({} as any);
        const mockTemplateRef = {} as TemplateRef<any>;
        component.requestAIFeedback(mockTemplateRef);
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
        component.hasUserAcceptedExternalLLMUsage = true;

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
        component.hasUserAcceptedExternalLLMUsage = true;

        jest.spyOn(component, 'hasAthenaResultForLatestSubmission').mockReturnValue(true);
        jest.spyOn(alertService, 'warning');

        component.requestAIFeedback({} as any);

        expect(alertService.warning).toHaveBeenCalled();
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

    it('should open modal when hasUserAcceptedExternalLLMUsage is false and requestAIFeedback is clicked', fakeAsync(() => {
        setAthenaEnabled(true);
        const participation = createParticipation();
        const exercise = createBaseExercise(ExerciseType.TEXT, false, participation);
        setupComponentInputs(exercise, true, false);
        component.hasUserAcceptedExternalLLMUsage = false;

        // Set up modal spy
        const modalService = TestBed.inject(NgbModal);
        const modalSpy = jest.spyOn(modalService, 'open').mockReturnValue({} as any);

        initAndTick();

        const button = debugElement.query(By.css('button'));
        expect(button).not.toBeNull();
        button.nativeElement.click();
        tick();

        expect(modalSpy).toHaveBeenCalled();
    }));

    it('should not open modal when hasUserAcceptedExternalLLMUsage is true and requestAIFeedback is clicked', fakeAsync(() => {
        setAthenaEnabled(true);
        const participation = createParticipation();
        const exercise = createBaseExercise(ExerciseType.TEXT, false, participation);
        setupComponentInputs(exercise, true, false);
        component.hasUserAcceptedExternalLLMUsage = true;

        // Set up spies
        const modalService = TestBed.inject(NgbModal);
        const modalSpy = jest.spyOn(modalService, 'open');
        const processFeedbackSpy = jest.spyOn(courseExerciseService, 'requestFeedback').mockReturnValue(of({} as StudentParticipation));

        // Just call requestAIFeedback with an empty template ref object
        const mockTemplateRef = {} as TemplateRef<any>;
        component.requestAIFeedback(mockTemplateRef);
        tick();

        expect(modalSpy).not.toHaveBeenCalled();
        expect(processFeedbackSpy).toHaveBeenCalledWith(exercise.id);
    }));
});
