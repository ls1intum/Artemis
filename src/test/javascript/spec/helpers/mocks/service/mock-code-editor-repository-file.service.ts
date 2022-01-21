import { Injectable } from '@angular/core';
import { EMPTY, of } from 'rxjs';
import { ICodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';

@Injectable({ providedIn: 'root' })
export class MockCodeEditorRepositoryFileService implements ICodeEditorRepositoryFileService {
    getRepositoryContent = () => EMPTY;
    getFile = (fileName: string) => EMPTY;
    createFile = (fileName: string) => EMPTY;
    createFolder = (fileName: string) => EMPTY;
    updateFileContent = (fileName: string) => EMPTY;
    updateFiles = (fileUpdates: Array<{ fileName: string; fileContent: string }>) => of({ fileName: undefined });
    renameFile = (fileName: string) => EMPTY;
    deleteFile = (fileName: string) => EMPTY;
}
