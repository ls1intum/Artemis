import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { JhiAlertService } from 'ng-jhipster';

import { ILtiOutcomeUrl } from 'app/shared/model/lti-outcome-url.model';
import { LtiOutcomeUrlService } from './lti-outcome-url.service';
import { IUser, UserService } from 'app/core';
import { IExercise } from 'app/shared/model/exercise.model';
import { ExerciseService } from 'app/entities/exercise';

@Component({
    selector: 'jhi-lti-outcome-url-update',
    templateUrl: './lti-outcome-url-update.component.html'
})
export class LtiOutcomeUrlUpdateComponent implements OnInit {
    private _ltiOutcomeUrl: ILtiOutcomeUrl;
    isSaving: boolean;

    users: IUser[];

    exercises: IExercise[];

    constructor(
        private jhiAlertService: JhiAlertService,
        private ltiOutcomeUrlService: LtiOutcomeUrlService,
        private userService: UserService,
        private exerciseService: ExerciseService,
        private activatedRoute: ActivatedRoute
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ ltiOutcomeUrl }) => {
            this.ltiOutcomeUrl = ltiOutcomeUrl;
        });
        this.userService.query().subscribe(
            (res: HttpResponse<IUser[]>) => {
                this.users = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
        this.exerciseService.query().subscribe(
            (res: HttpResponse<IExercise[]>) => {
                this.exercises = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.ltiOutcomeUrl.id !== undefined) {
            this.subscribeToSaveResponse(this.ltiOutcomeUrlService.update(this.ltiOutcomeUrl));
        } else {
            this.subscribeToSaveResponse(this.ltiOutcomeUrlService.create(this.ltiOutcomeUrl));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<ILtiOutcomeUrl>>) {
        result.subscribe((res: HttpResponse<ILtiOutcomeUrl>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
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

    trackUserById(index: number, item: IUser) {
        return item.id;
    }

    trackExerciseById(index: number, item: IExercise) {
        return item.id;
    }
    get ltiOutcomeUrl() {
        return this._ltiOutcomeUrl;
    }

    set ltiOutcomeUrl(ltiOutcomeUrl: ILtiOutcomeUrl) {
        this._ltiOutcomeUrl = ltiOutcomeUrl;
    }
}
