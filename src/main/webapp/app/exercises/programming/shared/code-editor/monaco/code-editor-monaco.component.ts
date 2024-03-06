import { ChangeDetectorRef, Component, Input, OnChanges, SimpleChanges, ViewChild, ViewEncapsulation } from '@angular/core';
import { RepositoryFileService } from 'app/exercises/shared/result/repository.service';
import { CodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { CodeEditorFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-file.service';
import { LocalStorageService } from 'ngx-webstorage';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { firstValueFrom } from 'rxjs';

export type FileSession = { [fileName: string]: { code: string; cursor: { column: number; row: number }; loadingError: boolean } };

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
            this.fileSession[fileName] = { code: fileContent, loadingError: false, cursor: { column: 0, row: 0 } };
            this.isLoading = false;
        }

        if (this.selectedFile === fileName) {
            //this.editor.setText(this.fileSession[fileName].code);
            this.editor.changeModel(fileName, this.fileSession[fileName].code);
        }
    }

    updateTabSize() {}
}
