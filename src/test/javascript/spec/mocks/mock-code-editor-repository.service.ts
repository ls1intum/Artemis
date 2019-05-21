import { Observable } from 'rxjs/Observable';
import { ICodeEditorRepositoryService } from 'app/code-editor/service';

export class MockCodeEditorRepositoryService implements ICodeEditorRepositoryService {
    isClean = () => Observable.of({ isClean: true });
    commit = () => Observable.of();
    pull = () => Observable.of();
}
