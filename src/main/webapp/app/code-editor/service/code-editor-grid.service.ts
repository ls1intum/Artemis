import { Injectable, OnDestroy } from '@angular/core';
import { of, Observable, Subject } from 'rxjs';
import { filter, map } from 'rxjs/operators';

export enum ResizeType {
    SIDEBAR_LEFT = 'SIDEBAR_LEFT',
    SIDEBAR_RIGHT = 'SIDEBAR_RIGHT',
    MAIN_BOTTOM = 'MAIN_BOTTOM',
    BOTTOM = 'BOTTOM',
}

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
