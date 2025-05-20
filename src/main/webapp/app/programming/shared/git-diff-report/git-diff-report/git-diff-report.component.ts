import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { faSpinner, faTableColumns } from '@fortawesome/free-solid-svg-icons';
import { ButtonComponent, ButtonSize, ButtonType, TooltipPlacement } from 'app/shared/components/buttons/button/button.component';
import { GitDiffLineStatComponent } from 'app/programming/shared/git-diff-report/git-diff-line-stat/git-diff-line-stat.component';

import { GitDiffFilePanelComponent } from 'app/programming/shared/git-diff-report/git-diff-file-panel/git-diff-file-panel.component';
import { captureException } from '@sentry/angular';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { RepositoryDiffInformation } from 'app/shared/monaco-editor/diff-editor/util/monaco-diff-editor.util';

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

    readonly repositoryDiffInformation = input.required<RepositoryDiffInformation>();
    readonly diffForTemplateAndSolution = input<boolean>(true);
    readonly diffForTemplateAndEmptyRepository = input<boolean>(false);
    readonly isRepositoryView = input<boolean>(false);

    readonly nothingToDisplay = computed(() => this.repositoryDiffInformation().diffInformations.length === 0);
    readonly leftCommitHash = input<string>();
    readonly rightCommitHash = input<string>();
    readonly participationId = input<number>();
    readonly allDiffsReady = signal<boolean>(false);
    readonly allowSplitView = signal<boolean>(true);

    readonly leftCommit = computed(() => this.leftCommitHash()?.substring(0, 10));
    readonly rightCommit = computed(() => this.rightCommitHash()?.substring(0, 10));

    /**
     * Records that the diff editor for a file has changed its "ready" state.
     * If all paths have reported that they are ready, {@link allDiffsReady} will be set to true.
     * @param path The path of the file whose diff this event refers to.
     * @param ready Whether the diff is ready to be displayed or not.
     */
    onDiffReady(path: string, ready: boolean) {
        const diffInformation = this.repositoryDiffInformation().diffInformations;
        const index = diffInformation.findIndex((info) => info.modifiedPath === path);

        if (index !== -1) {
            diffInformation[index].diffReady = ready;
        } else {
            captureException(`Received diff ready event for unknown path: ${path}`);
        }

        this.allDiffsReady.set(diffInformation.every((info) => info.diffReady));
    }
}
