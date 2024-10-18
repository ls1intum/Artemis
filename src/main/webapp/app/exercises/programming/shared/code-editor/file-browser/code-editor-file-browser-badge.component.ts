import { Component, Input, inject } from '@angular/core';
import { IconDefinition, faLightbulb } from '@fortawesome/free-solid-svg-icons';
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
    private translateService = inject(TranslateService);

    @Input() badge: FileBadge;
    @Input() onColoredBackground: boolean = false;

    get tooltip(): string | undefined {
        switch (this.badge.type) {
            case FileBadgeType.FEEDBACK_SUGGESTION:
                return this.translateService.instant('artemisApp.editor.fileBrowser.fileBadgeTooltips.feedbackSuggestions');
            default:
                return undefined;
        }
    }

    get icon(): IconDefinition | undefined {
        switch (this.badge.type) {
            case FileBadgeType.FEEDBACK_SUGGESTION:
                return faLightbulb;
            default:
                return undefined;
        }
    }
}
