import { ChangeDetectionStrategy, Component, ViewEncapsulation, computed, input, output, signal } from '@angular/core';
import { faAngleDown, faAngleUp } from '@fortawesome/free-solid-svg-icons';
import { GitDiffFilePanelTitleComponent } from 'app/programming/shared/git-diff-report/git-diff-file-panel-title/git-diff-file-panel-title.component';
import { GitDiffLineStatComponent } from 'app/programming/shared/git-diff-report/git-diff-line-stat/git-diff-line-stat.component';
import { GitDiffFileComponent } from 'app/programming/shared/git-diff-report/git-diff-file/git-diff-file.component';

import { NgbAccordionModule, NgbCollapse, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { DiffInformation, LineChange } from 'app/shared/monaco-editor/diff-editor/util/monaco-diff-editor.util';

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
    readonly onDiffReady = output<{ ready: boolean; lineChange: LineChange }>();

    private readonly lineChange = signal<LineChange>({ addedLineCount: 0, removedLineCount: 0 });

    readonly addedLineCount = computed(() => this.lineChange().addedLineCount);
    readonly removedLineCount = computed(() => this.lineChange().removedLineCount);

    handleDiffReady(event: { ready: boolean; lineChange: LineChange }): void {
        if (event.ready && event.lineChange) {
            this.lineChange.set(event.lineChange);
        }
        this.onDiffReady.emit(event);
    }
}
