import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { DATE_TIME_FORMAT } from 'app/shared/constants/input.constants';
import { JhiAlertService } from 'ng-jhipster';

import { ISubmission } from 'app/shared/model/submission.model';
import { SubmissionService } from './submission.service';
import { IExerciseResult } from 'app/shared/model/exercise-result.model';
import { ExerciseResultService } from 'app/entities/exercise-result';
import { IParticipation } from 'app/shared/model/participation.model';
import { ParticipationService } from 'app/entities/participation';

@Component({
    selector: 'jhi-submission-update',
    templateUrl: './submission-update.component.html'
})
export class SubmissionUpdateComponent implements OnInit {
    submission: ISubmission;
    isSaving: boolean;

    results: IExerciseResult[];

    participations: IParticipation[];
    submissionDate: string;

    constructor(
        private jhiAlertService: JhiAlertService,
        private submissionService: SubmissionService,
        private exerciseResultService: ExerciseResultService,
        private participationService: ParticipationService,
        private activatedRoute: ActivatedRoute
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ submission }) => {
            this.submission = submission;
            this.submissionDate = this.submission.submissionDate != null ? this.submission.submissionDate.format(DATE_TIME_FORMAT) : null;
        });
        this.exerciseResultService.query({ filter: 'submission-is-null' }).subscribe(
            (res: HttpResponse<IExerciseResult[]>) => {
                if (!this.submission.result || !this.submission.result.id) {
                    this.results = res.body;
                } else {
                    this.exerciseResultService.find(this.submission.result.id).subscribe(
                        (subRes: HttpResponse<IExerciseResult>) => {
                            this.results = [subRes.body].concat(res.body);
                        },
                        (subRes: HttpErrorResponse) => this.onError(subRes.message)
                    );
                }
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
        this.participationService.query().subscribe(
            (res: HttpResponse<IParticipation[]>) => {
                this.participations = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        this.submission.submissionDate = this.submissionDate != null ? moment(this.submissionDate, DATE_TIME_FORMAT) : null;
        if (this.submission.id !== undefined) {
            this.subscribeToSaveResponse(this.submissionService.update(this.submission));
        } else {
            this.subscribeToSaveResponse(this.submissionService.create(this.submission));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<ISubmission>>) {
        result.subscribe((res: HttpResponse<ISubmission>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }

    trackExerciseResultById(index: number, item: IExerciseResult) {
        return item.id;
    }

    trackParticipationById(index: number, item: IParticipation) {
        return item.id;
    }
}
