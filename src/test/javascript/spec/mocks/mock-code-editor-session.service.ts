import { ICodeEditorSessionService } from 'app/code-editor/service/icode-editor-session.service';

export class MockCodeEditorSessionService implements ICodeEditorSessionService {
    storeSession = () => {};
    loadSession = () => null;
}
