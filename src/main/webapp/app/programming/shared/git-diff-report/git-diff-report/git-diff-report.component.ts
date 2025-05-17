import { ChangeDetectionStrategy, Component, computed, effect, input, signal, untracked } from '@angular/core';
import { faSpinner, faTableColumns } from '@fortawesome/free-solid-svg-icons';
import { ButtonComponent, ButtonSize, ButtonType, TooltipPlacement } from 'app/shared/components/button/button.component';
import { GitDiffLineStatComponent } from 'app/programming/shared/git-diff-report/git-diff-line-stat/git-diff-line-stat.component';

import { GitDiffFilePanelComponent } from 'app/programming/shared/git-diff-report/git-diff-file-panel/git-diff-file-panel.component';
import { captureException } from '@sentry/angular';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { DiffInformation, FileStatus, LineChange } from 'app/programming/shared/git-diff-report/model/git-diff.model';
import ignore from 'ignore';

@Component({
    selector: 'jhi-git-diff-report',
    templateUrl: './git-diff-report.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [GitDiffLineStatComponent, GitDiffFilePanelComponent, ArtemisTranslatePipe, FontAwesomeModule, NgbTooltipModule, ButtonComponent],
})
export class GitDiffReportComponent {
    protected readonly faSpinner = faSpinner;
    protected readonly faTableColumns = faTableColumns;
    protected readonly ButtonSize = ButtonSize;
    protected readonly ButtonType = ButtonType;
    protected readonly TooltipPlacement = TooltipPlacement;

    readonly templateFileContentByPath = input.required<Map<string, string>>();
    readonly solutionFileContentByPath = input.required<Map<string, string>>();
    readonly diffForTemplateAndSolution = input<boolean>(true);
    readonly diffForTemplateAndEmptyRepository = input<boolean>(false);
    readonly isRepositoryView = input<boolean>(false);

    readonly filePaths = computed(() => {
        return [...new Set([...this.templateFileContentByPath().keys(), ...this.solutionFileContentByPath().keys()])].sort();
    });

    readonly diffInformationForPaths = signal<DiffInformation[]>([]);
    readonly nothingToDisplay = computed(() => this.diffInformationForPaths().length === 0);
    readonly leftCommitHash = input<string>();
    readonly rightCommitHash = input<string>();
    readonly participationId = input<number>();
    readonly allDiffsReady = computed(() => Object.values(this.diffInformationForPaths()).every((info) => info.diffReady));
    readonly allowSplitView = signal<boolean>(true);

    readonly totalAddedLines = computed(() => 
        this.diffInformationForPaths().reduce(
            (sum, info) => sum + (info.lineChange?.addedLineCount || 0), 
            0
        )
    );
    readonly totalRemovedLines = computed(() => 
        this.diffInformationForPaths().reduce(
            (sum, info) => sum + (info.lineChange?.removedLineCount || 0), 
            0
        )
    );

    readonly leftCommit = computed(() => this.leftCommitHash()?.substring(0, 10));
    readonly rightCommit = computed(() => this.rightCommitHash()?.substring(0, 10));

    constructor() {
        effect(() => {
            untracked(() => {
                // Extract .gitignore content from the solution repository
                const ig = ignore().add(this.solutionFileContentByPath().get('.gitignore') || '');
                const created: string[] = [];
                const deleted: string[] = [];
                let diffInformation: DiffInformation[] = this.filePaths().filter(ig.createFilter()) //Ignoring .gitignore patterns
                .filter((path) => {
                            const templateContent = this.templateFileContentByPath().get(path);
                            const solutionContent = this.solutionFileContentByPath().get(path);
                            return path && (templateContent !== solutionContent || (templateContent === undefined) !== (solutionContent === undefined));
                        })
                        .map((path) => {
                            const templateFileContent = this.templateFileContentByPath().get(path);
                            const solutionFileContent = this.solutionFileContentByPath().get(path);	

                            let fileStatus: FileStatus;
                            if (!templateFileContent && solutionFileContent) {
                                created.push(path);
                                fileStatus = FileStatus.CREATED;
                            } else if (templateFileContent && !solutionFileContent) {
                                deleted.push(path);
                                fileStatus = FileStatus.DELETED;
                            } else {
                                fileStatus = FileStatus.UNCHANGED;
                            }
                            
                            return { 
                                title: path,
                                path, 
                                oldPath: '',
                                templateFileContent, 
                                solutionFileContent, 
                                diffReady: false, 
                                fileStatus
                            };
                        });
                
                // Identify renamed files and merge them into a single entry with RENAMED status
                diffInformation = this.mergeRenamedFiles(diffInformation, created, deleted);

                this.diffInformationForPaths.set(diffInformation);
            });
        });
    }

    private mergeRenamedFiles(diffInformation: DiffInformation[], created: string[], deleted: string[]): DiffInformation[] {
        const toRemove = new Set<string>();
        for (const createdPath of created) {
            const createdFileContent = this.solutionFileContentByPath().get(createdPath);
            for (const deletedPath of deleted) {
                const deletedFileContent = this.templateFileContentByPath().get(deletedPath);
                if (createdFileContent === deletedFileContent) {
                    // Find the created and deleted entries
                    const createdIndex = diffInformation.findIndex(info => info.path === createdPath);
                    const deletedIndex = diffInformation.findIndex(info => info.path === deletedPath);
                    if (createdIndex !== -1 && deletedIndex !== -1) {
                        // Merge into a single RENAMED entry using old/new fields
                        diffInformation[createdIndex] = {
                            title: `${deletedPath} → ${createdPath}`,
                            diffReady: false,
                            fileStatus: FileStatus.RENAMED,
                            path: createdPath,
                            oldPath: deletedPath || '',
                            templateFileContent: deletedFileContent || '',
                            solutionFileContent: createdFileContent || '',
                        };
                        toRemove.add(deletedPath);
                    }
                }
            }
        }
        // Remove deleted entries that have been merged
        return diffInformation.filter(info => !toRemove.has(info.path));
    }

    

    /**
     * Records that the diff editor for a file has changed its "ready" state.
     * If all paths have reported that they are ready, {@link allDiffsReady} will be set to true.
     * @param path The path of the file whose diff this event refers to.
     * @param ready Whether the diff is ready to be displayed or not.
     * @param lineChange Line change information from the diff editor.
     */
    onDiffReady(path: string, ready: boolean, lineChange: LineChange) {
        const diffInformation = [...this.diffInformationForPaths()];
        const index = diffInformation.findIndex((info) => info.path === path);
        
        if (index !== -1) {
            diffInformation[index].diffReady = ready;
            
            // Update line change information if available
            if (ready && lineChange) {
                diffInformation[index].lineChange = lineChange;
            }
            
            this.diffInformationForPaths.set(diffInformation);
        } else {
            captureException(`Received diff ready event for unknown path: ${path}`);
        }
    }
}
