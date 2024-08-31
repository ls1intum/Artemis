import { Component, Input } from '@angular/core';
import { Lecture } from 'app/entities/lecture.model';
import { FormulaAction } from 'app/shared/monaco-editor/model/actions/formula.action';

@Component({
    selector: 'jhi-lecture-update-wizard-title',
    templateUrl: './lecture-wizard-title.component.html',
})
export class LectureUpdateWizardTitleComponent {
    @Input() currentStep: number;
    @Input() lecture: Lecture;

    domainActionsDescription = [new FormulaAction()];

    constructor() {}
}
