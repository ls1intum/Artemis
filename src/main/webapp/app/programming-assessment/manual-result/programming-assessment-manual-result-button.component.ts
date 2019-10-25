import { Component, Input, EventEmitter, Output } from '@angular/core';
import { ButtonSize, ButtonType } from 'app/shared/components';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingAssessmentManualResultDialogComponent } from 'app/programming-assessment/manual-result/programming-assessment-manual-result-dialog.component';
import { Result } from 'app/entities/result';

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
export class ProgrammingAssessmentManualResultButtonComponent {
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;
    @Input() participationId: number;
    @Output() onResultCreated = new EventEmitter<Result>();

    constructor(private modalService: NgbModal) {}

    openManualResultDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef = this.modalService.open(ProgrammingAssessmentManualResultDialogComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.participationId = this.participationId;
        modalRef.result.then(result => this.onResultCreated.emit(result));
    }
}
