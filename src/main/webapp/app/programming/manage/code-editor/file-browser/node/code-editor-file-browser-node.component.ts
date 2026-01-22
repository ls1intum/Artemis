import { Component, ElementRef, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild } from '@angular/core';
import { FileType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { TreeViewItem } from 'app/programming/shared/code-editor/treeview/models/tree-view-item';

@Component({
    template: '',
})
export abstract class CodeEditorFileBrowserNodeComponent implements OnChanges {
    FileType = FileType;

    @ViewChild('renamingInput', { static: false }) renamingInput: ElementRef;

    @Input() item: TreeViewItem<string>;
    @Input() hasError = false;
    @Input() hasUnsavedChanges = false;
    @Input() isBeingRenamed = false;

    @Output() onNodeSelect = new EventEmitter<TreeViewItem<string>>();
    @Output() onSetRenamingNode = new EventEmitter<TreeViewItem<string>>();
    @Output() onClearRenamingNode = new EventEmitter<void>();
    @Output() onRenameNode = new EventEmitter<{ item: TreeViewItem<string>; newFileName: string }>();
    @Output() onDeleteNode = new EventEmitter<TreeViewItem<string>>();

    /**
     * Check if the node is being renamed now, if so, focus the input when the view is rendered.
     * @param changes
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (changes.isBeingRenamed && this.isBeingRenamed) {
            // Timeout is needed to wait for view to render.
            setTimeout(() => {
                if (this.renamingInput) {
                    this.renamingInput.nativeElement.focus();
                }
            }, 0);
        }
    }

    /**
     * Emit that this node should be renamed.
     * @param event
     */
    setRenamingNode(event: any) {
        event.stopPropagation();
        this.onSetRenamingNode.emit(this.item);
    }

    /**
     * Stop renaming this node.
     * @param event
     */
    clearRenamingNode(event: any) {
        event.stopPropagation();
        // TODO: The 'emit' function requires a mandatory void argument
        this.onClearRenamingNode.emit();
    }

    /**
     * Send an event to the parent with the new name of the node.
     * @param event
     */
    renameNode(event: any) {
        if (!event.target.value || !this.isBeingRenamed) {
            return;
        } else if (event.target.value === this.item.text) {
            // TODO: The 'emit' function requires a mandatory void argument
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
        this.onDeleteNode.emit(this.item);
    }
}
