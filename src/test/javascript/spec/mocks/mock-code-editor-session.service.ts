import { ICodeEditorSessionService } from 'app/code-editor/service/icode-editor-session.service';
import { Session } from 'app/entities/ace-editor';

export class MockCodeEditorSessionService implements ICodeEditorSessionService {
    storeSession = () => {};
    loadSession = () => ({} as Session);
}
