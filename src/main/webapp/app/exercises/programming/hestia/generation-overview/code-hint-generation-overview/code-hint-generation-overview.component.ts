import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { CoverageReport } from 'app/entities/hestia/coverage-report.model';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';
import { ProgrammingExerciseFullGitDiffReport } from 'app/entities/hestia/programming-exercise-full-git-diff-report.model';
import { CodeHint } from 'app/entities/hestia/code-hint-model';

@Component({
    selector: 'jhi-code-hint-generation-overview',
    templateUrl: './code-hint-generation-overview.component.html',
    styleUrls: ['./code-hint-generation-overview.component.scss'],
})
export class CodeHintGenerationOverviewComponent implements OnInit {
    exercise?: ProgrammingExercise;

    currentStepIndex = 0;
    stepStatus = [false, false, false, false];
    selectedSolutionEntries?: ProgrammingExerciseSolutionEntry[];

    constructor(private route: ActivatedRoute, private router: Router) {}

    ngOnInit() {
        this.route.data.subscribe(({ exercise }) => {
            this.exercise = exercise;
        });
    }

    isNextStepAvailable(): boolean {
        return this.stepStatus[this.currentStepIndex];
    }

    onNextStep() {
        this.currentStepIndex++;
    }

    onPreviousStep() {
        this.currentStepIndex--;
    }

    onStepChange(index: number) {
        this.currentStepIndex = index;
    }

    onDiffReportLoaded(diffReport?: ProgrammingExerciseFullGitDiffReport) {
        this.stepStatus[0] = diffReport !== undefined;
    }

    onCoverageReportLoaded(coverageReport?: CoverageReport) {
        this.stepStatus[1] = coverageReport !== undefined;
    }

    onSolutionEntryChanges(entries?: ProgrammingExerciseSolutionEntry[]) {
        this.selectedSolutionEntries = entries;
        this.stepStatus[2] = entries !== undefined && entries!.length > 0;
    }

    onCodeHintsLoaded(codeHints?: CodeHint[]) {
        this.stepStatus[3] = codeHints !== undefined;
    }
}
