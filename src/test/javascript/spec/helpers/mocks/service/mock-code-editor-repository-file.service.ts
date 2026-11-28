import { Service } from '@angular/core';
import { EMPTY, Observable, of } from 'rxjs';
import { ICodeEditorRepositoryFileService, RepositoryWriteCoordinator } from 'app/programming/shared/code-editor/services/code-editor-repository.service';

@Service()
export class MockCodeEditorRepositoryFileService implements ICodeEditorRepositoryFileService {
    private writeCoordinator = new RepositoryWriteCoordinator();
    runExclusive<T>(operation: () => Observable<T>): Observable<T> {
        return this.writeCoordinator.run(operation);
    }
    getRestResourceUrl = () => 'api/programming/participations/1/repository';
    getRepositoryContent = () => EMPTY;

    getRepositoryContentForPlagiarismView = () => EMPTY;
    getFile = (fileName: string) => EMPTY;

    getFileAsBlob = (fileName: string) => EMPTY;

    getFileForPlagiarismView = (fileName: string) => EMPTY;
    createFile = (fileName: string) => EMPTY;
    createFolder = (fileName: string) => EMPTY;
    updateFileContent = (fileName: string, fileContent: string, restResourceUrl?: string) => EMPTY;
    updateFiles = (fileUpdates: Array<{ fileName: string; fileContent: string }>, thenCommit?: boolean, restResourceUrl?: string) => of({ fileName: undefined });
    renameFile = (fileName: string) => EMPTY;
    deleteFile = (fileName: string) => EMPTY;
}
