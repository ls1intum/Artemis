import { Component, Input, OnInit } from '@angular/core';
import { KatexCommand } from 'app/shared/markdown-editor/commands/katex.command';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';
import { Lecture, requiresChannelName } from 'app/entities/lecture.model';
import { isMessagingEnabled } from 'app/entities/course.model';

@Component({
    selector: 'jhi-lecture-update-wizard-title',
    templateUrl: './lecture-wizard-title.component.html',
})
export class LectureUpdateWizardTitleComponent implements OnInit {
    @Input() currentStep: number;
    @Input() lecture: Lecture;

    hideChannelNameInput = false;

    domainCommandsDescription = [new KatexCommand()];
    EditorMode = EditorMode;

    constructor() {}

    ngOnInit(): void {
        this.hideChannelNameInput = !requiresChannelName(this.lecture);
    }

    protected readonly isMessagingEnabled = isMessagingEnabled;
}
