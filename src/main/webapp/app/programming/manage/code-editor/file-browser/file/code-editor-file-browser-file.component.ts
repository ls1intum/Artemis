import { Component, input } from '@angular/core';
import { faEdit, faFile, faTrash } from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CodeEditorFileBrowserNodeComponent } from 'app/programming/manage/code-editor/file-browser/node/code-editor-file-browser-node.component';
import { FileBadge } from 'app/programming/shared/code-editor/model/code-editor.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgClass } from '@angular/common';
import { CodeEditorFileBrowserBadgeComponent } from '../badge/code-editor-file-browser-badge.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-code-editor-file-browser-file',
    templateUrl: './code-editor-file-browser-file.component.html',
    providers: [NgbModal],
    imports: [FaIconComponent, NgClass, CodeEditorFileBrowserBadgeComponent, ArtemisTranslatePipe],
})
export class CodeEditorFileBrowserFileComponent extends CodeEditorFileBrowserNodeComponent {
    disableActions = input.required<boolean>();
    hasChanges = input<boolean>(false);
    badges = input<FileBadge[]>([]);

    // Icons
    faTrash = faTrash;
    faEdit = faEdit;
    faFile = faFile;
}
