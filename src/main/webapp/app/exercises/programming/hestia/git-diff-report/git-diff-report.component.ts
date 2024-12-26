import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';
import { faSpinner, faTableColumns } from '@fortawesome/free-solid-svg-icons';
import { ButtonSize, ButtonType, TooltipPlacement } from 'app/shared/components/button.component';
import { GitDiffLineStatComponent, LineStat } from 'app/exercises/programming/hestia/git-diff-report/git-diff-line-stat.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { GitDiffFilePanelComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-file-panel.component';
import * as Diff from 'diff';

interface DiffInformation {
    key: string;
    templatePath?: string;
    solutionPath?: string;
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

    private readonly renames = computed(() => {
        const renames: [string, string][] = [];

        this.filePathsUniqueLeft().forEach((leftPath) => {
            this.filePathsUniqueRight().forEach((rightPath) => {
                const leftFileContent = this.templateFileContentByPath().get(leftPath)!;
                const rightFileContent = this.solutionFileContentByPath().get(rightPath)!;
                const changes = Diff.diffLines(leftFileContent, rightFileContent);
                const originalLinesCount = leftFileContent.split('\n').length;
                const unchangedLinesCount = changes
                    .filter((change) => {
                        return !change.added && !change.removed;
                    })
                    .reduce((lineCount, change) => {
                        return lineCount + (change.count ?? 0);
                    }, 0);
                if (unchangedLinesCount / originalLinesCount >= RENAME_SIMILARITY_THRESHOLD) {
                    renames.push([leftPath, rightPath]);
                }
            });
        });

        return renames;
    });

    private readonly diffFilePathPairsByKey = computed(() => {
        const filePathsRenamedLeft = new Set(this.renames().map((r) => r[0]));
        const filePathsRenamedRight = new Set(this.renames().map((r) => r[1]));

        const filesRemoved = this.filePathsUniqueLeft().filter((path) => !filePathsRenamedLeft.has(path));
        const filesAdded = this.filePathsUniqueRight().filter((path) => !filePathsRenamedRight.has(path));

        const pairs = [
            ...filesRemoved.map((path): [string, undefined] => [path, undefined]),
            ...this.renames(),
            ...this.filePathsCommonContentDiffers().map((path): [string, string] => [path, path]),
            ...filesAdded.map((path): [undefined, string] => [undefined, path]),
        ];

        return new Map(
            pairs.map((pair) => {
                return [this.keyOfPathPair(pair), pair];
            }),
        );
    });

    private readonly diffKeys = computed(() => [...this.diffFilePathPairsByKey().keys()].sort());

    readonly diffInformation = computed<DiffInformation[]>(() => {
        return this.diffKeys().map((key) => {
            const [templatePath, solutionPath] = this.diffFilePathPairsByKey().get(key)!;
            const templateFileContent = templatePath && this.templateFileContentByPath().get(templatePath);
            const solutionFileContent = solutionPath && this.solutionFileContentByPath().get(solutionPath);
            return { key, templatePath, solutionPath, templateFileContent, solutionFileContent };
        });
    });
    readonly nothingToDisplay = computed(() => this.diffInformation().length === 0);
    readonly allDiffsReady = computed(() => this.diffKeys().every((key) => this.diffReadyKeys().has(key)));
    readonly diffReadyKeys = signal<Set<string>>(new Set());
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
     * Records that the diff editor for a file pair has changed its "ready" state.
     * If all files have reported that they are ready, {@link allDiffsReady} will be set to true.
     * @param key The key of the file pair whose diff this event refers to.
     * @param ready Whether the diff is ready to be displayed or not.
     */
    onDiffReady(key: string, ready: boolean) {
        this.diffReadyKeys.update((diffReadyPaths) => {
            const paths = new Set(diffReadyPaths);
            if (ready) {
                paths.add(key);
            } else {
                paths.delete(key);
            }
            return paths;
        });
    }

    onLineStatChanged(key: string, lineStat: LineStat) {
        this.lineStatForPath.update((lineStatForPath) => {
            const stats = new Map(lineStatForPath);
            stats.set(key, lineStat);
            return stats;
        });
    }

    /**
     * Uniquely identifies one pair of files for which a diff editor is shown
     */
    private keyOfPathPair([left, right]: [string, undefined] | [string, string] | [undefined, string]): string {
        return left ?? right;
    }
}
