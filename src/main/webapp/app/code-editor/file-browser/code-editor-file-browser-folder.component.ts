import { Component, ElementRef, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { WindowRef } from 'app/core';
import { TreeviewItem } from 'ngx-treeview';
import { FileType } from 'app/entities/ace-editor/file-change.model';
import { CodeEditorFileBrowserNodeComponent } from 'app/code-editor/file-browser/code-editor-file-browser-node.component';

@Component({
    selector: 'jhi-code-editor-file-browser-folder',
    templateUrl: './code-editor-file-browser-folder.component.html',
    providers: [NgbModal, WindowRef],
})
export class CodeEditorFileBrowserFolderComponent extends CodeEditorFileBrowserNodeComponent {
    @ViewChild('renamingInput') renamingInput: ElementRef;

    @Input() onCollapseExpand: () => void;
    @Input() isCompressed = false;
    @Output() onSetCreatingNodeInFolder = new EventEmitter<{ item: TreeviewItem; fileType: FileType }>();

    setCreatingNodeInFolder(event: any, fileType: FileType) {
        event.stopPropagation();
        this.onSetCreatingNodeInFolder.emit({ item: this.item, fileType });
    }
}
