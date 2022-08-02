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

    @Input()
    templateFileContentByPath: Map<string, string>;

    @Input()
    solutionFileContentByPath: Map<string, string>;

    @Input()
    filePaths: string[];

    // TODO: Make this configurable by the user
    numberOfContextLines = 3;
    entries: ProgrammingExerciseGitDiffEntry[];
    entriesByPath: Map<string, ProgrammingExerciseGitDiffEntry[]>;
    addedLineCount: number;
    removedLineCount: number;

    constructor() {}

    ngOnInit(): void {
        // Sort the diff entries by file path and start lines
        this.entries = this.report.entries.sort((a, b) => {
            const filePathA = a.filePath ?? a.previousFilePath;
            const filePathB = b.filePath ?? b.previousFilePath;
            if (filePathA < filePathB) {
                return -1;
            }
            if (filePathA > filePathB) {
                return 1;
            }
            return (a.startLine ?? a.previousStartLine ?? 0) - (b.startLine ?? b.previousStartLine ?? 0);
        });

        this.addedLineCount = this.entries
            .flatMap((entry) => {
                if (entry && entry.filePath && entry.startLine && entry.lineCount) {
                    return this.solutionFileContentByPath
                        .get(entry.filePath)
                        ?.split('\n')
                        .slice(entry.startLine - 1, entry.startLine + entry.lineCount - 1);
                }
            })
            .filter((line) => line && line.trim().length !== 0).length;

        this.removedLineCount = this.entries
            .flatMap((entry) => {
                if (entry && entry.previousFilePath && entry.previousStartLine && entry.previousLineCount) {
                    return this.templateFileContentByPath
                        .get(entry.filePath)
                        ?.split('\n')
                        .slice(entry.previousStartLine - 1, entry.previousStartLine + entry.previousLineCount - 1);
                }
            })
            .filter((line) => line && line.trim().length !== 0).length;

        // Create a set of all file paths
        this.filePaths = [...new Set([...this.templateFileContentByPath.keys(), ...this.solutionFileContentByPath.keys()])].sort();

        // Group the diff entries by file path
        this.entriesByPath = new Map<string, ProgrammingExerciseGitDiffEntry[]>();
        [...this.templateFileContentByPath.keys()].forEach((filePath) => {
            this.entriesByPath.set(
                filePath,
                this.entries.filter((entry) => entry.previousFilePath === filePath && !entry.filePath),
            );
        });
        [...this.solutionFileContentByPath.keys()].forEach((filePath) => {
            this.entriesByPath.set(
                filePath,
                this.entries.filter((entry) => entry.filePath === filePath),
            );
        });
    }
}
