import { Injectable, OnDestroy } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { filter } from 'rxjs/operators';
import { ResizeType } from 'app/code-editor/model/code-editor.model';

export class ICodeEditorGridService {
    subscribeForResizeEvents: (byTypes: ResizeType[]) => Observable<ResizeType>;
    submitResizeEvent: (resizeType: ResizeType) => void;
}

@Injectable({ providedIn: 'root' })
export class CodeEditorGridService implements ICodeEditorGridService, OnDestroy {
    private resizeSubject = new Subject<ResizeType>();

    ngOnDestroy(): void {
        this.resizeSubject.complete();
    }

    public subscribeForResizeEvents = (byTypes: ResizeType[]) => {
        return this.resizeSubject.pipe(filter(resizeType => byTypes.includes(resizeType))) as Observable<ResizeType>;
    };

    public submitResizeEvent = (resizeType: ResizeType) => {
        this.resizeSubject.next(resizeType);
    };
}
