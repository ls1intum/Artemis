import { Component, Input, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Result } from '../../entities/result/result.model';
import { ResultService } from 'app/entities/result/result.service';
import { Feedback, FeedbackType } from '../../entities/feedback';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { HttpResponse } from '@angular/common/http';
import * as moment from 'moment';
import { Observable, of } from 'rxjs';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationService } from 'app/entities/participation';
import { catchError, tap } from 'rxjs/operators';
import { ProgrammingAssessmentManualResultService } from 'app/programming-assessment/manual-result/programming-assessment-manual-result.service';
import { SCORE_PATTERN } from 'app/app.constants';

@Component({
    selector: 'jhi-exercise-scores-result-dialog',
    templateUrl: './programming-assessment-manual-result-dialog.component.html',
})
export class ProgrammingAssessmentManualResultDialogComponent implements OnInit {
    SCORE_PATTERN = SCORE_PATTERN;
    @Input() participationId: number;
    @Input() result: Result;
    participation: StudentParticipation;
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
        private resultService: ResultService,
    ) {}

    ngOnInit() {
        // If there already is a manual result, update it instead of creating a new one.
        if (this.result) {
            this.initializeForResultUpdate();
            return;
        }
        this.initializeForResultCreation();
    }

    initializeForResultUpdate() {
        if (this.result.feedbacks) {
            this.feedbacks = this.result.feedbacks;
        } else {
            this.isLoading = true;
            this.resultService
                .getFeedbackDetailsForResult(this.result.id)
                .pipe(
                    tap(({ body: feedbacks }) => {
                        this.feedbacks = feedbacks!;
                    }),
                )
                .subscribe(() => (this.isLoading = false));
        }
        this.participation = this.result.participation! as StudentParticipation;
    }

    initializeForResultCreation() {
        this.isLoading = true;
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
                    this.clear();
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
            this.subscribeToSaveResponse(this.manualResultService.update(this.participation.id, this.result));
        } else {
            // in case id is null or undefined
            this.subscribeToSaveResponse(this.manualResultService.create(this.participation.id, this.result));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<Result>>) {
        result.subscribe(res => this.onSaveSuccess(res), err => this.onSaveError());
    }

    onSaveSuccess(result: HttpResponse<Result>) {
        this.activeModal.close(result.body);
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
