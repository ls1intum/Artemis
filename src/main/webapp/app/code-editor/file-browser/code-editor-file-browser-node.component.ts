import { ElementRef, EventEmitter, Input, Output, OnChanges, SimpleChanges, ViewChild } from '@angular/core';
import { TreeviewItem } from 'ngx-treeview';
import { FileType } from 'app/entities/ace-editor/file-change.model';

export abstract class CodeEditorFileBrowserNodeComponent implements OnChanges {
    FileType = FileType;

    @ViewChild('renamingInput') renamingInput: ElementRef;

    @Input() item: TreeviewItem;
    @Input() hasError = false;
    @Input() hasUnsavedChanges = false;
    @Input() isBeingRenamed = false;

    @Output() onNodeSelect = new EventEmitter<TreeviewItem>();
    @Output() onSetRenamingNode = new EventEmitter<TreeviewItem>();
    @Output() onClearRenamingNode = new EventEmitter<void>();
    @Output() onRenameNode = new EventEmitter<{ item: TreeviewItem; newFileName: string }>();
    @Output() onDeleteNode = new EventEmitter<TreeviewItem>();

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.isBeingRenamed && this.isBeingRenamed) {
            setTimeout(() => {
                if (this.renamingInput) {
                    this.renamingInput.nativeElement.focus();
                }
            }, 0);
        }
    }

    setRenamingNode(event: any) {
        event.stopPropagation();
        this.onSetRenamingNode.emit(this.item);
    }

    clearRenamingNode(event: any) {
        event.stopPropagation();
        this.onClearRenamingNode.emit();
    }

    renameNode(event: any) {
        if (!event.target.value || !this.isBeingRenamed) {
            return;
        } else if (event.target.value === this.item.text) {
            this.onClearRenamingNode.emit();
            return;
        }
        this.onRenameNode.emit({ item: this.item, newFileName: event.target.value });
    }

    deleteNode(event: any) {
        event.stopPropagation();
        this.onDeleteNode.emit(this.item);
    }
}
