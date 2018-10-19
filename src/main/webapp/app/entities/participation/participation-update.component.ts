import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { DATE_TIME_FORMAT } from 'app/shared/constants/input.constants';
import { JhiAlertService } from 'ng-jhipster';

import { IParticipation } from 'app/shared/model/participation.model';
import { ParticipationService } from './participation.service';
import { IUser, UserService } from 'app/core';
import { IExercise } from 'app/shared/model/exercise.model';
import { ExerciseService } from 'app/entities/exercise';

@Component({
    selector: 'jhi-participation-update',
    templateUrl: './participation-update.component.html'
})
export class ParticipationUpdateComponent implements OnInit {
    participation: IParticipation;
    isSaving: boolean;

    users: IUser[];

    exercises: IExercise[];
    initializationDate: string;

    constructor(
        private jhiAlertService: JhiAlertService,
        private participationService: ParticipationService,
        private userService: UserService,
        private exerciseService: ExerciseService,
        private activatedRoute: ActivatedRoute
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ participation }) => {
            this.participation = participation;
            this.initializationDate =
                this.participation.initializationDate != null ? this.participation.initializationDate.format(DATE_TIME_FORMAT) : null;
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
        this.participation.initializationDate = this.initializationDate != null ? moment(this.initializationDate, DATE_TIME_FORMAT) : null;
        if (this.participation.id !== undefined) {
            this.subscribeToSaveResponse(this.participationService.update(this.participation));
        } else {
            this.subscribeToSaveResponse(this.participationService.create(this.participation));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IParticipation>>) {
        result.subscribe((res: HttpResponse<IParticipation>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
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
}
