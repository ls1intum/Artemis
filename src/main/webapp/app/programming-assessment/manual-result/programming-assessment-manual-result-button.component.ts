import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { ButtonSize, ButtonType } from 'app/shared/components';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingAssessmentManualResultDialogComponent } from 'app/programming-assessment/manual-result/programming-assessment-manual-result-dialog.component';
import { Result } from 'app/entities/result';
import { AssessmentType } from 'app/entities/assessment-type';

@Component({
    selector: 'jhi-programming-assessment-manual-result',
    template: `
        <jhi-button
            [disabled]="!participationId"
            [btnType]="ButtonType.WARNING"
            [btnSize]="ButtonSize.SMALL"
            [icon]="'asterisk'"
            [title]="'entity.action.newResult'"
            (onClick)="openManualResultDialog($event)"
        ></jhi-button>
    `,
})
export class ProgrammingAssessmentManualResultButtonComponent implements OnChanges {
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;
    @Input() participationId: number;
    @Input() latestResult?: Result | null;

    constructor(private modalService: NgbModal) {}

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.latestResult && this.latestResult && this.latestResult.assessmentType !== AssessmentType.MANUAL) {
            // The assessor can't update the automatic result of the student.
            this.latestResult = null;
        }
    }

    openManualResultDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef = this.modalService.open(ProgrammingAssessmentManualResultDialogComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.participationId = this.participationId;
        modalRef.componentInstance.result = this.latestResult;
    }
}
