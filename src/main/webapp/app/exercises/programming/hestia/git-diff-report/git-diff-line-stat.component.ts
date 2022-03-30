import { Component, Input, OnInit } from '@angular/core';
import { ProgrammingExerciseFullGitDiffReport } from 'app/entities/hestia/programming-exercise-full-git-diff-report.model';
import { ProgrammingExerciseFullGitDiffEntry } from 'app/entities/hestia/programming-exercise-full-git-diff-entry.model';

@Component({
    selector: 'jhi-git-diff-line-stat',
    templateUrl: './git-diff-line-stat.component.html',
    styleUrls: ['./git-diff-line-stat.component.scss'],
})
export class GitDiffLineStatComponent implements OnInit {
    @Input()
    addedLineCount: number;
    @Input()
    removedLineCount: number;
    addedSquareCount: number;
    removedSquareCount: number;

    constructor() {}

    ngOnInit(): void {
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
