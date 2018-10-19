import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { JhiAlertService } from 'ng-jhipster';

import { IFeedback } from 'app/shared/model/feedback.model';
import { FeedbackService } from './feedback.service';
import { IExerciseResult } from 'app/shared/model/exercise-result.model';
import { ExerciseResultService } from 'app/entities/exercise-result';

@Component({
    selector: 'jhi-feedback-update',
    templateUrl: './feedback-update.component.html'
})
export class FeedbackUpdateComponent implements OnInit {
    feedback: IFeedback;
    isSaving: boolean;

    exerciseresults: IExerciseResult[];

    constructor(
        private jhiAlertService: JhiAlertService,
        private feedbackService: FeedbackService,
        private exerciseResultService: ExerciseResultService,
        private activatedRoute: ActivatedRoute
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ feedback }) => {
            this.feedback = feedback;
        });
        this.exerciseResultService.query().subscribe(
            (res: HttpResponse<IExerciseResult[]>) => {
                this.exerciseresults = res.body;
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

    trackExerciseResultById(index: number, item: IExerciseResult) {
        return item.id;
    }
}
