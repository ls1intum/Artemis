import { Component } from '@angular/core';
import { Subject } from 'rxjs';

@Component({
    selector: 'jhi-plagiarism-inspector',
    styleUrls: ['./plagiarism-inspector.component.scss'],
    templateUrl: './plagiarism-inspector.component.html',
})
export class PlagiarismInspectorComponent {
    splitControlSubject: Subject<string> = new Subject<string>();

    handleSplit(pane: string) {
        this.splitControlSubject.next(pane);
    }
}
