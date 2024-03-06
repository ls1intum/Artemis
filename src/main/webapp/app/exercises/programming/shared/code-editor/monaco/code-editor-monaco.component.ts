import { ChangeDetectorRef, Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild, ViewEncapsulation } from '@angular/core';
import { RepositoryFileService } from 'app/exercises/shared/result/repository.service';
import { CodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { CodeEditorFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-file.service';
import { LocalStorageService } from 'ngx-webstorage';
import { EditorPosition, MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { firstValueFrom } from 'rxjs';

export type FileSession = { [fileName: string]: { code: string; cursorPosition: EditorPosition; loadingError: boolean } };

@Component({
    selector: 'jhi-code-editor-monaco',
    templateUrl: './code-editor-monaco.component.html',
    styleUrls: ['./code-editor-monaco.component.scss'],
    encapsulation: ViewEncapsulation.None,
    providers: [RepositoryFileService],
})
export class CodeEditorMonacoComponent implements OnChanges {
    @ViewChild('editor', { static: true })
    editor: MonacoEditorComponent;
    @Input()
    selectedFile: string | undefined = undefined;

    @Output()
    onFileContentChange = new EventEmitter<{ file: string; fileContent: string }>();

    isLoading = false;

    private fileSession: FileSession = {};

    constructor(
        private repositoryFileService: CodeEditorRepositoryFileService,
        private fileService: CodeEditorFileService,
        protected localStorageService: LocalStorageService,
        private changeDetectorRef: ChangeDetectorRef,
    ) {}

    async ngOnChanges(changes: SimpleChanges): Promise<void> {
        if (changes.selectedFile) {
            await this.selectFileInEditor(changes.selectedFile.currentValue);
        }
    }

    async selectFileInEditor(fileName: string): Promise<void> {
        if (!this.fileSession[fileName]) {
            this.isLoading = true;
            const fileContent = await firstValueFrom(this.repositoryFileService.getFile(fileName)).then((fileObj) => fileObj.fileContent);
            this.fileSession[fileName] = { code: fileContent, loadingError: false, cursorPosition: { column: 0, lineNumber: 0 } };
            this.isLoading = false;
        }

        if (this.selectedFile === fileName) {
            //this.editor.setText(this.fileSession[fileName].code);
            this.editor.changeModel(fileName, this.fileSession[fileName].code);
            this.editor.setPosition(this.fileSession[fileName].cursorPosition);
        }
    }

    onFileTextChanged(text: string): void {
        if (this.selectedFile && this.fileSession[this.selectedFile]) {
            const previousText = this.fileSession[this.selectedFile].code;
            if (previousText !== text) {
                this.fileSession[this.selectedFile] = { code: text, loadingError: false, cursorPosition: this.editor.getPosition() };
                this.onFileContentChange.emit({ file: this.selectedFile, fileContent: text });
            }
        }
    }

    updateTabSize() {}
}
