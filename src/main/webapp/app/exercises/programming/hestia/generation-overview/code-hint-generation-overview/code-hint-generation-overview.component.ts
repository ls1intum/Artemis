import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { CoverageReport } from 'app/entities/hestia/coverage-report.model';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';
import { CodeHint, CodeHintGenerationStep } from 'app/entities/hestia/code-hint-model';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';

@Component({
    selector: 'jhi-code-hint-generation-overview',
    templateUrl: './code-hint-generation-overview.component.html',
    styleUrls: ['./code-hint-generation-overview.component.scss'],
})
export class CodeHintGenerationOverviewComponent implements OnInit {
    exercise?: ProgrammingExercise;

    currentStep: CodeHintGenerationStep;
    isPerformedByStep: Map<any, boolean>;
    selectedSolutionEntries?: ProgrammingExerciseSolutionEntry[];

    allowBehavioralEntryGeneration = false;

    readonly GenerationStep = CodeHintGenerationStep;

    constructor(private route: ActivatedRoute, private router: Router) {}

    ngOnInit() {
        this.route.data.subscribe(({ exercise }) => {
            this.exercise = exercise;
            // set all steps to unperformed initially
            this.isPerformedByStep = new Map<CodeHintGenerationStep, boolean>();
            this.isPerformedByStep.set(CodeHintGenerationStep.SOLUTION_ENTRIES, false);
            this.isPerformedByStep.set(CodeHintGenerationStep.CODE_HINTS, false);
            if (exercise.testwiseCoverageEnabled) {
                this.currentStep = CodeHintGenerationStep.GIT_DIFF;
                this.allowBehavioralEntryGeneration = true;
                this.isPerformedByStep.set(CodeHintGenerationStep.GIT_DIFF, false);
                this.isPerformedByStep.set(CodeHintGenerationStep.COVERAGE, false);
            } else {
                this.currentStep = CodeHintGenerationStep.SOLUTION_ENTRIES;
            }
        });
    }

    setLatestPerformedStep(latestUpdatedStep: CodeHintGenerationStep) {
        if (this.currentStep >= latestUpdatedStep) {
            return;
        }
        const optionalEntry = Array.from(this.isPerformedByStep.entries())
            .filter((a) => a[1])
            .sort((a, b) => b[0] - a[0])
            .first();
        this.currentStep = optionalEntry === undefined ? this.currentStep : optionalEntry![0];
    }

    isNextStepAvailable(): boolean {
        return this.isPerformedByStep.get(this.currentStep) ?? false;
    }

    onNextStep() {
        this.currentStep = this.currentStep + 1;
    }

    onPreviousStep() {
        this.currentStep = this.currentStep - 1;
    }

    onStepChange(step: CodeHintGenerationStep) {
        this.currentStep = step;
    }

    onDiffReportLoaded(diffReport?: ProgrammingExerciseGitDiffReport) {
        this.isPerformedByStep.set(CodeHintGenerationStep.GIT_DIFF, diffReport !== undefined);
        this.setLatestPerformedStep(CodeHintGenerationStep.GIT_DIFF);
    }

    onCoverageReportLoaded(coverageReport?: CoverageReport) {
        this.isPerformedByStep.set(CodeHintGenerationStep.COVERAGE, coverageReport !== undefined);
        this.setLatestPerformedStep(CodeHintGenerationStep.COVERAGE);
    }

    onSolutionEntryChanges(entries?: ProgrammingExerciseSolutionEntry[]) {
        this.selectedSolutionEntries = entries;
        this.isPerformedByStep.set(CodeHintGenerationStep.SOLUTION_ENTRIES, entries !== undefined && entries!.length > 0);
        this.setLatestPerformedStep(CodeHintGenerationStep.SOLUTION_ENTRIES);
    }

    onCodeHintsLoaded(codeHints?: CodeHint[]) {
        this.isPerformedByStep.set(CodeHintGenerationStep.CODE_HINTS, codeHints !== undefined && codeHints!.length > 0);
        this.setLatestPerformedStep(CodeHintGenerationStep.CODE_HINTS);
    }
}
