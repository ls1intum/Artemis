import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { MockProvider } from 'ng-mocks';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { AthenaService } from 'app/assessment/shared/athena.service';
import { ExerciseFeedbackSuggestionOptionsComponent } from 'app/exercise/feedback-suggestion/exercise-feedback-suggestion-options.component';
import dayjs from 'dayjs/esm';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';

describe('ExerciseFeedbackSuggestionOptionsComponent', () => {
    let component: ExerciseFeedbackSuggestionOptionsComponent;
    let fixture: ComponentFixture<ExerciseFeedbackSuggestionOptionsComponent>;
    let athenaService: AthenaService;
    const pastDueDate = dayjs().subtract(1, 'hour');
    const futureDueDate = dayjs().add(1, 'hour');

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                MockProvider(AthenaService, {
                    isEnabled: () => of(true),
                }),
                { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '1' } } } },
                MockDirective(TranslateDirective),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseFeedbackSuggestionOptionsComponent);
        component = fixture.componentInstance;
        athenaService = TestBed.inject(AthenaService);
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

    it('should toggle feedback suggestions and set the module for any exercise type', () => {
        const modules = ['Module1', 'Module2'];
        component.availableAthenaModules = modules;
        component.exercise = { type: ExerciseType.PROGRAMMING } as Exercise;

        expect(component.showDropdownList).toBeFalse();

        const event = { target: { checked: true } };
        component.toggleFeedbackSuggestions(event);

        expect(component.exercise.feedbackSuggestionModule).toBe('Module1');
        expect(component.showDropdownList).toBeTrue();
        expect(component.exercise.allowManualFeedbackRequests).toBeFalse();

        event.target.checked = false;
        component.toggleFeedbackSuggestions(event);

        expect(component.exercise.feedbackSuggestionModule).toBeUndefined();
    });

    it('should hide dropdown and disable checkbox when switching to AUTOMATIC, then, after switching back, the component is in its original state', () => {
        // prepare data
        const modules = ['Module1', 'Module2'];
        jest.spyOn(athenaService, 'getAvailableModules').mockReturnValue(of(modules));
        component.exercise = { type: ExerciseType.PROGRAMMING, dueDate: futureDueDate, assessmentType: AssessmentType.SEMI_AUTOMATIC } as Exercise;

        fixture.detectChanges();

        // assume a module is chosen, hence, the controls are active
        const event = { target: { checked: true } };
        component.toggleFeedbackSuggestions(event);

        expect(component.inputControlsDisabled()).toBeFalse();
        expect(component.showDropdownList).toBeTrue();

        // change assessment type
        component.exercise.assessmentType = AssessmentType.AUTOMATIC;
        fixture.detectChanges();

        // now, the input is unchecked and the controls are disabled
        expect(component.inputControlsDisabled()).toBeTrue();
        expect(component.showDropdownList).toBeFalse();

        let checkbox = fixture.debugElement.query(By.css('#feedbackSuggestionsEnabledCheck'));
        expect(checkbox.nativeElement.disabled).toBeTrue();

        // Now, change the assessment type back to semi automatic
        component.exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        component.exercise.feedbackSuggestionModule = undefined; // will be reset by parent component

        fixture.detectChanges();

        // the controls are enabled, but not checked
        expect(component.inputControlsDisabled()).toBeFalse();
        expect(component.showDropdownList).toBeFalse();

        checkbox = fixture.debugElement.query(By.css('#feedbackSuggestionsEnabledCheck'));
        expect(checkbox.nativeElement.disabled).toBeFalse();
        expect(checkbox.nativeElement.checked).toBeFalse();
    });
});
