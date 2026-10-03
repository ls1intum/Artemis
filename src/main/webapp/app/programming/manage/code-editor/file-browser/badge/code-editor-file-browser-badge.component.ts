import { Component, computed, inject, input } from '@angular/core';
import { NgbModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faComments } from '@fortawesome/free-solid-svg-icons';
import { FileBadge, FileBadgeType } from 'app/programming/shared/code-editor/model/code-editor.model';

@Component({
    selector: 'jhi-file-browser-badge',
    templateUrl: './code-editor-file-browser-badge.component.html',
    styleUrls: ['./code-editor-file-browser-badge.component.scss'],
    providers: [NgbModal],
    imports: [NgbTooltip, FaIconComponent],
})
export class CodeEditorFileBrowserBadgeComponent {
    private translateService = inject(TranslateService);

    badge = input.required<FileBadge>();
    /** Whether the badge sits on a selected, changed or erroneous row, whose color it then takes. */
    onColoredBackground = input<boolean>(false);

    /**
     * The feedback count is a bare pill like every other count of feedback. A review comment count can sit beside it in
     * the instructor editor, so it keeps an icon to tell the two apart.
     */
    readonly isReviewComment = computed(() => this.badge().type === FileBadgeType.REVIEW_COMMENT);

    protected readonly faComments = faComments;

    get tooltip(): string | undefined {
        switch (this.badge().type) {
            case FileBadgeType.FEEDBACK_SUGGESTION:
                return this.translateService.instant('artemisApp.editor.fileBrowser.fileBadgeTooltips.feedbackSuggestions');
            case FileBadgeType.REVIEW_COMMENT:
                return this.translateService.instant('artemisApp.editor.fileBrowser.fileBadgeTooltips.reviewComments');
            default:
                return undefined;
        }
    }
}
