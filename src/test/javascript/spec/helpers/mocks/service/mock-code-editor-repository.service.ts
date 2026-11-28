import { EMPTY, of } from 'rxjs';
import { ICodeEditorRepositoryService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';

export class MockCodeEditorRepositoryService implements ICodeEditorRepositoryService {
    getRestResourceUrl = () => 'api/programming/participations/1/repository';
    getStatus = () => of({ repositoryStatus: 'CLEAN' });
    commit = (restResourceUrl?: string) => EMPTY;
    pull = () => EMPTY;
    resetRepository = () => EMPTY;
}
