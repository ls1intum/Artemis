import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { ButtonSize, ButtonType } from 'app/shared/components';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingAssessmentManualResultDialogComponent } from 'app/programming-assessment/manual-result/programming-assessment-manual-result-dialog.component';
import { Result } from 'app/entities/result';
import { AssessmentType } from 'app/entities/assessment-type';
import { Subscription } from 'rxjs';
import { ParticipationWebsocketService } from 'app/entities/participation';
import { filter } from 'rxjs/operators';
import { cloneDeep } from 'lodash';
import { ProgrammingExercise } from 'app/entities/programming-exercise';

@Component({
    selector: 'jhi-programming-assessment-manual-result',
    template: `
        <jhi-button
            [disabled]="!participationId"
            [btnType]="ButtonType.WARNING"
            [btnSize]="ButtonSize.SMALL"
            [icon]="'asterisk'"
            [title]="latestResult ? (latestResult.hasComplaint ? 'entity.action.viewResult' : 'entity.action.updateResult') : 'entity.action.newResult'"
            (onClick)="openManualResultDialog($event)"
        ></jhi-button>
    `,
})
export class ProgrammingAssessmentManualResultButtonComponent implements OnChanges, OnDestroy {
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;
    @Input() participationId: number;
    @Output() onResultModified = new EventEmitter<Result>();
    @Input() latestResult?: Result | null;
    @Input() exercise: ProgrammingExercise;

    latestResultSubscription: Subscription;

    constructor(private modalService: NgbModal, private participationWebsocketService: ParticipationWebsocketService) {}

    /**
     * - Check that the inserted result is of type MANUAL, otherwise set it to null
     * - If the participationId changes, subscribe to the latest result from the websocket
     *
     * @param changes
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (changes.latestResult && this.latestResult && this.latestResult.assessmentType !== AssessmentType.MANUAL) {
            // The assessor can't update the automatic result of the student.
            this.latestResult = null;
        }
        if (changes.participationId && this.participationId) {
            if (this.latestResultSubscription) {
                this.latestResultSubscription.unsubscribe();
            }
            this.latestResultSubscription = this.participationWebsocketService
                .subscribeForLatestResultOfParticipation(this.participationId)
                .pipe(filter((result: Result) => result && result.assessmentType === AssessmentType.MANUAL))
                .subscribe(manualResult => (this.latestResult = manualResult));
        }
    }

    ngOnDestroy(): void {
        if (this.latestResultSubscription) {
            this.latestResultSubscription.unsubscribe();
        }
    }

    openManualResultDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(ProgrammingAssessmentManualResultDialogComponent, { keyboard: true, size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.participationId = this.participationId;
        modalRef.componentInstance.result = cloneDeep(this.latestResult);
        modalRef.componentInstance.exercise = this.exercise;
        modalRef.componentInstance.onResultModified.subscribe(($event: Result) => this.onResultModified.emit($event));
        modalRef.result.then(
            result => this.onResultModified.emit(result),
            () => {},
        );
    }
}
