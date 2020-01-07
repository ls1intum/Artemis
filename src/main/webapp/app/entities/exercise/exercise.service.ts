import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import * as moment from 'moment';

import { Exercise, ExerciseCategory } from './exercise.model';
import { LtiConfiguration } from '../lti-configuration';
import { ParticipationService } from '../participation/participation.service';
import { map } from 'rxjs/operators';
import { AccountService } from 'app/core/auth/account.service';
import { StatsForDashboard } from 'app/instructor-course-dashboard/stats-for-dashboard.model';

export type EntityResponseType = HttpResponse<Exercise>;
export type EntityArrayResponseType = HttpResponse<Exercise[]>;

@Injectable({ providedIn: 'root' })
export class ExerciseService {
    public resourceUrl = SERVER_API_URL + 'api/exercises';

    constructor(private http: HttpClient, private participationService: ParticipationService, private accountService: AccountService) {}

    create(exercise: Exercise): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(exercise);
        return this.http
            .post<Exercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    update(exercise: Exercise): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(exercise);
        return this.http
            .put<Exercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }
    /**
     * Validates if the date is correct
     */
    validateDate(exercise: Exercise) {
        exercise.dueDateError = exercise.releaseDate && exercise.dueDate ? !exercise.dueDate.isAfter(exercise.releaseDate) : false;

        exercise.assessmentDueDateError =
            exercise.assessmentDueDate && exercise.releaseDate
                ? !exercise.assessmentDueDate.isAfter(exercise.releaseDate)
                : exercise.assessmentDueDate && exercise.dueDate
                ? !exercise.assessmentDueDate.isAfter(exercise.dueDate)
                : false;
    }

    find(exerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<Exercise>(`${this.resourceUrl}/${exerciseId}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res))
            .map((res: EntityResponseType) => this.checkPermission(res));
    }

    cleanup(exerciseId: number, deleteRepositories: boolean): Observable<HttpResponse<void>> {
        const params = new HttpParams().set('deleteRepositories', deleteRepositories.toString());
        return this.http.delete<void>(`${this.resourceUrl}/${exerciseId}/cleanup`, { params, observe: 'response' });
    }

    reset(exerciseId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${exerciseId}/reset`, { observe: 'response' });
    }

    getExerciseDetails(exerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<Exercise>(`${this.resourceUrl}/${exerciseId}/details`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res))
            .map((res: EntityResponseType) => this.checkPermission(res));
    }

    getNextExerciseForDays(exercises: Exercise[], delayInDays = 7): Exercise {
        return exercises.find(exercise => {
            const dueDate = exercise.dueDate!;
            return (
                moment().isBefore(dueDate) &&
                moment()
                    .add(delayInDays, 'day')
                    .isSameOrAfter(dueDate)
            );
        })!;
    }

    getNextExerciseForHours(exercises: Exercise[], delayInHours = 12): Exercise {
        return exercises.find(exercise => {
            const dueDate = exercise.dueDate!;
            return (
                moment().isBefore(dueDate) &&
                moment()
                    .add(delayInHours, 'hours')
                    .isSameOrAfter(dueDate)
            );
        })!;
    }

    convertExerciseDateFromServer(exercise: Exercise): Exercise {
        exercise.releaseDate = exercise.releaseDate != null ? moment(exercise.releaseDate) : null;
        exercise.dueDate = exercise.dueDate != null ? moment(exercise.dueDate) : null;
        exercise.assessmentDueDate = exercise.assessmentDueDate != null ? moment(exercise.assessmentDueDate) : null;
        exercise.studentParticipations = this.participationService.convertParticipationsDateFromServer(exercise.studentParticipations);
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
            releaseDate: exercise.releaseDate != null && moment(exercise.releaseDate).isValid() ? moment(exercise.releaseDate).toJSON() : null,
            dueDate: exercise.dueDate != null && moment(exercise.dueDate).isValid() ? moment(exercise.dueDate).toJSON() : null,
            assessmentDueDate: exercise.assessmentDueDate != null && moment(exercise.assessmentDueDate).isValid() ? moment(exercise.assessmentDueDate).toJSON() : null,
        });
    }

    convertDateFromServer<ERT extends EntityResponseType>(res: ERT): ERT {
        if (res.body) {
            res.body.releaseDate = res.body.releaseDate != null ? moment(res.body.releaseDate) : null;
            res.body.dueDate = res.body.dueDate != null ? moment(res.body.dueDate) : null;
            res.body.assessmentDueDate = res.body.assessmentDueDate != null ? moment(res.body.assessmentDueDate) : null;
            res.body.studentParticipations = this.participationService.convertParticipationsDateFromServer(res.body.studentParticipations);
        }
        return res;
    }

    checkPermission<ERT extends EntityResponseType>(res: ERT): ERT {
        if (res.body && res.body.course) {
            res.body.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(res.body.course);
            res.body.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(res.body.course);
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

    convertExerciseCategoriesFromServer(exercise: Exercise): ExerciseCategory[] {
        if (!exercise || !exercise.categories) {
            return [];
        }
        return exercise.categories.map(el => JSON.parse(el));
    }

    convertExerciseCategoriesAsStringFromServer(categories: string[]): ExerciseCategory[] {
        return categories.map(el => JSON.parse(el));
    }

    convertExerciseForServer<E extends Exercise>(exercise: Exercise): Exercise {
        let copy = Object.assign(exercise, {});
        copy = this.convertDateFromClient(copy);
        if (copy.course) {
            delete copy.course.exercises;
            delete copy.course.lectures;
        }
        delete copy.studentParticipations;
        return copy;
    }

    getForTutors(exerciseId: number): Observable<HttpResponse<Exercise>> {
        return this.http
            .get<Exercise>(`${this.resourceUrl}/${exerciseId}/for-tutor-dashboard`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    getStatsForTutors(exerciseId: number): Observable<HttpResponse<StatsForDashboard>> {
        return this.http.get<StatsForDashboard>(`${this.resourceUrl}/${exerciseId}/stats-for-tutor-dashboard`, { observe: 'response' });
    }

    getStatsForInstructors(exerciseId: number): Observable<HttpResponse<StatsForDashboard>> {
        return this.http.get<StatsForDashboard>(`${this.resourceUrl}/${exerciseId}/stats-for-instructor-dashboard`, { observe: 'response' });
    }
}

@Injectable({ providedIn: 'root' })
export class ExerciseLtiConfigurationService {
    private resourceUrl = SERVER_API_URL + 'api/lti/configuration';

    constructor(private http: HttpClient) {}

    find(exerciseId: number): Observable<HttpResponse<LtiConfiguration>> {
        return this.http.get<LtiConfiguration>(`${this.resourceUrl}/${exerciseId}`, { observe: 'response' });
    }
}
