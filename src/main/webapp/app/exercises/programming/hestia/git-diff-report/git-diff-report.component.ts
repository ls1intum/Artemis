import { Component, Input, OnInit } from '@angular/core';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { ProgrammingExerciseGitDiffEntry } from 'app/entities/hestia/programming-exercise-git-diff-entry.model';

@Component({
    selector: 'jhi-git-diff-report',
    templateUrl: './git-diff-report.component.html',
})
export class GitDiffReportComponent implements OnInit {
    @Input()
    report: ProgrammingExerciseGitDiffReport;

    entries: ProgrammingExerciseGitDiffEntry[];

    constructor() {}

    ngOnInit(): void {
        this.entries = this.report.entries.sort((a, b) => {
            if (a.filePath < b.filePath) {
                return -1;
            }
            if (a.filePath > b.filePath) {
                return 1;
            }
            return (a.line ?? a.previousLine ?? 0) - (b.line ?? b.previousLine ?? 0);
        });
    }
}
