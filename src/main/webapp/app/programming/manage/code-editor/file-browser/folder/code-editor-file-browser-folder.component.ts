import { Component, input, output } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CodeEditorFileBrowserNodeComponent } from 'app/programming/manage/code-editor/file-browser/node/code-editor-file-browser-node.component';
import { faChevronDown, faChevronRight, faEdit, faFile, faFolder, faFolderOpen, faTrash } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CodeEditorFileBrowserBadgeComponent } from '../badge/code-editor-file-browser-badge.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FileBadge, FileType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { TreeViewItem } from 'app/programming/shared/code-editor/treeview/models/tree-view-item';

@Component({
    selector: 'jhi-code-editor-file-browser-folder',
    templateUrl: './code-editor-file-browser-folder.component.html',
    providers: [NgbModal],
    imports: [FaIconComponent, CodeEditorFileBrowserBadgeComponent, ArtemisTranslatePipe],
})
export class CodeEditorFileBrowserFolderComponent extends CodeEditorFileBrowserNodeComponent {
    onCollapseExpand = input.required<() => void>();
    isCompressed = input<boolean>(false);
    disableActions = input.required<boolean>();
    badges = input<FileBadge[]>([]);
    onSetCreatingNodeInFolder = output<{ item: TreeViewItem<string>; fileType: FileType }>();

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
        this.onSetCreatingNodeInFolder.emit({ item: this.item(), fileType });
    }
}
