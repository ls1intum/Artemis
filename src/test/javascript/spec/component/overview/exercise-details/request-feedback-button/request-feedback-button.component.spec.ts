import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { of, Observable } from 'rxjs';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { By } from '@angular/platform-browser';
import { DebugElement } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import dayjs from 'dayjs/esm';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { RequestFeedbackButtonComponent } from 'app/overview/exercise-details/request-feedback-button/request-feedback-button.component';
import { ArtemisTestModule } from '../../../../test.module';
import { ExerciseDetailsStudentActionsComponent } from 'app/overview/exercise-details/exercise-details-student-actions.component';
import { ExerciseActionButtonComponent } from 'app/shared/components/exercise-action-button.component';
import { CodeButtonComponent } from 'app/shared/components/code-button/code-button.component';
import { StartPracticeModeButtonComponent } from 'app/shared/components/start-practice-mode-button/start-practice-mode-button.component';
import { ExtensionPointDirective } from 'app/shared/extension-point/extension-point.directive';
import { MockRouterLinkDirective } from '../../../../helpers/mocks/directive/mock-router-link.directive';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { MockCourseExerciseService } from '../../../../helpers/mocks/service/mock-course-exercise.service';
import { Router } from '@angular/router';
import { MockRouter } from '../../../../helpers/mocks/mock-router';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../../../helpers/mocks/service/mock-sync-storage.service';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { PasswordComponent } from 'app/account/password/password.component';
import { MockProfileService } from '../../../../helpers/mocks/service/mock-profile.service';
import { MockExerciseService } from '../../../../helpers/mocks/service/mock-exercise.service';

describe('RequestFeedbackButtonComponent', () => {
    let component: RequestFeedbackButtonComponent;
    let fixture: ComponentFixture<RequestFeedbackButtonComponent>;
    let debugElement: DebugElement;
    let profileService: ProfileService;
    let alertService: AlertService;
    let courseExerciseService: CourseExerciseService;
    let exerciseService: ExerciseService;
    let participationService: ParticipationService;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RequestFeedbackButtonComponent],
            providers: [{ provide: ProfileService, useClass: MockProfileService }, MockProvider(HttpClient)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(RequestFeedbackButtonComponent);
                component = fixture.componentInstance;
                debugElement = fixture.debugElement;
                courseExerciseService = debugElement.injector.get(CourseExerciseService);
                exerciseService = debugElement.injector.get(ExerciseService);
                participationService = debugElement.injector.get(ParticipationService);
                profileService = debugElement.injector.get(ProfileService);
                alertService = debugElement.injector.get(AlertService);
            });
    });

    function setAthenaEnabled(enabled: boolean) {
        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(of({ activeProfiles: enabled ? ['athena'] : [] }));
    }

    function mockExerciseDetails(exercise: Exercise) {
        jest.spyOn(exerciseService, 'getExerciseDetails').mockReturnValue(of(new HttpResponse({ body: { exercise: exercise } })));
    }

    it('should handle errors when requestFeedback fails', fakeAsync(() => {
        setAthenaEnabled(true);
        const participation = {
            id: 1,
            submissions: [{ id: 1, submitted: true }],
            testRun: false,
        } as StudentParticipation;
        const exercise = { id: 1, type: ExerciseType.TEXT, course: undefined, studentParticipations: [participation] } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);
        mockExerciseDetails(exercise);

        jest.spyOn(component, 'assureConditionsSatisfied').mockReturnValue(true);
        jest.spyOn(courseExerciseService, 'requestFeedback').mockReturnValue(
            new Observable((subscriber) => {
                subscriber.error({ error: { errorKey: 'someError' } });
            }),
        );
        jest.spyOn(alertService, 'error');

        component.requestFeedback();
        tick();

        expect(alertService.error).toHaveBeenCalledWith('artemisApp.exercise.someError');
    }));

    it('should display the button when Athena is enabled and it is not an exam exercise', fakeAsync(() => {
        setAthenaEnabled(true);
        const exercise = { id: 1, type: ExerciseType.TEXT, course: {} } as Exercise; // course undefined means exam exercise
        fixture.componentRef.setInput('exercise', exercise);
        mockExerciseDetails(exercise);

        component.ngOnInit();
        tick();
        fixture.detectChanges();

        const button = debugElement.query(By.css('button'));
        expect(button).not.toBeNull();
        expect(button.nativeElement.disabled).toBe(true);
    }));

    it('should not display the button when it is an exam exercise', fakeAsync(() => {
        setAthenaEnabled(true);
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.TEXT, course: undefined } as Exercise);

        component.ngOnInit();
        tick();
        fixture.detectChanges();

        const button = debugElement.query(By.css('button'));
        const link = debugElement.query(By.css('a'));
        expect(button).toBeNull();
        expect(link).toBeNull();
    }));

    it('should disable the button when participation is missing', fakeAsync(() => {
        setAthenaEnabled(true);
        const exercise = { id: 1, type: ExerciseType.TEXT, course: {}, studentParticipations: undefined } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);
        mockExerciseDetails(exercise);

        component.ngOnInit();
        tick();
        fixture.detectChanges();

        const button = debugElement.query(By.css('button'));
        expect(button).not.toBeNull();
        expect(button.nativeElement.disabled).toBe(true);
    }));

    it('should display the correct button label and style when Athena is enabled', fakeAsync(() => {
        setAthenaEnabled(true);
        const participation = {
            id: 1,
            submissions: [{ id: 1, submitted: true }],
        } as StudentParticipation;
        const exercise = { id: 1, type: ExerciseType.TEXT, course: {}, studentParticipations: [participation] } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);
        component.isExamExercise = false;
        mockExerciseDetails(exercise);

        component.ngOnInit();
        tick();
        fixture.detectChanges();

        const button = debugElement.query(By.css('button'));
        expect(button).not.toBeNull();

        const span = button.query(By.css('span'));
        expect(span.nativeElement.textContent).toContain('artemisApp.exerciseActions.requestAutomaticFeedback');
    }));

    it('should call requestFeedback() when button is clicked', fakeAsync(() => {
        setAthenaEnabled(true);
        const participation = {
            id: 1,
            submissions: [{ id: 1, submitted: false }],
            testRun: false,
        } as StudentParticipation;
        const exercise = { id: 1, type: ExerciseType.PROGRAMMING, studentParticipations: [participation], course: {} } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);

        mockExerciseDetails(exercise);

        component.ngOnInit();
        tick();
        fixture.detectChanges();

        jest.spyOn(component, 'requestFeedback');
        jest.spyOn(window, 'confirm').mockReturnValue(false);

        const button = debugElement.query(By.css('a'));
        button.nativeElement.click();
        tick();

        expect(component.requestFeedback).toHaveBeenCalled();
    }));

    it('should not proceed when confirmation is cancelled for programming exercise', fakeAsync(() => {
        setAthenaEnabled(false);
        const participation = {
            id: 1,
            submissions: [{ id: 1, submitted: false }],
            testRun: false,
        } as StudentParticipation;
        const exercise = { id: 1, type: ExerciseType.PROGRAMMING, studentParticipations: [participation], course: {} } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);
        mockExerciseDetails(exercise);

        component.ngOnInit();
        tick();
        fixture.detectChanges();

        jest.spyOn(component, 'assureConditionsSatisfied').mockReturnValue(true);
        jest.spyOn(window, 'confirm').mockReturnValue(false);
        jest.spyOn(courseExerciseService, 'requestFeedback');

        component.requestFeedback();
        tick();

        expect(window.confirm).toHaveBeenCalled();
        expect(courseExerciseService.requestFeedback).not.toHaveBeenCalled();
    }));

    it('should proceed when confirmation is accepted for programming exercise', fakeAsync(() => {
        setAthenaEnabled(false);
        const participation = {
            id: 1,
            submissions: [{ id: 1, submitted: false }],
            testRun: false,
        } as StudentParticipation;
        const exercise = { id: 1, type: ExerciseType.PROGRAMMING, studentParticipations: [participation], course: {} } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);

        mockExerciseDetails(exercise);

        component.ngOnInit();
        tick();
        fixture.detectChanges();

        jest.spyOn(component, 'assureConditionsSatisfied').mockReturnValue(true);
        jest.spyOn(window, 'confirm').mockReturnValue(true);
        jest.spyOn(courseExerciseService, 'requestFeedback').mockReturnValue(of(participation));
        jest.spyOn(alertService, 'success');

        component.requestFeedback();
        tick();

        expect(window.confirm).toHaveBeenCalled();
        expect(courseExerciseService.requestFeedback).toHaveBeenCalledWith(exercise.id);
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.exercise.feedbackRequestSent');
    }));

    it('should show an alert when requestFeedback() is called and conditions are not satisfied', fakeAsync(() => {
        setAthenaEnabled(true);

        const exercise = { id: 1, type: ExerciseType.TEXT, course: {} } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);

        jest.spyOn(component, 'hasAthenaResultForLatestSubmission').mockReturnValue(true);
        jest.spyOn(alertService, 'warning');

        component.requestFeedback();

        expect(alertService.warning).toHaveBeenCalled();
    }));

    it('should disable the button if latest submission is not submitted or feedback is generating', fakeAsync(() => {
        setAthenaEnabled(true);
        const participation = {
            id: 1,
            submissions: [{ id: 1, submitted: false }],
            testRun: false,
        } as StudentParticipation;
        const exercise = { id: 1, type: ExerciseType.TEXT, studentParticipations: [participation], course: {} } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('isGeneratingFeedback', false);
        mockExerciseDetails(exercise);

        component.ngOnInit();
        tick();
        fixture.detectChanges();

        const button = debugElement.query(By.css('button'));
        expect(button).not.toBeNull();
        expect(button.nativeElement.disabled).toBe(true);
    }));

    it('should enable the button if latest submission is submitted and feedback is not generating', fakeAsync(() => {
        setAthenaEnabled(true);
        const participation = {
            id: 1,
            submissions: [{ id: 1, submitted: true }],
            testRun: false,
        } as StudentParticipation;
        const exercise = { id: 1, type: ExerciseType.TEXT, course: {}, studentParticipations: [participation] } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('isGeneratingFeedback', false);
        mockExerciseDetails(exercise);

        component.ngOnInit();
        tick();
        fixture.detectChanges();

        const button = debugElement.query(By.css('button'));
        expect(button).not.toBeNull();
        expect(button.nativeElement.disabled).toBe(false);
    }));
});
