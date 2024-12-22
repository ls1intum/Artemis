import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';
import { faSpinner, faTableColumns } from '@fortawesome/free-solid-svg-icons';
import { ButtonSize, ButtonType, TooltipPlacement } from 'app/shared/components/button.component';
import { GitDiffLineStatComponent, LineStat } from 'app/exercises/programming/hestia/git-diff-report/git-diff-line-stat.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { GitDiffFilePanelComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-file-panel.component';
import * as Diff from 'diff';

interface DiffInformation {
    templatePath: string;
    path: string;
    templateFileContent?: string;
    solutionFileContent?: string;
}

/**
 * Analogous to the argument of the git diff -M option
 */
const RENAME_SIMILARITY_THRESHOLD = 0.5;

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

    readonly templateFileContentByPath = input.required<Map<string, string>>();
    readonly solutionFileContentByPath = input.required<Map<string, string>>();
    readonly leftCommitHash = input<string>();
    readonly rightCommitHash = input<string>();
    readonly diffForTemplateAndSolution = input<boolean>(true);
    readonly diffForTemplateAndEmptyRepository = input<boolean>(false);
    readonly isRepositoryView = input<boolean>(false);
    readonly lineStatChanged = output<LineStat>();

    readonly leftCommit = computed(() => this.leftCommitHash()?.substring(0, 10));
    readonly rightCommit = computed(() => this.rightCommitHash()?.substring(0, 10));

    private readonly filePathsLeft = computed(() => [...this.templateFileContentByPath().keys()]);
    private readonly filePathsRight = computed(() => [...this.solutionFileContentByPath().keys()]);
    private readonly filePathsLeftSet = computed(() => new Set(this.filePathsLeft()));
    private readonly filePathsRightSet = computed(() => new Set(this.filePathsRight()));
    private readonly filePathsCommon = computed(() => this.filePathsLeft().filter((left) => this.filePathsRightSet().has(left)));
    private readonly filePathsUniqueLeft = computed(() => this.filePathsLeft().filter((left) => !this.filePathsRightSet().has(left)));
    private readonly filePathsUniqueRight = computed(() => this.filePathsRight().filter((left) => !this.filePathsLeftSet().has(left)));
    private readonly filePathsCommonContentDiffers = computed(() =>
        this.filePathsCommon().filter((path) => !path.endsWith('.jar') && this.templateFileContentByPath().get(path) !== this.solutionFileContentByPath().get(path)),
    );

    readonly renamedFilePaths = computed(() => {
        const renamedFilePaths: { [before: string]: string } = {};

        this.filePathsUniqueLeft().forEach((leftPath) => {
            this.filePathsUniqueRight().forEach((rightPath) => {
                const leftFileContent = this.templateFileContentByPath().get(leftPath)!;
                const rightFileContent = this.solutionFileContentByPath().get(rightPath)!;
                const changes = Diff.diffLines(leftFileContent, rightFileContent);
                const originalLinesCount = leftFileContent.split('\n').length;
                const unchangedLinesCount = changes.filter((change) => !change.added && !change.removed).length;
                if (unchangedLinesCount / originalLinesCount >= RENAME_SIMILARITY_THRESHOLD) {
                    renamedFilePaths[leftPath] = rightPath;
                }
            });
        });

        return renamedFilePaths;
    });

    readonly diffFilePaths = computed(() => {
        return [...this.filePathsUniqueLeft(), ...this.filePathsCommonContentDiffers(), ...this.filePathsUniqueRight()].sort();
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
    readonly nothingToDisplay = computed(() => this.diffInformationForPaths().length === 0);
    readonly allDiffsReady = computed(() => this.diffFilePaths().every((path) => this.diffReadyPaths().has(path)));
    readonly diffReadyPaths = signal<Set<string>>(new Set());
    readonly allowSplitView = signal<boolean>(true);
    readonly lineStatForPath = signal<Map<string, LineStat>>(new Map());

    readonly lineStat = computed(() => {
        if (!this.allDiffsReady()) {
            return undefined;
        }

        return [...this.lineStatForPath().values()].reduce(
            (left, right) => ({
                addedLineCount: left.addedLineCount + right.addedLineCount,
                removedLineCount: left.removedLineCount + right.removedLineCount,
            }),
            { addedLineCount: 0, removedLineCount: 0 },
        );
    });

    constructor() {
        effect(() => {
            const lineStat = this.lineStat();
            if (lineStat) {
                this.lineStatChanged.emit(lineStat);
            }
        });
    }

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
