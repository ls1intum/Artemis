import { AfterViewInit, Component, ElementRef, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { faFile, faFolder } from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { FileType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';

@Component({
    selector: 'jhi-code-editor-file-browser-create-node',
    templateUrl: './code-editor-file-browser-create-node.component.html',
    styleUrls: ['./code-editor-file-browser-create-node.component.scss'],
    providers: [NgbModal],
})
export class CodeEditorFileBrowserCreateNodeComponent implements AfterViewInit {
    FileType = FileType;
    // Icons
    faFile = faFile;
    faFolder = faFolder;

    @ViewChild('creatingInput', { static: false }) creatingInput: ElementRef;

    @Input() createFileType: FileType;
    @Input() folder: string;
    @Output() onCreateFile = new EventEmitter<string>();
    @Output() onClearCreatingFile = new EventEmitter<Event>();

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
