import { of, empty } from 'rxjs';
import { ICodeEditorRepositoryService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';

export class MockCodeEditorRepositoryService implements ICodeEditorRepositoryService {
    getStatus = () => of({ repositoryStatus: 'CLEAN' });
    commit = () => empty();
    pull = () => empty();
    resetRepository = () => empty();
}
