import { of } from 'rxjs';
import { GitConflictState, IConflictStateService } from 'app/code-editor/service/code-editor-conflict-state.service';

export class MockCodeEditorConflictStateService implements IConflictStateService {
    subscribeConflictState = () => of(GitConflictState.OK);
    notifyConflictState = (gitConflictState: GitConflictState) => {};
}
