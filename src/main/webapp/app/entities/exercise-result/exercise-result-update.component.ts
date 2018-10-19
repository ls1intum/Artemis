import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { DATE_TIME_FORMAT } from 'app/shared/constants/input.constants';
import { JhiAlertService } from 'ng-jhipster';

import { IExerciseResult } from 'app/shared/model/exercise-result.model';
import { ExerciseResultService } from './exercise-result.service';
import { IUser, UserService } from 'app/core';
import { ISubmission } from 'app/shared/model/submission.model';
import { SubmissionService } from 'app/entities/submission';
import { IParticipation } from 'app/shared/model/participation.model';
import { ParticipationService } from 'app/entities/participation';

@Component({
    selector: 'jhi-exercise-result-update',
    templateUrl: './exercise-result-update.component.html'
})
export class ExerciseResultUpdateComponent implements OnInit {
    exerciseResult: IExerciseResult;
    isSaving: boolean;

    users: IUser[];

    submissions: ISubmission[];

    participations: IParticipation[];
    completionDate: string;

    constructor(
        private jhiAlertService: JhiAlertService,
        private exerciseResultService: ExerciseResultService,
        private userService: UserService,
        private submissionService: SubmissionService,
        private participationService: ParticipationService,
        private activatedRoute: ActivatedRoute
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ exerciseResult }) => {
            this.exerciseResult = exerciseResult;
            this.completionDate =
                this.exerciseResult.completionDate != null ? this.exerciseResult.completionDate.format(DATE_TIME_FORMAT) : null;
        });
        this.userService.query().subscribe(
            (res: HttpResponse<IUser[]>) => {
                this.users = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
        this.submissionService.query().subscribe(
            (res: HttpResponse<ISubmission[]>) => {
                this.submissions = res.body;
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
        this.exerciseResult.completionDate = this.completionDate != null ? moment(this.completionDate, DATE_TIME_FORMAT) : null;
        if (this.exerciseResult.id !== undefined) {
            this.subscribeToSaveResponse(this.exerciseResultService.update(this.exerciseResult));
        } else {
            this.subscribeToSaveResponse(this.exerciseResultService.create(this.exerciseResult));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IExerciseResult>>) {
        result.subscribe((res: HttpResponse<IExerciseResult>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
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

    trackSubmissionById(index: number, item: ISubmission) {
        return item.id;
    }

    trackParticipationById(index: number, item: IParticipation) {
        return item.id;
    }
}
