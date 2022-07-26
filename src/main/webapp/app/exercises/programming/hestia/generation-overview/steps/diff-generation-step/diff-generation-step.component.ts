import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { AlertService } from 'app/core/util/alert.service';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';

@Component({
    selector: 'jhi-diff-generation-step',
    templateUrl: './diff-generation-step.component.html',
    styleUrls: ['../../code-hint-generation-overview/code-hint-generation-overview.component.scss'],
})
export class DiffGenerationStepComponent implements OnInit {
    @Input()
    exercise: ProgrammingExercise;

    @Output()
    onGitDiffLoaded = new EventEmitter<ProgrammingExerciseGitDiffReport>();

    isLoading = false;
    gitDiffReport?: ProgrammingExerciseGitDiffReport;
    templateFileContentByPath: Map<string, string>;
    solutionFileContentByPath: Map<string, string>;

    constructor(private exerciseService: ProgrammingExerciseService, private alertService: AlertService) {}

    ngOnInit() {
        this.isLoading = true;
        this.exerciseService.getDiffReport(this.exercise.id!).subscribe({
            next: (report) => {
                this.gitDiffReport = report;
                this.onGitDiffLoaded.emit(report);
                this.exerciseService.getTemplateRepositoryTestFilesWithContent(this.exercise.id!).subscribe({
                    next: (response: Map<string, string>) => {
                        this.templateFileContentByPath = response;
                        this.isLoading = this.solutionFileContentByPath !== undefined;
                    },
                });
                this.exerciseService.getSolutionRepositoryTestFilesWithContent(this.exercise.id!).subscribe({
                    next: (response: Map<string, string>) => {
                        this.solutionFileContentByPath = response;
                        this.isLoading = this.templateFileContentByPath !== undefined;
                    },
                });
            },
            error: (error) => {
                this.isLoading = false;
                this.alertService.error(error.message);
            },
        });
    }
}
