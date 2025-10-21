import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ExerciseFeedbackSuggestionOptionsComponent } from 'app/exercise/feedback-suggestion/exercise-feedback-suggestion-options.component';
import { AthenaService } from 'app/assessment/shared/services/athena.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { SimpleChange } from '@angular/core';

describe('ExerciseFeedbackSuggestionOptionsComponent', () => {
    let fixture: ComponentFixture<ExerciseFeedbackSuggestionOptionsComponent>;
    let component: ExerciseFeedbackSuggestionOptionsComponent;
    let athenaService: { getAvailableModules: jest.Mock };
    let profileService: { isProfileActive: jest.Mock };

    const courseId = 42;

    beforeAll(() => {
        if (!(Array.prototype as any).first) {
            (Array.prototype as any).first = function <T>(this: T[]) {
                return this[0];
            };
        }
    });

    beforeEach(async () => {
        athenaService = { getAvailableModules: jest.fn() };
        profileService = { isProfileActive: jest.fn() };

        await TestBed.configureTestingModule({
            imports: [ExerciseFeedbackSuggestionOptionsComponent],
            providers: [
                { provide: AthenaService, useValue: athenaService },
                { provide: ProfileService, useValue: profileService },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: {
                            paramMap: {
                                get: (key: string) => (key === 'courseId' ? courseId.toString() : null),
                            },
                        },
                    },
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseFeedbackSuggestionOptionsComponent);
        component = fixture.componentInstance;
        component.exercise = {
            id: 7,
            type: ExerciseType.TEXT,
            assessmentType: AssessmentType.SEMI_AUTOMATIC,
            dueDate: dayjs().add(2, 'day').toISOString(),
            feedbackSuggestionModule: 'initial-module',
        } as Exercise;
    });

    it('should initialize available modules and athena state in ngOnInit', fakeAsync(() => {
        const modules = ['moduleA', 'moduleB'];
        athenaService.getAvailableModules.mockReturnValue(of(modules));
        profileService.isProfileActive.mockReturnValue(true);

        component.ngOnInit();
        tick();

        expect(athenaService.getAvailableModules).toHaveBeenCalledWith(courseId, component.exercise);
        expect(component.availableAthenaModules).toEqual(modules);
        expect(component.modulesAvailable).toBeTrue();
        expect(component.isAthenaEnabled).toBeTrue();
        expect(component.initialAthenaModule).toBe('initial-module');
    }));

    it('should mark modules unavailable and athena disabled when no modules are returned', fakeAsync(() => {
        component.exercise.feedbackSuggestionModule = undefined;
        athenaService.getAvailableModules.mockReturnValue(of([]));
        profileService.isProfileActive.mockReturnValue(false);

        component.ngOnInit();
        tick();

        expect(component.availableAthenaModules).toEqual([]);
        expect(component.modulesAvailable).toBeFalse();
        expect(component.isAthenaEnabled).toBeFalse();
        expect(component.initialAthenaModule).toBeUndefined();
    }));

    it('should restore the initial module when the due date change disables the inputs', () => {
        component.initialAthenaModule = 'initial-module';
        component.availableAthenaModules = ['moduleA'] as any;
        (component.availableAthenaModules as any).first = function () {
            return this[0];
        };

        component.exercise.feedbackSuggestionModule = 'changed-module';
        component.exercise.dueDate = dayjs().subtract(1, 'day').toISOString();
        component.dueDate = dayjs().subtract(1, 'day');

        component.ngOnChanges({
            dueDate: new SimpleChange(dayjs().add(1, 'day'), component.dueDate, false),
        });

        expect(component.exercise.feedbackSuggestionModule).toBe('initial-module');
    });

    it('should keep the selected module when inputs remain enabled after due date change', () => {
        component.initialAthenaModule = 'initial-module';
        component.exercise.feedbackSuggestionModule = 'selected-module';
        component.exercise.dueDate = dayjs().add(1, 'day').toISOString();
        component.dueDate = dayjs().add(1, 'day');

        component.ngOnChanges({
            dueDate: new SimpleChange(dayjs().subtract(2, 'day'), component.dueDate, false),
        });

        expect(component.exercise.feedbackSuggestionModule).toBe('selected-module');
    });

    it('should evaluate disabled state for programming exercises correctly', () => {
        component.exercise = {
            id: 8,
            type: ExerciseType.PROGRAMMING,
            assessmentType: AssessmentType.AUTOMATIC,
            dueDate: dayjs().add(1, 'day').toISOString(),
        } as Exercise;

        expect(component.inputControlsDisabled()).toBeTrue();

        component.exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        component.readOnly = true;
        expect(component.inputControlsDisabled()).toBeTrue();

        component.readOnly = false;
        component.exercise.dueDate = undefined;
        expect(component.inputControlsDisabled()).toBeTrue();

        component.exercise.dueDate = dayjs().add(2, 'day').toISOString();
        expect(component.inputControlsDisabled()).toBeFalse();

        component.exercise.dueDate = dayjs().subtract(1, 'day').toISOString();
        expect(component.inputControlsDisabled()).toBeTrue();
    });

    it('should evaluate disabled state for non-programming exercises based on due date', () => {
        component.exercise = {
            id: 9,
            type: ExerciseType.TEXT,
            assessmentType: AssessmentType.SEMI_AUTOMATIC,
            dueDate: dayjs().add(1, 'day').toISOString(),
        } as Exercise;

        expect(component.inputControlsDisabled()).toBeFalse();

        component.exercise.dueDate = dayjs().subtract(1, 'day').toISOString();
        expect(component.inputControlsDisabled()).toBeTrue();
    });

    it('should return grey label style when inputs are disabled', () => {
        const disabledSpy = jest.spyOn(component, 'inputControlsDisabled');
        disabledSpy.mockReturnValue(true);
        expect(component.getCheckboxLabelStyle()).toEqual({ color: 'grey' });

        disabledSpy.mockReturnValue(false);
        expect(component.getCheckboxLabelStyle()).toEqual({});
    });

    it('should toggle feedback suggestions and update exercise state', () => {
        component.availableAthenaModules = ['moduleA', 'moduleB'] as any;
        (component.availableAthenaModules as any).first = function () {
            return this[0];
        };

        component.exercise.feedbackSuggestionModule = undefined;
        component.exercise.allowFeedbackRequests = true;

        component.toggleFeedbackSuggestions({ target: { checked: true } });
        expect(component.exercise.feedbackSuggestionModule).toBe('moduleA');

        component.toggleFeedbackSuggestions({ target: { checked: false } });
        expect(component.exercise.feedbackSuggestionModule).toBeUndefined();
        expect(component.exercise.allowFeedbackRequests).toBeFalse();
    });

    it('should toggle feedback requests and set module when enabling', () => {
        component.availableAthenaModules = ['moduleA'] as any;
        (component.availableAthenaModules as any).first = function () {
            return this[0];
        };

        component.exercise.feedbackSuggestionModule = undefined;
        component.exercise.allowFeedbackRequests = false;

        component.toggleFeedbackRequests({ target: { checked: true } });
        expect(component.exercise.allowFeedbackRequests).toBeTrue();
        expect(component.exercise.feedbackSuggestionModule).toBe('moduleA');

        component.toggleFeedbackRequests({ target: { checked: false } });
        expect(component.exercise.allowFeedbackRequests).toBeFalse();
        expect(component.exercise.feedbackSuggestionModule).toBe('moduleA');
    });

    it('should honor the initial module when athena remains enabled', fakeAsync(() => {
        component.exercise.feedbackSuggestionModule = 'initial-module';
        component.initialAthenaModule = 'initial-module';
        athenaService.getAvailableModules.mockReturnValue(of(['moduleA']));
        profileService.isProfileActive.mockReturnValue(true);

        component.ngOnInit();
        tick();

        expect(component.exercise.feedbackSuggestionModule).toBe('initial-module');
        expect(component.modulesAvailable).toBeTrue();
        expect(component.isAthenaEnabled).toBeTrue();
    }));
});
