import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { Component, input, output } from '@angular/core';
import { CodeEditorFileBrowserNodeComponent } from 'app/programming/manage/code-editor/file-browser/node/code-editor-file-browser-node.component';
import { faChevronDown } from '@fortawesome/free-solid-svg-icons/faChevronDown';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons/faChevronRight';
import { faEdit } from '@fortawesome/free-solid-svg-icons/faEdit';
import { faFile } from '@fortawesome/free-solid-svg-icons/faFile';
import { faFolder } from '@fortawesome/free-solid-svg-icons/faFolder';
import { faFolderOpen } from '@fortawesome/free-solid-svg-icons/faFolderOpen';
import { faTrash } from '@fortawesome/free-solid-svg-icons/faTrash';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CodeEditorFileBrowserBadgeComponent } from '../badge/code-editor-file-browser-badge.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FileBadge, FileType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { TreeViewItem } from 'app/programming/shared/code-editor/treeview/models/tree-view-item';

@Component({
    selector: 'jhi-code-editor-file-browser-folder',
    templateUrl: './code-editor-file-browser-folder.component.html',
    imports: [TumAetUiButtonDirective, FaIconComponent, CodeEditorFileBrowserBadgeComponent, ArtemisTranslatePipe],
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

    setCreatingNodeInFolder(event: Event, fileType: FileType) {
        event.stopPropagation();
        this.onSetCreatingNodeInFolder.emit({ item: this.item(), fileType });
    }
}
