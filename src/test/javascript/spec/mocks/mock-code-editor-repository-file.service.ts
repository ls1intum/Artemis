import { Injectable } from '@angular/core';
import { Observable } from 'rxjs/Observable';
import { ICodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';

@Injectable({ providedIn: 'root' })
export class MockCodeEditorRepositoryFileService implements ICodeEditorRepositoryFileService {
    getRepositoryContent = () => Observable.empty();
    getFile = (fileName: string) => Observable.empty();
    createFile = (fileName: string) => Observable.empty();
    createFolder = (fileName: string) => Observable.empty();
    updateFileContent = (fileName: string) => Observable.empty();
    updateFiles = (fileUpdates: Array<{ fileName: string; fileContent: string }>) => Observable.of({ fileName: null });
    renameFile = (fileName: string) => Observable.empty();
    deleteFile = (fileName: string) => Observable.empty();
}
