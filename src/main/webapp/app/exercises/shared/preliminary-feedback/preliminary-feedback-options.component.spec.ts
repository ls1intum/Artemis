import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { MockProvider } from 'ng-mocks';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AthenaService } from 'app/assessment/shared/services/athena.service';
import dayjs from 'dayjs/esm';
import { ExercisePreliminaryFeedbackOptionsComponent } from './exercise-preliminary-feedback-options.component';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';

describe('ExercisePreliminaryFeedbackOptionsComponent', () => {
    let component: ExercisePreliminaryFeedbackOptionsComponent;
    let fixture: ComponentFixture<ExercisePreliminaryFeedbackOptionsComponent>;
    let athenaService: AthenaService;
    const pastDueDate = dayjs().subtract(1, 'hour');
    const futureDueDate = dayjs().add(1, 'hour');

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                MockProvider(AthenaService, {
                    isEnabled: () => true,
                }),
                { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '1' } } } },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExercisePreliminaryFeedbackOptionsComponent);
        component = fixture.componentInstance;
        athenaService = TestBed.inject(AthenaService);
    });

    it('should initialize with available modules', async () => {
        const modules = ['Module1', 'Module2'];
        jest.spyOn(athenaService, 'getAvailableModules').mockReturnValue(of(modules));
        fixture.componentRef.setInput('exercise', { type: ExerciseType.TEXT, dueDate: futureDueDate, athenaConfig: undefined } as Exercise);

        await component.ngOnInit();

        expect(component.availableAthenaModules).toEqual(modules);
        expect(component.modulesAvailable).toBeTruthy();
    });

    it('should set isAthenaEnabled with the result from athenaService', async () => {
        jest.spyOn(athenaService, 'getAvailableModules').mockReturnValue(of());
        jest.spyOn(athenaService, 'isEnabled').mockReturnValue(true);
        fixture.componentRef.setInput('exercise', { type: ExerciseType.TEXT, dueDate: futureDueDate, athenaConfig: undefined } as Exercise);

        await component.ngOnInit();

        expect(component.isAthenaEnabled).toBeDefined();
        expect(component.isAthenaEnabled).toBeTrue();
    });

    it('should disable input controls for programming exercises with automatic assessment type or read-only', () => {
        fixture.componentRef.setInput('exercise', {
            type: ExerciseType.PROGRAMMING,
            assessmentType: AssessmentType.AUTOMATIC,
            dueDate: pastDueDate,
            athenaConfig: undefined,
        } as Exercise);

        let result = component.inputControlsDisabled();
        expect(result).toBeTruthy();

        fixture.componentRef.setInput('readOnly', true);
        result = component.inputControlsDisabled();
        expect(result).toBeTruthy();
    });

    it('should disable input controls if due date has passed', () => {
        fixture.componentRef.setInput('exercise', { type: ExerciseType.PROGRAMMING, assessmentType: AssessmentType.AUTOMATIC, dueDate: pastDueDate } as Exercise);

        const result = component.inputControlsDisabled();
        expect(result).toBeTruthy();
    });

    it('should return grey color for checkbox label style for automatic programming exercises', () => {
        fixture.componentRef.setInput('exercise', { type: ExerciseType.PROGRAMMING, assessmentType: AssessmentType.AUTOMATIC, dueDate: pastDueDate } as Exercise);

        const style = component.getCheckboxLabelStyle();

        expect(style).toEqual({ color: 'grey' });
    });

    it('should return an empty object for checkbox label style for non-automatic programming exercises', () => {
        fixture.componentRef.setInput('exercise', {
            type: ExerciseType.PROGRAMMING,
            assessmentType: AssessmentType.MANUAL,
            dueDate: futureDueDate,
            athenaConfig: undefined,
        } as Exercise);

        const style = component.getCheckboxLabelStyle();

        expect(style).toEqual({});
    });

    it('should toggle preliminary feedback module and set the module for any exercise', () => {
        component.availableAthenaModules = ['Module1', 'Module2'];
        fixture.componentRef.setInput('exercise', { type: ExerciseType.PROGRAMMING, athenaConfig: undefined } as Exercise);

        expect(component.showDropdownList).toBeFalse();

        const event = { target: { checked: true } };
        component.togglePreliminaryFeedback(event);

        expect(component.exercise().athenaConfig?.preliminaryFeedbackModule).toBe('Module1');
        expect(component.showDropdownList).toBeTrue();

        event.target.checked = false;
        component.togglePreliminaryFeedback(event);

        expect(component.exercise().athenaConfig?.preliminaryFeedbackModule).toBeUndefined();
    });
});
