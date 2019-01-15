import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import * as moment from 'moment';

import { Exercise } from './exercise.model';
import { createRequestOption } from '../../shared';
import { LtiConfiguration } from '../lti-configuration';
import { ParticipationService } from '../participation/participation.service';

export type EntityResponseType = HttpResponse<Exercise>;
export type EntityArrayResponseType = HttpResponse<Exercise[]>;

@Injectable({providedIn: 'root'})
export class ExerciseService {
    public resourceUrl = SERVER_API_URL + 'api/exercises';

    constructor(private http: HttpClient, private participationService: ParticipationService) {
    }

    create(exercise: Exercise): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(exercise);
        return this.http
            .post<Exercise>(this.resourceUrl, copy, {observe: 'response'})
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    update(exercise: Exercise): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(exercise);
        return this.http
            .put<Exercise>(this.resourceUrl, copy, {observe: 'response'})
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<Exercise>(`${this.resourceUrl}/${id}`, {observe: 'response'})
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    delete(id: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${id}`, {observe: 'response'});
    }

    archive(id: number): Observable<HttpResponse<Blob>> {
        return this.http.get(`${this.resourceUrl}/${id}/archive`, {observe: 'response', responseType: 'blob'});
    }

    cleanup(id: number, deleteRepositories: boolean): Observable<HttpResponse<void>> {
        const params = new HttpParams().set('deleteRepositories', deleteRepositories.toString());
        return this.http.delete<void>(`${this.resourceUrl}/${id}/cleanup`, {params, observe: 'response'});
    }

    reset(id: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${id}/participations`, {observe: 'response'});
    }

    exportRepos(id: number, students: string[]): Observable<HttpResponse<Blob>> {
        return this.http.get(`${this.resourceUrl}/${id}/participations/${students}`, {
            observe: 'response',
            responseType: 'blob'
        });
    }

    getNextExercisesForDays(exercises: Exercise[], delay: number = 7): Exercise[] {
        return exercises.filter(exercise => {
            return moment().isBefore(exercise.dueDate) && moment().add(delay, 'day').isSameOrAfter(exercise.dueDate);
        });
    }

    convertExerciseDateFromServer(exercise: Exercise): Exercise {
        exercise.releaseDate = exercise.releaseDate != null ? moment(exercise.releaseDate) : null;
        exercise.dueDate = exercise.dueDate != null ? moment(exercise.dueDate) : null;
        exercise.participations = this.participationService.convertParticipationsDateFromServer(exercise.participations);
        return exercise;
    }

    convertExercisesDateFromServer(exercises: Exercise[]): Exercise[] {
        const convertedExercises: Exercise[] = [];
        if (exercises != null && exercises.length > 0) {
            exercises.forEach((exercise: Exercise) => {
                convertedExercises.push(this.convertExerciseDateFromServer(exercise));
            });
        }
        return convertedExercises;
    }

    convertDateFromClient<E extends Exercise>(exercise: E): E {
        return Object.assign({}, exercise, {
            releaseDate: exercise.releaseDate != null && moment(exercise.releaseDate).isValid() ? exercise.releaseDate.toJSON() : null,
            dueDate: exercise.dueDate != null && moment(exercise.dueDate).isValid() ? exercise.dueDate.toJSON() : null
        });
    }

    convertDateFromServer<ERT extends EntityResponseType>(res: ERT): ERT {
        if (res.body) {
            res.body.releaseDate = res.body.releaseDate != null ? moment(res.body.releaseDate) : null;
            res.body.dueDate = res.body.dueDate != null ? moment(res.body.dueDate) : null;
            res.body.participations = this.participationService.convertParticipationsDateFromServer(res.body.participations);
        }
        return res;
    }

    convertDateArrayFromServer<E extends Exercise, EART extends EntityArrayResponseType>(res: EART): EART {
        if (res.body) {
            res.body.forEach((exercise: Exercise) => {
                this.convertExerciseDateFromServer(exercise);
            });
        }
        return res;
    }
}

@Injectable({providedIn: 'root'})
export class ExerciseLtiConfigurationService {
    private resourceUrl = SERVER_API_URL + 'api/lti/configuration';

    constructor(private http: HttpClient) {
    }

    find(id: number): Observable<HttpResponse<LtiConfiguration>> {
        return this.http.get<LtiConfiguration>(`${this.resourceUrl}/${id}`, {observe: 'response'});
    }
}
