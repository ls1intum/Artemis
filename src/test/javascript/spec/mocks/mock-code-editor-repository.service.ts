import { Observable } from 'rxjs/Observable';
import { IRepositoryService } from 'app/code-editor/service';

export class MockCodeEditorRepositoryService implements IRepositoryService {
    isClean = () => Observable.of();
    commit = () => Observable.of();
    pull = () => Observable.of();
}
