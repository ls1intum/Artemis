import { Component, Input, OnInit } from '@angular/core';
import { ProgrammingExerciseFullGitDiffReport } from 'app/entities/hestia/programming-exercise-full-git-diff-report.model';
import { ProgrammingExerciseFullGitDiffEntry } from 'app/entities/hestia/programming-exercise-full-git-diff-entry.model';

@Component({
    selector: 'jhi-git-diff-report',
    templateUrl: './full-git-diff-report.component.html',
})
export class FullGitDiffReportComponent implements OnInit {
    @Input()
    report: ProgrammingExerciseFullGitDiffReport;

    entries: ProgrammingExerciseFullGitDiffEntry[];

    constructor() {}

    ngOnInit(): void {
        this.entries = this.report.entries.sort((a, b) => {
            const filePathA = a.filePath ?? a.previousFilePath;
            const filePathB = b.filePath ?? b.previousFilePath;
            if (filePathA < filePathB) {
                return -1;
            }
            if (filePathA > filePathB) {
                return 1;
            }
            return (a.line ?? a.previousLine ?? 0) - (b.line ?? b.previousLine ?? 0);
        });
    }
}
