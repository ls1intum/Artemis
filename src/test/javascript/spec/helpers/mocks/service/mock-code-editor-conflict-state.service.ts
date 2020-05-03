import { of } from 'rxjs';
import { IConflictStateService } from 'app/exercises/programming/shared/code-editor/service/code-editor-conflict-state.service';
import { GitConflictState } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';

export class MockCodeEditorConflictStateService implements IConflictStateService {
    subscribeConflictState = () => of(GitConflictState.OK);
    notifyConflictState = (gitConflictState: GitConflictState) => {};
}
