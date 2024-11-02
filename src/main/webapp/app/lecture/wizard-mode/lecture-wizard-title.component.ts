import { Component, Input } from '@angular/core';
import { Lecture } from 'app/entities/lecture.model';
import { FormulaAction } from 'app/shared/monaco-editor/model/actions/formula.action';
import { Subject } from 'rxjs';

@Component({
    selector: 'jhi-lecture-update-wizard-title',
    templateUrl: './lecture-wizard-title.component.html',
})
export class LectureUpdateTitleComponent {
    @Input() currentStep: number;
    @Input() lecture: Lecture;

    domainActionsDescription = [new FormulaAction()];

    formValid: boolean;
    formValidChanges = new Subject<boolean>();
}
