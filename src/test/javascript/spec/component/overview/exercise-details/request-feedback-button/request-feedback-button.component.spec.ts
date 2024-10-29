import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { Observable, of } from 'rxjs';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { By } from '@angular/platform-browser';
import { DebugElement } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { RequestFeedbackButtonComponent } from 'app/overview/exercise-details/request-feedback-button/request-feedback-button.component';
import { ArtemisTestModule } from '../../../../test.module';
import { MockProfileService } from '../../../../helpers/mocks/service/mock-profile.service';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';

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

    it('should handle errors when requestFeedback fails', fakeAsync(() => {
        setAthenaEnabled(true);
        const participation = {
            id: 1,
            submissions: [{ id: 1, submitted: true }],
            testRun: false,
        } as StudentParticipation;
        const exercise = { id: 1, type: ExerciseType.TEXT, course: undefined, studentParticipations: [participation], allowFeedbackRequests: true } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);
        mockExerciseDetails(exercise);

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
        const exercise = { id: 1, type: ExerciseType.TEXT, course: {}, allowFeedbackRequests: true } as Exercise; // course undefined means exam exercise
        fixture.componentRef.setInput('exercise', exercise);
        mockExerciseDetails(exercise);

        component.ngOnInit();
        tick();
        fixture.detectChanges();

        const button = debugElement.query(By.css('button'));
        expect(button).not.toBeNull();
        expect(button.nativeElement.disabled).toBeTrue();
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
        const exercise = { id: 1, type: ExerciseType.TEXT, course: {}, studentParticipations: undefined, allowFeedbackRequests: true } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);
        mockExerciseDetails(exercise);

        component.ngOnInit();
        tick();
        fixture.detectChanges();

        const button = debugElement.query(By.css('button'));
        expect(button).not.toBeNull();
        expect(button.nativeElement.disabled).toBeTrue();
    }));

    it('should display the correct button label and style when Athena is enabled', fakeAsync(() => {
        setAthenaEnabled(true);
        const participation = {
            id: 1,
            submissions: [{ id: 1, submitted: true }],
        } as StudentParticipation;
        const exercise = { id: 1, type: ExerciseType.TEXT, course: {}, studentParticipations: [participation], allowFeedbackRequests: true } as Exercise;
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
        const exercise = { id: 1, type: ExerciseType.PROGRAMMING, studentParticipations: [participation], course: {}, allowFeedbackRequests: true } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);

        mockExerciseDetails(exercise);

        component.ngOnInit();
        tick();
        fixture.detectChanges();

        jest.spyOn(component, 'requestFeedback');
        jest.spyOn(courseExerciseService, 'requestFeedback').mockReturnValue(
            new Observable((subscriber) => {
                subscriber.next();
                subscriber.complete();
            }),
        );

        const button = debugElement.query(By.css('a'));
        button.nativeElement.click();
        tick();

        expect(component.requestFeedback).toHaveBeenCalled();
    }));

    it('should show an alert when requestFeedback() is called and conditions are not satisfied', fakeAsync(() => {
        setAthenaEnabled(true);

        const exercise = { id: 1, type: ExerciseType.TEXT, course: {}, allowFeedbackRequests: true } as Exercise;
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
        const exercise = { id: 1, type: ExerciseType.TEXT, studentParticipations: [participation], course: {}, allowFeedbackRequests: true } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('isSubmitted', false);
        fixture.componentRef.setInput('isGeneratingFeedback', false);
        mockExerciseDetails(exercise);

        component.ngOnInit();
        tick();
        fixture.detectChanges();

        const button = debugElement.query(By.css('button'));
        expect(button).not.toBeNull();
        expect(button.nativeElement.disabled).toBeTrue();
    }));

    it('should enable the button if latest submission is submitted and feedback is not generating', fakeAsync(() => {
        setAthenaEnabled(true);
        const participation = {
            id: 1,
            submissions: [{ id: 1, submitted: true }],
            testRun: false,
        } as StudentParticipation;
        const exercise = { id: 1, type: ExerciseType.TEXT, course: {}, studentParticipations: [participation], allowFeedbackRequests: true } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('isSubmitted', true);
        fixture.componentRef.setInput('isGeneratingFeedback', false);
        mockExerciseDetails(exercise);

        component.ngOnInit();
        tick();
        fixture.detectChanges();

        const button = debugElement.query(By.css('button'));
        expect(button).not.toBeNull();
        expect(button.nativeElement.disabled).toBeFalse();
    }));
});
