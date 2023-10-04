import { Component, Input } from '@angular/core';
import { faEdit, faFile, faTrash } from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CodeEditorFileBrowserNodeComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-node.component';
import { FileBadge } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';

@Component({
    selector: 'jhi-code-editor-file-browser-file',
    templateUrl: './code-editor-file-browser-file.component.html',
    providers: [NgbModal],
})
export class CodeEditorFileBrowserFileComponent extends CodeEditorFileBrowserNodeComponent {
    @Input() disableActions: boolean;
    @Input() hasChanges = false;
    @Input() badges: FileBadge[] = [];

    // Icons
    faTrash = faTrash;
    faEdit = faEdit;
    faFile = faFile;
}
