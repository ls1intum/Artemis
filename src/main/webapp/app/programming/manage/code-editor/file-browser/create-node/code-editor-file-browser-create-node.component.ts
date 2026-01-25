import { AfterViewInit, Component, ElementRef, ViewChild, input, output } from '@angular/core';
import { faFile, faFolder } from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { FileType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-code-editor-file-browser-create-node',
    templateUrl: './code-editor-file-browser-create-node.component.html',
    styleUrls: ['./code-editor-file-browser-create-node.component.scss'],
    providers: [NgbModal],
    imports: [FaIconComponent],
})
export class CodeEditorFileBrowserCreateNodeComponent implements AfterViewInit {
    FileType = FileType;
    // Icons
    faFile = faFile;
    faFolder = faFolder;

    @ViewChild('creatingInput', { static: false }) creatingInput: ElementRef;

    createFileType = input.required<FileType>();
    folder = input.required<string>();
    onCreateFile = output<string>();
    onClearCreatingFile = output<Event>();

    createFile(event: any) {
        if (!event.target.value) {
            this.onClearCreatingFile.emit(event);
            return;
        }
        this.onCreateFile.emit(event.target.value);
    }

    ngAfterViewInit(): void {
        this.creatingInput.nativeElement.focus();
    }
}
