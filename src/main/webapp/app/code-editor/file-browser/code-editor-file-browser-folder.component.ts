import { Component, ElementRef, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { WindowRef } from 'app/core/websocket/window.service';
import { TreeviewItem } from 'ngx-treeview';
import { FileType } from 'app/entities/ace-editor/file-change.model';
import { CodeEditorFileBrowserNodeComponent } from 'app/code-editor/file-browser/code-editor-file-browser-node.component';

@Component({
    selector: 'jhi-code-editor-file-browser-folder',
    templateUrl: './code-editor-file-browser-folder.component.html',
    providers: [NgbModal, WindowRef],
})
export class CodeEditorFileBrowserFolderComponent extends CodeEditorFileBrowserNodeComponent {
    @ViewChild('renamingInput', { static: false }) renamingInput: ElementRef;

    @Input() onCollapseExpand: () => void;
    @Input() isCompressed = false;
    @Input() disableActions: boolean;
    @Output() onSetCreatingNodeInFolder = new EventEmitter<{ item: TreeviewItem; fileType: FileType }>();

    setCreatingNodeInFolder(event: any, fileType: FileType) {
        event.stopPropagation();
        this.onSetCreatingNodeInFolder.emit({ item: this.item, fileType });
    }
}
