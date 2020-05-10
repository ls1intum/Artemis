import { Subject } from 'rxjs';
import { ICodeEditorGridService } from 'app/exercises/programming/shared/code-editor/service/code-editor-grid.service';
import { ResizeType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';

export class MockCodeEditorGridService implements ICodeEditorGridService {
    public subject = new Subject<ResizeType>();
    submitResizeEvent = (resizeType: ResizeType) => {};
    subscribeForResizeEvents = (byTypes: ResizeType[]) => this.subject;
}
