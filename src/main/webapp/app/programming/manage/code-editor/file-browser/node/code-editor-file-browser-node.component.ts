import { Component, ElementRef, ViewChild, effect, input, output } from '@angular/core';
import { FileType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { TreeViewItem } from 'app/programming/shared/code-editor/treeview/models/tree-view-item';

@Component({
    template: '',
})
export abstract class CodeEditorFileBrowserNodeComponent {
    FileType = FileType;

    @ViewChild('renamingInput', { static: false }) renamingInput: ElementRef;

    item = input.required<TreeViewItem<string>>();
    hasError = input<boolean>(false);
    hasUnsavedChanges = input<boolean>(false);
    isBeingRenamed = input<boolean>(false);

    onNodeSelect = output<TreeViewItem<string>>();
    onSetRenamingNode = output<TreeViewItem<string>>();
    onClearRenamingNode = output<void>();
    onRenameNode = output<string>();
    onDeleteNode = output<TreeViewItem<string>>();

    constructor() {
        effect(() => {
            if (this.isBeingRenamed()) {
                // Timeout is needed to wait for view to render.
                setTimeout(() => {
                    if (this.renamingInput) {
                        this.renamingInput.nativeElement.focus();
                    }
                }, 0);
            }
        });
    }

    /**
     * Emit that this node should be renamed.
     * @param event
     */
    setRenamingNode(event: any) {
        event.stopPropagation();
        this.onSetRenamingNode.emit(this.item());
    }

    /**
     * Stop renaming this node.
     * @param event
     */
    clearRenamingNode(event: any) {
        event.stopPropagation();
        this.onClearRenamingNode.emit();
    }

    /**
     * Send an event to the parent with the new name of the node.
     * @param event
     */
    renameNode(event: any) {
        if (!event.target.value || !this.isBeingRenamed()) {
            return;
        } else if (event.target.value === this.item().text) {
            this.onClearRenamingNode.emit();
            return;
        }
        this.onRenameNode.emit(event.target.value);
    }

    /**
     * Delete the node.
     * @param event
     */
    deleteNode(event: any) {
        event.stopPropagation();
        this.onDeleteNode.emit(this.item());
    }
}
