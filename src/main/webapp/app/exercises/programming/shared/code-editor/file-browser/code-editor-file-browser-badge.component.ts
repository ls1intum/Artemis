import { Component, Input } from '@angular/core';
import { IconDefinition, faLightbulb } from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
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

    getIcon(): IconDefinition | undefined {
        switch (this.badge.type) {
            case FileBadgeType.FEEDBACK_SUGGESTION:
                return faLightbulb;
            default:
                return undefined;
        }
    }
}
