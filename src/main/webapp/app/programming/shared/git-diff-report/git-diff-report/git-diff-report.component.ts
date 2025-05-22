import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { faAngleDown, faAngleUp, faSpinner, faTableColumns } from '@fortawesome/free-solid-svg-icons';
import { ButtonComponent, ButtonSize, ButtonType, TooltipPlacement } from 'app/shared/components/buttons/button/button.component';
import { GitDiffLineStatComponent } from 'app/programming/shared/git-diff-report/git-diff-line-stat/git-diff-line-stat.component';

import { GitDiffFilePanelTitleComponent } from 'app/programming/shared/git-diff-report/git-diff-file-panel-title/git-diff-file-panel-title.component';
import { GitDiffFileComponent } from 'app/programming/shared/git-diff-report/git-diff-file/git-diff-file.component';
import { captureException } from '@sentry/angular';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbAccordionModule, NgbCollapse, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { RepositoryDiffInformation } from 'app/programming/shared/utils/diff.utils';

@Component({
    selector: 'jhi-git-diff-report',
    templateUrl: './git-diff-report.component.html',
    styleUrls: ['./git-diff-report.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        GitDiffLineStatComponent,
        GitDiffFilePanelTitleComponent,
        GitDiffFileComponent,
        ArtemisTranslatePipe,
        FontAwesomeModule,
        NgbTooltipModule,
        ButtonComponent,
        NgbAccordionModule,
        NgbCollapse,
    ],
})
export class GitDiffReportComponent {
    protected readonly faSpinner = faSpinner;
    protected readonly faTableColumns = faTableColumns;
    protected readonly faAngleUp = faAngleUp;
    protected readonly faAngleDown = faAngleDown;
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
    readonly addedLineCount = computed(() => this.repositoryDiffInformation().totalLineChange.addedLineCount);
    readonly removedLineCount = computed(() => this.repositoryDiffInformation().totalLineChange.removedLineCount);

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

    /**
     * Handles the diff ready event from a GitDiffFileComponent
     * @param path The path of the file whose diff this event refers to
     * @param ready Whether the diff is ready to be displayed or not
     */
    handleDiffReady(path: string, ready: boolean): void {
        this.onDiffReady(path, ready);
    }
}
