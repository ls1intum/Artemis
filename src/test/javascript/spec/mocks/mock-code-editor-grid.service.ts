import { Subject } from 'rxjs';
import { ICodeEditorGridService, ResizeType } from 'app/code-editor/service/code-editor-grid.service';

export class MockCodeEditorGridService implements ICodeEditorGridService {
    public subject = new Subject<ResizeType>();
    submitResizeEvent = (resizeType: ResizeType) => {};
    subscribeForResizeEvents = (byTypes: ResizeType[]) => this.subject;
}
