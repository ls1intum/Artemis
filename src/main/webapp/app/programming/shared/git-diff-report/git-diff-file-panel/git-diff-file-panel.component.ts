import { ChangeDetectionStrategy, Component, ViewEncapsulation, computed, input, output } from '@angular/core';
import { faAngleDown, faAngleUp } from '@fortawesome/free-solid-svg-icons';
import { GitDiffFilePanelTitleComponent } from 'app/programming/shared/git-diff-report/git-diff-file-panel-title/git-diff-file-panel-title.component';
import { GitDiffLineStatComponent } from 'app/programming/shared/git-diff-report/git-diff-line-stat/git-diff-line-stat.component';
import { GitDiffFileComponent } from 'app/programming/shared/git-diff-report/git-diff-file/git-diff-file.component';

import { NgbAccordionModule, NgbCollapse, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { DiffInformation } from 'app/shared/monaco-editor/diff-editor/util/monaco-diff-editor.util';

@Component({
    selector: 'jhi-git-diff-file-panel',
    templateUrl: './git-diff-file-panel.component.html',
    styleUrls: ['./git-diff-file-panel.component.scss'],
    encapsulation: ViewEncapsulation.None,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        GitDiffFilePanelTitleComponent,
        GitDiffLineStatComponent,
        GitDiffFileComponent,
        NgbAccordionModule,
        NgbCollapse,
        ArtemisTranslatePipe,
        FontAwesomeModule,
        NgbTooltipModule,
    ],
})
export class GitDiffFilePanelComponent {
    protected readonly faAngleUp = faAngleUp;
    protected readonly faAngleDown = faAngleDown;

    readonly diffForTemplateAndSolution = input<boolean>(true);
    readonly allowSplitView = input<boolean>(true);
    readonly diffInformation = input.required<DiffInformation>();
    readonly onDiffReady = output<boolean>();

    readonly addedLineCount = computed(() => this.diffInformation().lineChange?.addedLineCount ?? 0);
    readonly removedLineCount = computed(() => this.diffInformation().lineChange?.removedLineCount ?? 0);

    handleDiffReady(ready: boolean): void {
        this.onDiffReady.emit(ready);
    }
}
