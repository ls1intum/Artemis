import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';

export enum CollapsableCodeEditorElement {
    FILE_BROWSER,
    RIGHT_PANEL, // problem statement or instructions
    BUILD_OUTPUT,
}

@Injectable()
export class CodeEditorCollapseService {
    private collapseSubject = new Subject<CollapsableCodeEditorElement>();

    constructor() {}

    sendToggleCollapseEvent(codeEditorElement: CollapsableCodeEditorElement) {
        this.collapseSubject.next(codeEditorElement);
    }

    getToggleCollapseEvent(): Observable<any> {
        return this.collapseSubject.asObservable();
    }
}
