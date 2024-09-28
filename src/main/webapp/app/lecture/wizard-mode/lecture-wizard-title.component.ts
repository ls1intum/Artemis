import { Component, Input } from '@angular/core';
import { Lecture } from 'app/entities/lecture.model';
import { MonacoFormulaAction } from 'app/shared/monaco-editor/model/actions/monaco-formula.action';

@Component({
    selector: 'jhi-lecture-update-wizard-title',
    templateUrl: './lecture-wizard-title.component.html',
})
export class LectureUpdateWizardTitleComponent {
    @Input() currentStep: number;
    @Input() lecture: Lecture;

    domainActionsDescription = [new MonacoFormulaAction()];

    constructor() {}
}
