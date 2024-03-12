import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { MockProvider } from 'ng-mocks';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { AthenaService } from 'app/assessment/athena.service';
import { ExerciseFeedbackSuggestionOptionsComponent } from 'app/exercises/shared/feedback-suggestion/exercise-feedback-suggestion-options.component';
import dayjs from 'dayjs/esm';

describe('ExerciseFeedbackSuggestionOptionsComponent', () => {
    let component: ExerciseFeedbackSuggestionOptionsComponent;
    let fixture: ComponentFixture<ExerciseFeedbackSuggestionOptionsComponent>;
    let athenaService: AthenaService;
    const pastDueDate = dayjs().subtract(1, 'hour');
    const futureDueDate = dayjs().add(1, 'hour');

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [ExerciseFeedbackSuggestionOptionsComponent],
            providers: [
                MockProvider(AthenaService, {
                    isEnabled: () => of(true),
                }),
                { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '1' } } } },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseFeedbackSuggestionOptionsComponent);
        component = fixture.componentInstance;
        athenaService = TestBed.inject(AthenaService);
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize with available modules', async () => {
        const modules = ['Module1', 'Module2'];
        jest.spyOn(athenaService, 'getAvailableModules').mockReturnValue(of(modules));
        component.exercise = { type: ExerciseType.TEXT, dueDate: futureDueDate, feedbackSuggestionModule: undefined } as Exercise;

        await component.ngOnInit();

        expect(component.availableAthenaModules).toEqual(modules);
        expect(component.modulesAvailable).toBeTruthy();
    });

    it('should set isAthenaEnabled$ with the result from athenaService', async () => {
        jest.spyOn(athenaService, 'getAvailableModules').mockReturnValue(of());
        jest.spyOn(athenaService, 'isEnabled').mockReturnValue(of(true));
        component.exercise = { type: ExerciseType.TEXT, dueDate: futureDueDate, feedbackSuggestionModule: undefined } as Exercise;

        await component.ngOnInit();

        expect(component.isAthenaEnabled$).toBeDefined();
        component.isAthenaEnabled$.subscribe((result) => {
            expect(result).toBeTrue();
        });
    });

    it('should disable input controls for programming exercises with automatic assessment type or read-only', () => {
        component.exercise = { type: ExerciseType.PROGRAMMING, assessmentType: AssessmentType.AUTOMATIC, dueDate: futureDueDate } as Exercise;

        let result = component.inputControlsDisabled();
        expect(result).toBeTruthy();

        component.exercise.assessmentType = AssessmentType.MANUAL;
        result = component.inputControlsDisabled();
        expect(result).toBeFalsy();

        component.readOnly = true;
        result = component.inputControlsDisabled();
        expect(result).toBeTruthy();
    });

    it('should disable input controls if due date has passed', () => {
        component.exercise = { type: ExerciseType.TEXT, dueDate: pastDueDate } as Exercise;

        const result = component.inputControlsDisabled();
        expect(result).toBeTruthy();
    });

    it('should return grey color for checkbox label style for automatic programming exercises', () => {
        component.exercise = { type: ExerciseType.PROGRAMMING, assessmentType: AssessmentType.AUTOMATIC, dueDate: futureDueDate } as Exercise;

        const style = component.getCheckboxLabelStyle();

        expect(style).toEqual({ color: 'grey' });
    });

    it('should return an empty object for checkbox label style for non-automatic programming exercises', () => {
        component.exercise = { type: ExerciseType.PROGRAMMING, assessmentType: AssessmentType.MANUAL, dueDate: futureDueDate } as Exercise;

        const style = component.getCheckboxLabelStyle();

        expect(style).toEqual({});
    });

    it('should toggle feedback suggestions and set the module for programming exercises', () => {
        const modules = ['Module1', 'Module2'];
        component.availableAthenaModules = modules;
        component.exercise = { type: ExerciseType.PROGRAMMING } as Exercise;

        const event = { target: { checked: true } };
        component.toggleFeedbackSuggestions(event);

        expect(component.exercise.feedbackSuggestionModule).toBe('Module1');

        event.target.checked = false;
        component.toggleFeedbackSuggestions(event);

        expect(component.exercise.feedbackSuggestionModule).toBeUndefined();
    });
});
