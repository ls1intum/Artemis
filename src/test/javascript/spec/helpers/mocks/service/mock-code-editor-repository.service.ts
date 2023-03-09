import { ICodeEditorRepositoryService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { EMPTY, of } from 'rxjs';

export class MockCodeEditorRepositoryService implements ICodeEditorRepositoryService {
    getStatus = () => of({ repositoryStatus: 'CLEAN' });
    commit = () => EMPTY;
    pull = () => EMPTY;
    resetRepository = () => EMPTY;
}
