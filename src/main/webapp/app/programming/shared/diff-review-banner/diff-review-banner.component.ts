import { ChangeDetectionStrategy, Component, input, model, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBan, faSave, faTableColumns } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { TumAetUiMessageComponent } from '@tumaet/ui-angular/message';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular/tooltip';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { GitDiffLineStatComponent } from 'app/programming/shared/git-diff-report/git-diff-line-stat/git-diff-line-stat.component';

@Component({
    selector: 'jhi-diff-review-banner',
    templateUrl: './diff-review-banner.component.html',
    imports: [TumAetUiMessageComponent, TumAetUiButtonDirective, TumAetUiTooltipDirective, FaIconComponent, TranslateDirective, ArtemisTranslatePipe, GitDiffLineStatComponent],
    host: { class: 'block mb-1' },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DiffReviewBannerComponent {
    readonly allowSplitView = model(false);
    readonly actionsDisabled = input(false);
    readonly addedLineCount = input(0);
    readonly removedLineCount = input(0);
    readonly revertRequested = output<void>();
    readonly doneRequested = output<void>();

    protected readonly faTableColumns = faTableColumns;
    protected readonly faBan = faBan;
    protected readonly faSave = faSave;
}
