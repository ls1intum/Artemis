import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { DATE_TIME_FORMAT } from 'app/shared/constants/input.constants';
import { JhiAlertService } from 'ng-jhipster';

import { IResult } from 'app/shared/model/result.model';
import { ResultService } from './result.service';
import { ISubmission } from 'app/shared/model/submission.model';
import { SubmissionService } from 'app/entities/submission';
import { IParticipation } from 'app/shared/model/participation.model';
import { ParticipationService } from 'app/entities/participation';

@Component({
    selector: 'jhi-result-update',
    templateUrl: './result-update.component.html'
})
export class ResultUpdateComponent implements OnInit {
    private _result: IResult;
    isSaving: boolean;

    submissions: ISubmission[];

    participations: IParticipation[];
    completionDate: string;

    constructor(
        private jhiAlertService: JhiAlertService,
        private resultService: ResultService,
        private submissionService: SubmissionService,
        private participationService: ParticipationService,
        private activatedRoute: ActivatedRoute
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ result }) => {
            this.result = result;
        });
        this.submissionService.query({ filter: 'result-is-null' }).subscribe(
            (res: HttpResponse<ISubmission[]>) => {
                if (!this.result.submission || !this.result.submission.id) {
                    this.submissions = res.body;
                } else {
                    this.submissionService.find(this.result.submission.id).subscribe(
                        (subRes: HttpResponse<ISubmission>) => {
                            this.submissions = [subRes.body].concat(res.body);
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
        this.result.completionDate = moment(this.completionDate, DATE_TIME_FORMAT);
        if (this.result.id !== undefined) {
            this.subscribeToSaveResponse(this.resultService.update(this.result));
        } else {
            this.subscribeToSaveResponse(this.resultService.create(this.result));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IResult>>) {
        result.subscribe((res: HttpResponse<IResult>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
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

    trackSubmissionById(index: number, item: ISubmission) {
        return item.id;
    }

    trackParticipationById(index: number, item: IParticipation) {
        return item.id;
    }
    get result() {
        return this._result;
    }

    set result(result: IResult) {
        this._result = result;
        this.completionDate = moment(result.completionDate).format(DATE_TIME_FORMAT);
    }
}
