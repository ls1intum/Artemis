import { expect, vi } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ActivatedRoute } from '@angular/router';
import { PROFILE_ATHENA } from 'app/app.constants';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { AthenaService } from 'app/assessment/shared/services/athena.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ExerciseFeedbackSuggestionOptionsComponent } from 'app/exercise/feedback-suggestion/exercise-feedback-suggestion-options.component';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import dayjs from 'dayjs/esm';
import { of } from 'rxjs';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

describe('ExerciseFeedbackSuggestionOptionsComponent', () => {
    setupTestBed({ zoneless: true });
    let component: ExerciseFeedbackSuggestionOptionsComponent;
    let fixture: ComponentFixture<ExerciseFeedbackSuggestionOptionsComponent>;
    let athenaService: AthenaService;
    let profileService: ProfileService;
    const pastDueDate = dayjs().subtract(1, 'hour');
    const futureDueDate = dayjs().add(1, 'hour');

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ExerciseFeedbackSuggestionOptionsComponent],
            providers: [
                { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '1' } } } },
                {
                    provide: ProfileService,
                    useClass: MockProfileService,
                },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseFeedbackSuggestionOptionsComponent);
        component = fixture.componentInstance;
        athenaService = TestBed.inject(AthenaService);
        profileService = TestBed.inject(ProfileService);
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize with available modules', async () => {
        const modules = ['Module1', 'Module2'];
        vi.spyOn(athenaService, 'getAvailableModules').mockReturnValue(of(modules));
        component.exercise = { type: ExerciseType.TEXT, dueDate: futureDueDate, feedbackSuggestionModule: undefined } as Exercise;

        component.ngOnInit();

        expect(component.availableAthenaModules).toEqual(modules);
        expect(component.modulesAvailable).toBeTruthy();
    });

    it('should set isAthenaEnabled$ with the result from athenaService', async () => {
        vi.spyOn(athenaService, 'getAvailableModules').mockReturnValue(of());
        vi.spyOn(profileService, 'isProfileActive').mockImplementation((profile) => profile === PROFILE_ATHENA);
        component.exercise = { type: ExerciseType.TEXT, dueDate: futureDueDate, feedbackSuggestionModule: undefined } as Exercise;

        component.ngOnInit();

        expect(component.isAthenaEnabled).toBeDefined();
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
        component.availableAthenaModules = ['Module1', 'Module2'];
        component.exercise = { type: ExerciseType.PROGRAMMING } as Exercise;

        const event = { target: { checked: true } };
        component.toggleFeedbackSuggestions(event);

        expect(component.exercise.feedbackSuggestionModule).toBe('Module1');

        event.target.checked = false;
        component.toggleFeedbackSuggestions(event);

        expect(component.exercise.feedbackSuggestionModule).toBeUndefined();
    });

    it('should toggle feedback requests and set the module for text exercises', () => {
        component.availableAthenaModules = ['Module1', 'Module2'];
        component.exercise = { type: ExerciseType.TEXT } as Exercise;

        const event = { target: { checked: true } };
        component.toggleFeedbackRequests(event); // for students

        expect(component.exercise.feedbackSuggestionModule).toBe('Module1');

        event.target.checked = false;
        component.toggleFeedbackSuggestions(event); // for tutors, should disable both

        expect(component.exercise.feedbackSuggestionModule).toBeUndefined();
    });
});
