import { Observable } from 'rxjs/Observable';
import { ICodeEditorRepositoryService } from 'app/code-editor/service/code-editor-repository.service';

export class MockCodeEditorRepositoryService implements ICodeEditorRepositoryService {
    getStatus = () => Observable.of({ repositoryStatus: 'CLEAN' });
    commit = () => Observable.empty();
    pull = () => Observable.empty();
    resetRepository = () => Observable.empty();
}
