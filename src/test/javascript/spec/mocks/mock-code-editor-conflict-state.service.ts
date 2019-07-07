import { GitConflictState, IConflictStateService } from 'app/code-editor';
import { of } from 'rxjs';

export class MockCodeEditorConflictStateService implements IConflictStateService {
    subscribeConflictState = () => of(GitConflictState.OK);
    notifyConflictState = (gitConflictState: GitConflictState) => {};
}
