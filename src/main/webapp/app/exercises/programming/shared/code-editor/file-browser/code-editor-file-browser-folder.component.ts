import { Component, EventEmitter, Input, Output } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { FileBadge, FileType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { CodeEditorFileBrowserNodeComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-node.component';
import { faChevronDown, faChevronRight, faEdit, faFile, faFolder, faFolderOpen, faTrash } from '@fortawesome/free-solid-svg-icons';
import { TreeviewItem } from 'app/exercises/programming/shared/code-editor/treeview/models/treeview-item';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CodeEditorFileBrowserBadgeComponent } from './code-editor-file-browser-badge.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-code-editor-file-browser-folder',
    templateUrl: './code-editor-file-browser-folder.component.html',
    providers: [NgbModal],
    imports: [FaIconComponent, CodeEditorFileBrowserBadgeComponent, ArtemisTranslatePipe],
})
export class CodeEditorFileBrowserFolderComponent extends CodeEditorFileBrowserNodeComponent {
    @Input() onCollapseExpand: () => void;
    @Input() isCompressed = false;
    @Input() disableActions: boolean;
    @Input() badges: FileBadge[] = [];
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
