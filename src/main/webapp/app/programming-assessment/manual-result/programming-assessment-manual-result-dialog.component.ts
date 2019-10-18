import { Component, OnDestroy, OnInit, Input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseScoresPopupService } from '../../scores/exercise-scores-popup.service';
import { Result } from '../../entities/result/result.model';
import { ResultService } from 'app/entities/result/result.service';
import { Feedback, FeedbackType } from '../../entities/feedback';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';
import { HttpResponse } from '@angular/common/http';
import * as moment from 'moment';
import { Observable, of } from 'rxjs';

import { Subscription } from 'rxjs/Subscription';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationService } from 'app/entities/participation';
import { catchError, tap } from 'rxjs/operators';
import { ProgrammingAssessmentManualResultService } from 'app/programming-assessment/manual-result/programming-assessment-manual-result.service';

@Component({
    selector: 'jhi-exercise-scores-result-dialog',
    templateUrl: './programming-assessment-manual-result-dialog.component.html',
})
export class ProgrammingAssessmentManualResultDialogComponent implements OnInit {
    @Input() participationId: number;
    participation: StudentParticipation;
    result: Result;
    feedbacks: Feedback[] = [];
    isLoading = false;
    isSaving = false;
    isOpenForSubmission = false;

    constructor(
        private participationService: ParticipationService,
        private manualResultService: ProgrammingAssessmentManualResultService,
        public activeModal: NgbActiveModal,
        private datePipe: DatePipe,
        private eventManager: JhiEventManager,
        private alertService: JhiAlertService,
    ) {}

    ngOnInit() {
        this.isLoading = true;
        // TODO: Implement result update.
        this.result = this.manualResultService.generateInitialManualResult();
        this.participationService
            .find(this.participationId)
            .pipe(
                tap(({ body: participation }) => {
                    this.participation = participation!;
                    this.result.participation = this.participation;
                    this.isOpenForSubmission = this.participation.exercise.dueDate === null || this.participation.exercise.dueDate.isAfter(moment());
                }),
                catchError((err: any) => {
                    this.alertService.error(err);
                    return of(null);
                }),
            )
            .subscribe(() => {
                this.isLoading = false;
            });
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    save() {
        this.result.feedbacks = this.feedbacks;
        this.isSaving = true;
        for (let i = 0; i < this.result.feedbacks.length; i++) {
            this.result.feedbacks[i].type = FeedbackType.MANUAL;
        }
        if (this.result.id != null) {
            this.subscribeToSaveResponse(this.manualResultService.update(this.result));
        } else {
            // in case id is null or undefined
            this.subscribeToSaveResponse(this.manualResultService.create(this.result));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<Result>>) {
        result.subscribe(res => this.onSaveSuccess(res), err => this.onSaveError());
    }

    onSaveSuccess(result: HttpResponse<Result>) {
        this.activeModal.close(result);
        this.isSaving = false;
        this.eventManager.broadcast({ name: 'resultListModification', content: 'Added a manual result' });
    }

    onSaveError() {
        this.isSaving = false;
    }

    pushFeedback() {
        this.feedbacks.push(new Feedback());
    }

    popFeedback() {
        if (this.feedbacks.length > 0) {
            this.feedbacks.pop();
        }
    }
}
