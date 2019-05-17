import { Injectable } from '@angular/core';
import { Observable } from 'rxjs/Observable';
import { ICodeEditorRepositoryFileService } from 'app/code-editor/service';

@Injectable({ providedIn: 'root' })
export class MockCodeEditorRepositoryFileService implements ICodeEditorRepositoryFileService {
    getRepositoryContent = () => Observable.of();
    getFile = (fileName: string) => Observable.of();
    createFile = (fileName: string) => Observable.of();
    createFolder = (fileName: string) => Observable.of();
    updateFileContent = (fileName: string) => Observable.of();
    updateFiles = (fileName: string) => Observable.of();
    renameFile = (fileName: string) => Observable.of();
    deleteFile = (fileName: string) => Observable.of();
}
