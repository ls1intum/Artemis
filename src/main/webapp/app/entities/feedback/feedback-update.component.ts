import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { JhiAlertService } from 'ng-jhipster';

import { IFeedback } from 'app/shared/model/feedback.model';
import { FeedbackService } from './feedback.service';
import { IResult } from 'app/shared/model/result.model';
import { ResultService } from 'app/entities/result';

@Component({
    selector: 'jhi-feedback-update',
    templateUrl: './feedback-update.component.html'
})
export class FeedbackUpdateComponent implements OnInit {
    private _feedback: IFeedback;
    isSaving: boolean;

    results: IResult[];

    constructor(
        private jhiAlertService: JhiAlertService,
        private feedbackService: FeedbackService,
        private resultService: ResultService,
        private activatedRoute: ActivatedRoute
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ feedback }) => {
            this.feedback = feedback;
        });
        this.resultService.query().subscribe(
            (res: HttpResponse<IResult[]>) => {
                this.results = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.feedback.id !== undefined) {
            this.subscribeToSaveResponse(this.feedbackService.update(this.feedback));
        } else {
            this.subscribeToSaveResponse(this.feedbackService.create(this.feedback));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IFeedback>>) {
        result.subscribe((res: HttpResponse<IFeedback>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
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

    trackResultById(index: number, item: IResult) {
        return item.id;
    }
    get feedback() {
        return this._feedback;
    }

    set feedback(feedback: IFeedback) {
        this._feedback = feedback;
    }
}
