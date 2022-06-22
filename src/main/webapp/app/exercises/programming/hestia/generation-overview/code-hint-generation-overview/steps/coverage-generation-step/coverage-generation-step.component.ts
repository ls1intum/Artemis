import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { CoverageReport } from 'app/entities/hestia/coverage-report.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Component({
    selector: 'jhi-coverage-generation-step',
    templateUrl: './coverage-generation-step.component.html',
    styleUrls: ['../../code-hint-generation-overview.component.scss'],
})
export class CoverageGenerationStepComponent implements OnInit {
    @Input()
    exercise: ProgrammingExercise;

    @Output()
    onCoverageLoaded = new EventEmitter<CoverageReport>();

    isLoading = false;
    coverageReport?: CoverageReport;
    fileContentByPath = new Map<string, string>();

    constructor(private exerciseService: ProgrammingExerciseService) {}

    ngOnInit(): void {
        this.isLoading = true;
        this.exerciseService.getSolutionRepositoryTestFilesWithContent(this.exercise.id!).subscribe({
            next: (response: Map<string, string>) => {
                this.exerciseService.getLatestFullTestwiseCoverageReport(this.exercise.id!).subscribe({
                    next: (coverageReport) => {
                        this.isLoading = false;
                        this.onCoverageLoaded.emit(coverageReport);
                        this.coverageReport = coverageReport;
                        this.fileContentByPath = response;
                    },
                    error: () => {
                        this.isLoading = false;
                    },
                });
            },
        });
    }
}
