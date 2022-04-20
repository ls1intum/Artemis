import { Component, Input, OnInit } from '@angular/core';

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
            // Calculates the amount of green rectangles to show between 1 and 4
            // This is the rounded percentage of added lines divided by total lines
            this.addedSquareCount = Math.round(Math.max(1, Math.min(4, (this.addedLineCount / totalLineCount) * 5)));
            this.removedSquareCount = 5 - this.addedSquareCount;
        }
    }
}
