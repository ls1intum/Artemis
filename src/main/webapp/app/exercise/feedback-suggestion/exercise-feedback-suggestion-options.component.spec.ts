import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { ExerciseFeedbackSuggestionOptionsComponent } from 'app/exercise/feedback-suggestion/exercise-feedback-suggestion-options.component';
import { AthenaService } from 'app/assessment/shared/services/athena.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { TranslateModule } from '@ngx-translate/core';

describe('ExerciseFeedbackSuggestionOptionsComponent', () => {
    setupTestBed({ zoneless: true });
    let component: ExerciseFeedbackSuggestionOptionsComponent;
    let fixture: ComponentFixture<ExerciseFeedbackSuggestionOptionsComponent>;
    let athenaService: { getAvailableModules: ReturnType<typeof vi.fn> };
    let profileService: { isModuleFeatureActive: ReturnType<typeof vi.fn> };

    const futureDueDate = dayjs().add(1, 'day');
    const pastDueDate = dayjs().subtract(1, 'day');

    const makeExercise = (overrides: Partial<Exercise> = {}): Exercise =>
        ({
            id: 1,
            type: ExerciseType.TEXT,
            assessmentType: AssessmentType.SEMI_AUTOMATIC,
            dueDate: futureDueDate,
            feedbackSuggestionModule: 'module_text_test',
            allowFeedbackRequests: false,
            numberOfAssessmentsOfCorrectionRounds: [],
            studentAssignedTeamIdComputed: false,
            secondCorrectionEnabled: false,
            ...overrides,
        }) as Exercise;

    beforeEach(async () => {
        athenaService = { getAvailableModules: vi.fn().mockReturnValue(of(['moduleA', 'moduleB'])) };
        profileService = { isModuleFeatureActive: vi.fn().mockReturnValue(true) };

        await TestBed.configureTestingModule({
            imports: [ExerciseFeedbackSuggestionOptionsComponent, TranslateModule.forRoot()],
            providers: [
                { provide: AthenaService, useValue: athenaService },
                { provide: ProfileService, useValue: profileService },
                {
                    provide: ActivatedRoute,
                    useValue: { snapshot: { paramMap: { get: (key: string) => (key === 'courseId' ? '42' : null) } } },
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseFeedbackSuggestionOptionsComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exercise', makeExercise());
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should load available modules and set isAthenaEnabled on ngOnInit', () => {
        component.ngOnInit();
        expect(athenaService.getAvailableModules).toHaveBeenCalledWith(42, expect.objectContaining({ type: ExerciseType.TEXT }));
        expect(component.availableAthenaModules()).toEqual(['moduleA', 'moduleB']);
        expect(component.modulesAvailable()).toBe(true);
        expect(component.isAthenaEnabled()).toBe(true);
    });

    it('should mark modulesAvailable false when no modules returned', () => {
        athenaService.getAvailableModules.mockReturnValue(of([]));
        component.ngOnInit();
        expect(component.modulesAvailable()).toBe(false);
    });

    it('inputControlsDisabled returns false for text exercise with future due date', () => {
        expect(component.inputControlsDisabled()).toBe(false);
    });

    it('inputControlsDisabled returns true for text exercise with past due date', () => {
        fixture.componentRef.setInput('exercise', makeExercise({ dueDate: pastDueDate }));
        fixture.detectChanges();
        expect(component.inputControlsDisabled()).toBe(true);
    });

    it('inputControlsDisabled returns true for programming exercise with automatic assessment', () => {
        fixture.componentRef.setInput('exercise', makeExercise({ type: ExerciseType.PROGRAMMING, assessmentType: AssessmentType.AUTOMATIC }));
        fixture.detectChanges();
        expect(component.inputControlsDisabled()).toBe(true);
    });

    it('inputControlsDisabled returns true when readOnly is true', () => {
        fixture.componentRef.setInput('exercise', makeExercise({ type: ExerciseType.PROGRAMMING, assessmentType: AssessmentType.SEMI_AUTOMATIC }));
        fixture.componentRef.setInput('readOnly', true);
        fixture.detectChanges();
        expect(component.inputControlsDisabled()).toBe(true);
    });

    it('inputControlsDisabled returns true for programming exercise with undefined dueDate', () => {
        fixture.componentRef.setInput('exercise', makeExercise({ type: ExerciseType.PROGRAMMING, assessmentType: AssessmentType.SEMI_AUTOMATIC, dueDate: undefined }));
        fixture.detectChanges();
        expect(component.inputControlsDisabled()).toBe(true);
    });

    it('getCheckboxLabelStyle returns grey when disabled', () => {
        fixture.componentRef.setInput('exercise', makeExercise({ dueDate: pastDueDate }));
        fixture.detectChanges();
        expect(component.getCheckboxLabelStyle()).toEqual({ color: 'grey' });
    });

    it('getCheckboxLabelStyle returns empty object when enabled', () => {
        expect(component.getCheckboxLabelStyle()).toEqual({});
    });

    it('toggleFeedbackSuggestions enables feedback module when checked', () => {
        component.availableAthenaModules.set(['moduleA', 'moduleB']);
        const exercise = makeExercise({ feedbackSuggestionModule: undefined });
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        const event = { target: { checked: true } } as unknown as Event;
        component.toggleFeedbackSuggestions(event);
        expect(exercise.feedbackSuggestionModule).toBe('moduleA');
    });

    it('toggleFeedbackSuggestions disables feedback module and requests when unchecked', () => {
        const exercise = makeExercise({ feedbackSuggestionModule: 'moduleA', allowFeedbackRequests: true });
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        const event = { target: { checked: false } } as unknown as Event;
        component.toggleFeedbackSuggestions(event);
        expect(exercise.feedbackSuggestionModule).toBeUndefined();
        expect(exercise.allowFeedbackRequests).toBe(false);
    });

    it('toggleFeedbackRequests enables feedback requests and sets module when checked', () => {
        component.availableAthenaModules.set(['moduleA']);
        const exercise = makeExercise({ feedbackSuggestionModule: undefined, allowFeedbackRequests: false });
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        const event = { target: { checked: true } } as unknown as Event;
        component.toggleFeedbackRequests(event);
        expect(exercise.allowFeedbackRequests).toBe(true);
        expect(exercise.feedbackSuggestionModule).toBe('moduleA');
    });

    it('toggleFeedbackRequests disables feedback requests when unchecked', () => {
        const exercise = makeExercise({ allowFeedbackRequests: true });
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        const event = { target: { checked: false } } as unknown as Event;
        component.toggleFeedbackRequests(event);
        expect(exercise.allowFeedbackRequests).toBe(false);
    });

    it('setFeedbackModule updates the exercise feedbackSuggestionModule', () => {
        const exercise = makeExercise({ feedbackSuggestionModule: 'old' });
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        component.setFeedbackModule('new-module');
        expect(exercise.feedbackSuggestionModule).toBe('new-module');
    });

    it('setFeedbackModule can clear the feedbackSuggestionModule', () => {
        const exercise = makeExercise({ feedbackSuggestionModule: 'old' });
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        component.setFeedbackModule(undefined);
        expect(exercise.feedbackSuggestionModule).toBeUndefined();
    });
});
