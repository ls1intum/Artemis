import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { ProgrammingExerciseGitDiffEntry } from 'app/entities/hestia/programming-exercise-git-diff-entry.model';
import { faSpinner, faTableColumns } from '@fortawesome/free-solid-svg-icons';
import { ButtonSize, ButtonType, TooltipPlacement } from 'app/shared/components/button.component';
import { GitDiffLineStatComponent, LineStat } from 'app/exercises/programming/hestia/git-diff-report/git-diff-line-stat.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { GitDiffFilePanelComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-file-panel.component';

interface DiffInformation {
    templatePath: string;
    path: string;
    templateFileContent?: string;
    solutionFileContent?: string;
}

@Component({
    selector: 'jhi-git-diff-report',
    templateUrl: './git-diff-report.component.html',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [GitDiffLineStatComponent, ArtemisSharedModule, ArtemisSharedComponentModule, GitDiffFilePanelComponent],
})
export class GitDiffReportComponent {
    protected readonly faSpinner = faSpinner;
    protected readonly faTableColumns = faTableColumns;
    protected readonly ButtonSize = ButtonSize;
    protected readonly ButtonType = ButtonType;
    protected readonly TooltipPlacement = TooltipPlacement;

    readonly report = input.required<ProgrammingExerciseGitDiffReport>();
    readonly templateFileContentByPath = input.required<Map<string, string>>();
    readonly solutionFileContentByPath = input.required<Map<string, string>>();
    readonly diffForTemplateAndSolution = input<boolean>(true);
    readonly diffForTemplateAndEmptyRepository = input<boolean>(false);
    readonly isRepositoryView = input<boolean>(false);

    readonly sortedEntries = computed(() => {
        return (
            this.report().entries?.sort((a, b) => {
                const filePathA = a.filePath ?? a.previousFilePath ?? '';
                const filePathB = b.filePath ?? b.previousFilePath ?? '';
                if (filePathA < filePathB) {
                    return -1;
                }
                if (filePathA > filePathB) {
                    return 1;
                }
                return (a.startLine ?? a.previousStartLine ?? 0) - (b.startLine ?? b.previousStartLine ?? 0);
            }) ?? []
        );
    });

    readonly filePaths = computed(() => {
        return [...new Set([...this.templateFileContentByPath().keys(), ...this.solutionFileContentByPath().keys()])].sort();
    });

    readonly renamedFilePaths = computed(() => {
        const renamedFilePaths: { [before: string]: string | undefined } = {};
        this.sortedEntries().forEach((entry) => {
            // Accounts only for files that have existed in the original and the modified version, but under different names
            if (entry.filePath && entry.previousFilePath && entry.filePath !== entry.previousFilePath) {
                renamedFilePaths[entry.filePath] = entry.previousFilePath;
            }
        });
        return renamedFilePaths;
    });

    readonly entriesByPath = computed(() => {
        const entriesByPath = new Map<string, ProgrammingExerciseGitDiffEntry[]>();
        [...this.templateFileContentByPath().keys()].forEach((filePath) => {
            entriesByPath.set(
                filePath,
                this.sortedEntries().filter((entry) => entry.previousFilePath === filePath && !entry.filePath),
            );
        });
        [...this.solutionFileContentByPath().keys()].forEach((filePath) => {
            entriesByPath.set(
                filePath,
                this.sortedEntries().filter((entry) => entry.filePath === filePath),
            );
        });
        return entriesByPath;
    });

    readonly diffFilePaths = computed(() => {
        return this.filePaths().filter((path) => this.entriesByPath().get(path)?.length);
    });

    readonly diffInformationForPaths = computed<DiffInformation[]>(() => {
        return this.diffFilePaths().map((path) => {
            // entries is not undefined due to the filter above
            const templatePath = this.renamedFilePaths()[path] ?? path;
            const templateFileContent = this.templateFileContentByPath().get(templatePath);
            const solutionFileContent = this.solutionFileContentByPath().get(path);
            return { templatePath, path, templateFileContent, solutionFileContent };
        });
    });

    readonly leftCommit = computed(() => this.report().leftCommitHash?.substring(0, 10));
    readonly rightCommit = computed(() => this.report().rightCommitHash?.substring(0, 10));
    readonly nothingToDisplay = computed(() => this.diffInformationForPaths().length === 0);
    readonly allDiffsReady = computed(() => this.diffFilePaths().every((path) => this.diffReadyPaths().has(path)));
    readonly diffReadyPaths = signal<Set<string>>(new Set());
    readonly allowSplitView = signal<boolean>(true);
    readonly lineStatForPath = signal<Map<string, LineStat>>(new Map());
    readonly lineStatChanged = output<LineStat>();

    readonly lineStat = computed(() => {
        if (!this.allDiffsReady()) {
            return undefined;
        }

        const totalLineStat = [...this.lineStatForPath().values()].reduce(
            (left, right) => ({
                addedLineCount: left.addedLineCount + right.addedLineCount,
                removedLineCount: left.removedLineCount + right.removedLineCount,
            }),
            { addedLineCount: 0, removedLineCount: 0 },
        );

        this.lineStatChanged.emit(totalLineStat);

        return totalLineStat;
    });

    /**
     * Records that the diff editor for a file has changed its "ready" state.
     * If all paths have reported that they are ready, {@link allDiffsReady} will be set to true.
     * @param path The path of the file whose diff this event refers to.
     * @param ready Whether the diff is ready to be displayed or not.
     */
    onDiffReady(path: string, ready: boolean) {
        this.diffReadyPaths.update((diffReadyPaths) => {
            const paths = new Set(diffReadyPaths);
            if (ready) {
                paths.add(path);
            } else {
                paths.delete(path);
            }
            return paths;
        });
    }

    onLineStatChanged(path: string, lineStat: LineStat) {
        this.lineStatForPath.update((lineStatForPath) => {
            const stats = new Map(lineStatForPath);
            stats.set(path, lineStat);
            return stats;
        });
    }
}
