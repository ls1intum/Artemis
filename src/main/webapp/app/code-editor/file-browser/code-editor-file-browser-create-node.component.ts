import { AfterViewInit, Component, ElementRef, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { WindowRef } from 'app/core/websocket/window.service';
import { FileType } from 'app/entities/ace-editor/file-change.model';

@Component({
    selector: 'jhi-code-editor-file-browser-create-node',
    templateUrl: './code-editor-file-browser-create-node.component.html',
    providers: [NgbModal, WindowRef],
})
export class CodeEditorFileBrowserCreateNodeComponent implements AfterViewInit {
    FileType = FileType;

    @ViewChild('creatingInput', { static: false }) creatingInput: ElementRef;

    @Input() createFileType: FileType;
    @Input() folder: string;
    @Output() onCreateFile = new EventEmitter<string>();
    @Output() onClearCreatingFile = new EventEmitter<void>();

    createFile(event: any) {
        if (!event.target.value) {
            this.onClearCreatingFile.emit();
            return;
        }
        this.onCreateFile.emit(event.target.value);
    }

    ngAfterViewInit(): void {
        this.creatingInput.nativeElement.focus();
    }
}
