import { Component, ElementRef, Input, ViewChild } from '@angular/core';
import { faEdit, faFile, faTrash } from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CodeEditorFileBrowserNodeComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-node.component';

@Component({
    selector: 'jhi-code-editor-file-browser-file',
    templateUrl: './code-editor-file-browser-file.component.html',
    providers: [NgbModal],
})
export class CodeEditorFileBrowserFileComponent extends CodeEditorFileBrowserNodeComponent {
    @ViewChild('renamingInput', { static: false }) renamingInput: ElementRef;

    @Input() disableActions: boolean;
    @Input() hasChanges = false;

    // Icons
    faTrash = faTrash;
    faEdit = faEdit;
    faFile = faFile;
}
