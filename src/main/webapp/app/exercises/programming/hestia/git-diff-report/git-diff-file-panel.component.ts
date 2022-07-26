import { Component, Input, OnInit, ViewEncapsulation } from '@angular/core';
import { ProgrammingExerciseGitDiffEntry } from 'app/entities/hestia/programming-exercise-git-diff-entry.model';
import { faAngleDown, faAngleUp } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-git-diff-file-panel',
    templateUrl: './git-diff-file-panel.component.html',
    styleUrls: ['./git-diff-file-panel.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class GitDiffFilePanelComponent implements OnInit {
    @Input()
    diffEntries: ProgrammingExerciseGitDiffEntry[];

    @Input()
    templateFileContent: string | undefined;

    @Input()
    solutionFileContent: string | undefined;

    @Input()
    numberOfContextLines = 3;

    previousFilePath: string | undefined;
    filePath: string | undefined;

    addedLineCount: number;
    removedLineCount: number;

    faAngleUp = faAngleUp;
    faAngleDown = faAngleDown;

    ngOnInit(): void {
        this.filePath = this.diffEntries
            .map((entry) => entry.filePath)
            .filter((filePath) => filePath)
            .first();

        this.previousFilePath = this.diffEntries
            .map((entry) => entry.previousFilePath)
            .filter((filePath) => filePath)
            .first();

        this.addedLineCount = this.diffEntries
            .flatMap((entry) => {
                if (entry && entry.filePath && entry.startLine && entry.lineCount) {
                    return this.solutionFileContent?.split('\n').slice(entry.startLine - 1, entry.startLine + entry.lineCount - 1);
                }
            })
            .filter((line) => line && line.trim().length !== 0).length;

        this.removedLineCount = this.diffEntries
            .flatMap((entry) => {
                if (entry && entry.previousFilePath && entry.previousStartLine && entry.previousLineCount) {
                    return this.templateFileContent?.split('\n').slice(entry.previousStartLine - 1, entry.previousStartLine + entry.previousLineCount - 1);
                }
            })
            .filter((line) => line && line.trim().length !== 0).length;
    }
}
