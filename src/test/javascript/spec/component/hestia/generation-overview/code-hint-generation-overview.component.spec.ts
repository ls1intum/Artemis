import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { CodeHint, CodeHintGenerationStep } from 'app/entities/hestia/code-hint-model';
import { CodeHintGenerationOverviewComponent } from 'app/exercises/programming/hestia/generation-overview/code-hint-generation-overview/code-hint-generation-overview.component';
import { MockActivatedRoute } from '../../../helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';
import { CoverageReport } from 'app/entities/hestia/coverage-report.model';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';

describe('CodeHintGenerationOverview Component', () => {
    let comp: CodeHintGenerationOverviewComponent;
    let fixture: ComponentFixture<CodeHintGenerationOverviewComponent>;

    const exercise = new ProgrammingExercise(undefined, undefined);
    exercise.id = 1;
    exercise.testwiseCoverageEnabled = true;

    let latestStepSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CodeHintGenerationOverviewComponent],
            providers: [{ provide: ActivatedRoute, useValue: new MockActivatedRoute({ exercise }) }],
        }).compileComponents();
        fixture = TestBed.createComponent(CodeHintGenerationOverviewComponent);
        comp = fixture.componentInstance;

        latestStepSpy = jest.spyOn(comp, 'setLatestPerformedStep');
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set all step status on init', () => {
        comp.ngOnInit();

        const expectedStatus = new Map<CodeHintGenerationStep, boolean>();
        expectedStatus.set(CodeHintGenerationStep.GIT_DIFF, false);
        expectedStatus.set(CodeHintGenerationStep.COVERAGE, false);
        expectedStatus.set(CodeHintGenerationStep.SOLUTION_ENTRIES, false);
        expectedStatus.set(CodeHintGenerationStep.CODE_HINTS, false);

        expect(comp.isPerformedByStep).toEqual(expectedStatus);
        expect(comp.allowBehavioralEntryGeneration).toBeTrue();
        expect(comp.currentStep).toEqual(CodeHintGenerationStep.GIT_DIFF);
    });

    it('should set next step as current step', () => {
        comp.currentStep = CodeHintGenerationStep.GIT_DIFF;
        comp.onNextStep();
        expect(comp.currentStep).toBe(CodeHintGenerationStep.COVERAGE);
    });

    it('should set previous step as current step', () => {
        comp.currentStep = CodeHintGenerationStep.COVERAGE;
        comp.onPreviousStep();
        expect(comp.currentStep).toBe(CodeHintGenerationStep.GIT_DIFF);
    });

    it('should check if next step is available', () => {
        const expectedStatus = new Map<CodeHintGenerationStep, boolean>();
        comp.isPerformedByStep = expectedStatus;
        comp.currentStep = CodeHintGenerationStep.GIT_DIFF;
        expectedStatus.set(CodeHintGenerationStep.GIT_DIFF, false);

        let isAvailable = comp.isNextStepAvailable();
        expect(isAvailable).toBeFalse();

        expectedStatus.set(CodeHintGenerationStep.GIT_DIFF, true);

        isAvailable = comp.isNextStepAvailable();
        expect(isAvailable).toBeTrue();
    });

    it('should set specific step as current step', () => {
        comp.currentStep = CodeHintGenerationStep.GIT_DIFF;
        comp.onStepChange(CodeHintGenerationStep.COVERAGE);
        expect(comp.currentStep).toBe(CodeHintGenerationStep.COVERAGE);
    });

    it('should update diff step status', () => {
        latestStepSpy.mockReturnValue(undefined);
        comp.isPerformedByStep = new Map<CodeHintGenerationStep, boolean>();
        comp.onDiffReportLoaded(undefined);

        expect(latestStepSpy).toHaveBeenCalledOnce();
        expect(latestStepSpy).toHaveBeenCalledWith(CodeHintGenerationStep.GIT_DIFF);
        expect(comp.isPerformedByStep.get(CodeHintGenerationStep.GIT_DIFF)).toBeFalse();

        comp.onDiffReportLoaded(new ProgrammingExerciseGitDiffReport());
        expect(latestStepSpy).toHaveBeenCalledTimes(2);
        expect(latestStepSpy).toHaveBeenNthCalledWith(2, CodeHintGenerationStep.GIT_DIFF);
        expect(comp.isPerformedByStep.get(CodeHintGenerationStep.GIT_DIFF)).toBeTrue();
    });

    it('should update coverage step status', () => {
        latestStepSpy.mockReturnValue(undefined);
        comp.isPerformedByStep = new Map<CodeHintGenerationStep, boolean>();
        comp.onCoverageReportLoaded(undefined);

        expect(latestStepSpy).toHaveBeenCalledOnce();
        expect(latestStepSpy).toHaveBeenCalledWith(CodeHintGenerationStep.COVERAGE);
        expect(comp.isPerformedByStep.get(CodeHintGenerationStep.COVERAGE)).toBeFalse();

        comp.onCoverageReportLoaded(new CoverageReport());
        expect(latestStepSpy).toHaveBeenCalledTimes(2);
        expect(latestStepSpy).toHaveBeenNthCalledWith(2, CodeHintGenerationStep.COVERAGE);
        expect(comp.isPerformedByStep.get(CodeHintGenerationStep.COVERAGE)).toBeTrue();
    });

    it('should update solution entry step status', () => {
        latestStepSpy.mockReturnValue(undefined);
        comp.isPerformedByStep = new Map<CodeHintGenerationStep, boolean>();
        comp.onSolutionEntryChanges(undefined);

        expect(latestStepSpy).toHaveBeenCalledOnce();
        expect(latestStepSpy).toHaveBeenCalledWith(CodeHintGenerationStep.SOLUTION_ENTRIES);
        expect(comp.isPerformedByStep.get(CodeHintGenerationStep.SOLUTION_ENTRIES)).toBeFalse();

        comp.onSolutionEntryChanges([]);
        expect(latestStepSpy).toHaveBeenCalledTimes(2);
        expect(latestStepSpy).toHaveBeenNthCalledWith(2, CodeHintGenerationStep.SOLUTION_ENTRIES);
        expect(comp.isPerformedByStep.get(CodeHintGenerationStep.SOLUTION_ENTRIES)).toBeFalse();

        comp.onSolutionEntryChanges([new ProgrammingExerciseSolutionEntry()]);
        expect(latestStepSpy).toHaveBeenCalledTimes(3);
        expect(latestStepSpy).toHaveBeenNthCalledWith(3, CodeHintGenerationStep.SOLUTION_ENTRIES);
        expect(comp.isPerformedByStep.get(CodeHintGenerationStep.SOLUTION_ENTRIES)).toBeTrue();
    });

    it('should update code hint step status', () => {
        latestStepSpy.mockReturnValue(undefined);
        comp.isPerformedByStep = new Map<CodeHintGenerationStep, boolean>();
        comp.onCodeHintsLoaded(undefined);

        expect(latestStepSpy).toHaveBeenCalledOnce();
        expect(latestStepSpy).toHaveBeenCalledWith(CodeHintGenerationStep.CODE_HINTS);
        expect(comp.isPerformedByStep.get(CodeHintGenerationStep.CODE_HINTS)).toBeFalse();

        comp.onCodeHintsLoaded([]);
        expect(latestStepSpy).toHaveBeenCalledTimes(2);
        expect(latestStepSpy).toHaveBeenNthCalledWith(2, CodeHintGenerationStep.CODE_HINTS);
        expect(comp.isPerformedByStep.get(CodeHintGenerationStep.CODE_HINTS)).toBeFalse();

        comp.onCodeHintsLoaded([new CodeHint()]);
        expect(latestStepSpy).toHaveBeenCalledTimes(3);
        expect(latestStepSpy).toHaveBeenNthCalledWith(3, CodeHintGenerationStep.CODE_HINTS);
        expect(comp.isPerformedByStep.get(CodeHintGenerationStep.CODE_HINTS)).toBeTrue();
    });

    it('should not update latest performed step for update on previous step', () => {
        comp.currentStep = CodeHintGenerationStep.SOLUTION_ENTRIES;
        comp.isPerformedByStep = new Map<CodeHintGenerationStep, boolean>();
        comp.isPerformedByStep.set(CodeHintGenerationStep.SOLUTION_ENTRIES, true);

        comp.isPerformedByStep.set(CodeHintGenerationStep.GIT_DIFF, true);
        comp.setLatestPerformedStep(CodeHintGenerationStep.GIT_DIFF);

        expect(comp.currentStep).toBe(CodeHintGenerationStep.SOLUTION_ENTRIES);
    });

    it('should select latest performed step for update on later step', () => {
        comp.currentStep = CodeHintGenerationStep.GIT_DIFF;
        comp.isPerformedByStep = new Map<CodeHintGenerationStep, boolean>();
        comp.isPerformedByStep.set(CodeHintGenerationStep.GIT_DIFF, true);

        comp.isPerformedByStep.set(CodeHintGenerationStep.SOLUTION_ENTRIES, true);
        comp.setLatestPerformedStep(CodeHintGenerationStep.SOLUTION_ENTRIES);

        expect(comp.currentStep).toBe(CodeHintGenerationStep.SOLUTION_ENTRIES);
    });
});
