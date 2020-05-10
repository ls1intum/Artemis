import { ICodeEditorSessionService } from 'app/exercises/programming/shared/code-editor/service/icode-editor-session.service';

export class MockCodeEditorSessionService implements ICodeEditorSessionService {
    storeSession = () => {};
    loadSession = () => null;
}
