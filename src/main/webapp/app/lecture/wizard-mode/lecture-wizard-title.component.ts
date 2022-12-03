import { Component, Input } from '@angular/core';
import { KatexCommand } from 'app/shared/markdown-editor/commands/katex.command';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';
import { Lecture } from 'app/entities/lecture.model';

@Component({
    selector: 'jhi-lecture-update-wizard-title',
    templateUrl: './lecture-wizard-title.component.html',
})
export class LectureUpdateWizardTitleComponent {
    @Input() currentStep: number;
    @Input() lecture: Lecture;

    domainCommandsDescription = [new KatexCommand()];
    EditorMode = EditorMode;

    constructor() {}
}
