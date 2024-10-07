import { ChangeDetectionStrategy, Component, Input, OnInit } from '@angular/core';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { ProgrammingExerciseGitDiffEntry } from 'app/entities/hestia/programming-exercise-git-diff-entry.model';
import { faSpinner, faTableColumns } from '@fortawesome/free-solid-svg-icons';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { GitDiffLineStatComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-line-stat.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { GitDiffFilePanelComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-file-panel.component';

interface DiffInformation {
    path: string;
    entries: ProgrammingExerciseGitDiffEntry[];
    templateFileContent?: string;
    solutionFileContent?: string;
    diffReady: boolean;
}

@Component({
    selector: 'jhi-git-diff-report',
    templateUrl: './git-diff-report.component.html',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [GitDiffLineStatComponent, ArtemisSharedModule, ArtemisSharedComponentModule, GitDiffFilePanelComponent],
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
    diffInformationForPaths: DiffInformation[] = [];
    allDiffsReady = false;
    nothingToDisplay = false;
    allowSplitView = true;
    renamedFilePaths: { [before: string]: string | undefined } = {};

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
        this.diffInformationForPaths = this.filePaths
            .filter((path) => this.entriesByPath.get(path)?.length)
            .map((path) => {
                // entries is not undefined due to the filter
                const entries = this.entriesByPath.get(path)!;
                const templateFileContent = this.templateFileContentByPath.get(this.renamedFilePaths[path] ?? path);
                const solutionFileContent = this.solutionFileContentByPath.get(path);
                return { path, entries, templateFileContent, solutionFileContent, diffReady: false };
            });
        this.nothingToDisplay = this.diffInformationForPaths.length === 0;
    }

    /**
     * Records that the diff editor for a file has changed its "ready" state.
     * If all paths have reported that they are ready, {@link allDiffsReady} will be set to true.
     * @param path The path of the file whose diff this event refers to.
     * @param ready Whether the diff is ready to be displayed or not.
     */
    onDiffReady(path: string, ready: boolean) {
        const index = this.diffInformationForPaths.findIndex((info) => info.path === path);
        if (index !== -1) {
            this.diffInformationForPaths[index].diffReady = ready;
            this.allDiffsReady = Object.values(this.diffInformationForPaths).every((info) => info.diffReady);
        } else {
            console.error(`Received diff ready event for unknown path: ${path}`);
        }
    }
}
