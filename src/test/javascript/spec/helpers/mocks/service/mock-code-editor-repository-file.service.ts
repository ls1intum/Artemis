import { Injectable } from '@angular/core';
import { empty, of } from 'rxjs';
import { ICodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';

@Injectable({ providedIn: 'root' })
export class MockCodeEditorRepositoryFileService implements ICodeEditorRepositoryFileService {
    getRepositoryContent = () => empty();
    getFile = (fileName: string) => empty();
    createFile = (fileName: string) => empty();
    createFolder = (fileName: string) => empty();
    updateFileContent = (fileName: string) => empty();
    updateFiles = (fileUpdates: Array<{ fileName: string; fileContent: string }>) => of({ fileName: undefined });
    renameFile = (fileName: string) => empty();
    deleteFile = (fileName: string) => empty();
}
