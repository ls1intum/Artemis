import { Component, ElementRef, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { FileType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { CodeEditorFileBrowserNodeComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-node.component';
import { faChevronDown, faChevronRight, faEdit, faFile, faFolder, faFolderOpen, faTrash } from '@fortawesome/free-solid-svg-icons';
import { TreeviewItem } from 'app/exercises/programming/shared/code-editor/treeview/models/treeview-item';

@Component({
    selector: 'jhi-code-editor-file-browser-folder',
    templateUrl: './code-editor-file-browser-folder.component.html',
    providers: [NgbModal],
})
export class CodeEditorFileBrowserFolderComponent extends CodeEditorFileBrowserNodeComponent {
    @ViewChild('renamingInput', { static: false }) renamingInput: ElementRef;

    @Input() onCollapseExpand: () => void;
    @Input() isCompressed = false;
    @Input() disableActions: boolean;
    @Output() onSetCreatingNodeInFolder = new EventEmitter<{ item: TreeviewItem<string>; fileType: FileType }>();

    // Icons
    faTrash = faTrash;
    faEdit = faEdit;
    faFolder = faFolder;
    faFile = faFile;
    faChevronRight = faChevronRight;
    faChevronDown = faChevronDown;
    faFolderOpen = faFolderOpen;

    setCreatingNodeInFolder(event: any, fileType: FileType) {
        event.stopPropagation();
        this.onSetCreatingNodeInFolder.emit({ item: this.item, fileType });
    }
}
