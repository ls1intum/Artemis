import { Component, Input } from '@angular/core';
import { faLightbulb, IconDefinition } from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { FileBadge, FileBadgeType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';

@Component({
    selector: 'jhi-file-browser-badge',
    templateUrl: './code-editor-file-browser-badge.component.html',
    styleUrls: ['./code-editor-file-browser-badge.component.scss'],
    providers: [NgbModal],
})
export class CodeEditorFileBrowserBadgeComponent {
    @Input() badge: FileBadge;
    @Input() onColoredBackground: boolean = false; // Only slightly darken the background and use white text

    constructor(private translateService: TranslateService) {}

    get tooltip(): string | undefined {
        switch (this.badge.type) {
            case FileBadgeType.FEEDBACK_SUGGESTION:
                return this.translateService.instant('artemisApp.editor.fileBrowser.fileBadgeTooltips.feedbackSuggestions');
            case FileBadgeType.PRELIMINARY_FEEDBACK:
                return this.translateService.instant('artemisApp.editor.fileBrowser.fileBadgeTooltips.preliminaryFeedback');
            default:
                return undefined;
        }
    }

    get icon(): IconDefinition | undefined {
        switch (this.badge.type) {
            case FileBadgeType.FEEDBACK_SUGGESTION:
                return faLightbulb;
            case FileBadgeType.PRELIMINARY_FEEDBACK:
                return faLightbulb;
            default:
                return undefined;
        }
    }
}
