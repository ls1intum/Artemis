import { Component, Input, OnInit } from '@angular/core';
import { ProgrammingExerciseFullGitDiffReport } from 'app/entities/hestia/programming-exercise-full-git-diff-report.model';
import { ProgrammingExerciseFullGitDiffEntry } from 'app/entities/hestia/programming-exercise-full-git-diff-entry.model';

@Component({
    selector: 'jhi-git-diff-report',
    templateUrl: './full-git-diff-report.component.html',
    styleUrls: ['./git-diff-report.component.scss'],
})
export class FullGitDiffReportComponent implements OnInit {
    @Input()
    report: ProgrammingExerciseFullGitDiffReport;

    entries: ProgrammingExerciseFullGitDiffEntry[];
    addedLineCount: number;
    removedLineCount: number;
    addedSquareCount: number;
    removedSquareCount: number;

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

        this.addedLineCount = this.entries.flatMap((entry) => entry.code?.split('\n')).filter((line) => line !== undefined && line.length !== 0).length;
        this.removedLineCount = this.entries.flatMap((entry) => entry.previousCode?.split('\n')).filter((line) => line !== undefined && line.length !== 0).length;
        if (this.addedLineCount === 0) {
            this.addedSquareCount = 0;
            this.removedSquareCount = 5;
        } else if (this.removedLineCount === 0) {
            this.addedSquareCount = 5;
            this.removedSquareCount = 0;
        } else {
            const totalLineCount = this.addedLineCount + this.removedLineCount;
            this.addedSquareCount = Math.round(Math.max(1, Math.min(4, (this.addedLineCount / totalLineCount) * 5)));
            this.removedSquareCount = 5 - this.addedSquareCount;
        }
    }
}
