import { ICodeEditorGridService, ResizeType } from 'app/code-editor';
import { Subject } from 'rxjs';

export class MockCodeEditorGridService implements ICodeEditorGridService {
    public subject = new Subject<ResizeType>();
    submitResizeEvent = (resizeType: ResizeType) => {};
    subscribeForResizeEvents = (byTypes: ResizeType[]) => this.subject;
}
