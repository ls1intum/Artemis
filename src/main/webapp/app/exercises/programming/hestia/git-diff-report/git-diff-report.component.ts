import { Component, Input, OnInit } from '@angular/core';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { ProgrammingExerciseGitDiffEntry } from 'app/entities/hestia/programming-exercise-git-diff-entry.model';
import { faSpinner, faTableColumns } from '@fortawesome/free-solid-svg-icons';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';

@Component({
    selector: 'jhi-git-diff-report',
    templateUrl: './git-diff-report.component.html',
})
export class GitDiffReportComponent implements OnInit {
    @Input() report: ProgrammingExerciseGitDiffReport;

    @Input() templateFileContentByPath: Map<string, string>;

    @Input() solutionFileContentByPath: Map<string, string>;

    @Input() filePaths: string[];

    @Input() diffForTemplateAndSolution = true;

    @Input() diffForTemplateAndEmptyRepository = false;

    @Input() isRepositoryView = false;

    leftCommit: string | undefined;

    rightCommit: string | undefined;

    entries: ProgrammingExerciseGitDiffEntry[];
    entriesByPath: Map<string, ProgrammingExerciseGitDiffEntry[]>;
    addedLineCount: number;
    removedLineCount: number;
    diffsReadyByPath: { [path: string]: boolean } = {};
    allDiffsReady = false;
    nothingToDisplay = false;
    allowSplitView = true;
    renamedFilePaths: { [before: string]: string } = {};

    faSpinner = faSpinner;
    faTableColumns = faTableColumns;

    // Expose to template
    protected readonly ButtonSize = ButtonSize;
    protected readonly ButtonType = ButtonType;

    constructor() {}

    ngOnInit(): void {
        // Sort the diff entries by file path and start lines
        this.entries =
            this.report.entries?.sort((a, b) => {
                const filePathA = a.filePath ?? a.previousFilePath ?? '';
                const filePathB = b.filePath ?? b.previousFilePath ?? '';
                if (filePathA < filePathB) {
                    return -1;
                }
                if (filePathA > filePathB) {
                    return 1;
                }
                return (a.startLine ?? a.previousStartLine ?? 0) - (b.startLine ?? b.previousStartLine ?? 0);
            }) ?? [];

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
                        .get(entry.previousFilePath!)
                        ?.split('\n')
                        .slice(entry.previousStartLine - 1, entry.previousStartLine + entry.previousLineCount - 1);
                }
            })
            .filter((line) => line && line.trim().length !== 0).length;

        // Create a set of all file paths
        this.filePaths = [...new Set([...this.templateFileContentByPath.keys(), ...this.solutionFileContentByPath.keys()])].sort();
        // Track renamed files
        this.entries.forEach((entry) => {
            // Accounts only for files that have existed in the original and the modified version, but under different names
            if (entry.filePath && entry.previousFilePath && entry.filePath !== entry.previousFilePath) {
                this.renamedFilePaths[entry.filePath] = entry.previousFilePath;
            }
        });
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
        this.leftCommit = this.report.leftCommitHash?.substring(0, 10);
        this.rightCommit = this.report.rightCommitHash?.substring(0, 10);
        this.filePaths.forEach((path) => {
            if (this.entriesByPath.get(path)?.length) {
                this.diffsReadyByPath[path] = false;
            }
        });
        this.nothingToDisplay = Object.keys(this.diffsReadyByPath).length === 0;
    }

    /**
     * Records that the diff editor for a file has changed its "ready" state.
     * If all paths have reported that they are ready, {@link allDiffsReady} will be set to true.
     * @param path The path of the file whose diff this event refers to.
     * @param ready Whether the diff is ready to be displayed or not.
     */
    onDiffReady(path: string, ready: boolean) {
        this.diffsReadyByPath[path] = ready;
        this.allDiffsReady = Object.values(this.diffsReadyByPath).reduce((a, b) => a && b);
    }
}
