import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ProgrammingExerciseFullGitDiffReport } from 'app/entities/hestia/programming-exercise-full-git-diff-report.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-diff-generation-step',
    templateUrl: './diff-generation-step.component.html',
    styleUrls: ['../../code-hint-generation-overview/code-hint-generation-overview.component.scss'],
})
export class DiffGenerationStepComponent implements OnInit {
    @Input()
    exercise: ProgrammingExercise;

    @Output()
    onGitDiffLoaded = new EventEmitter<ProgrammingExerciseFullGitDiffReport>();

    isLoading = false;
    gitDiffReport?: ProgrammingExerciseFullGitDiffReport;

    constructor(private exerciseService: ProgrammingExerciseService, private alertService: AlertService) {}

    ngOnInit() {
        this.isLoading = true;
        this.exerciseService.getFullDiffReport(this.exercise.id!).subscribe({
            next: (report) => {
                this.gitDiffReport = report;
                this.isLoading = false;
                this.onGitDiffLoaded.emit(report);
            },
            error: (error) => {
                this.isLoading = false;
                this.alertService.error(error.message);
            },
        });
    }
}
