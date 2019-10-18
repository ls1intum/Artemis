import { Component, Input } from '@angular/core';
import { ButtonType } from 'app/shared/components';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingAssessmentManualResultDialogComponent } from 'app/programming-assessment/manual-result/programming-assessment-manual-result-dialog.component';

@Component({
    selector: 'jhi-programming-assessment-manual-result',
    template: `
        <jhi-button
            [disabled]="!participationId"
            [btnType]="ButtonType.WARNING"
            [icon]="'asterisk'"
            [title]="'entity.action.newResult'"
            (onClick)="openManualResultDialog($event)"
        ></jhi-button>
    `,
})
export class ProgrammingAssessmentManualResultButtonComponent {
    ButtonType = ButtonType;
    @Input() participationId: number;

    constructor(private modalService: NgbModal) {}

    openManualResultDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef = this.modalService.open(ProgrammingAssessmentManualResultDialogComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.participationId = this.participationId;
    }
}
