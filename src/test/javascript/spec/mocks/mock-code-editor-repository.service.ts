import { Observable } from 'rxjs/Observable';
import { ICodeEditorRepositoryService } from 'app/code-editor/service';

export class MockCodeEditorRepositoryService implements ICodeEditorRepositoryService {
    getStatus = () => Observable.of({ repositoryStatus: 'CLEAN' });
    commit = () => Observable.of();
    pull = () => Observable.of();
    resetRepository = () => Observable.of();
}
