import { Injectable, inject, signal } from '@angular/core';
import { AlertService } from 'app/shared/service/alert.service';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, finalize, map, of, tap } from 'rxjs';
import { PlannedExercise, PlannedExerciseCreateDTO, RawPlannedExercise } from 'app/core/course/shared/entities/planned-exercise.model';
import { getFirstAvailableDatePropertyOf } from 'app/core/course/manage/exercise-planning/planned-exercise.util';

@Injectable({
    providedIn: 'root',
})
export class PlannedExerciseService {
    private readonly urlBase = 'api/planned-exercise';

    private httpClient = inject(HttpClient);
    private alertService = inject(AlertService);
    private _plannedExercises = signal<PlannedExercise[]>([]);
    private _loading = signal(false);

    plannedExercises = this._plannedExercises.asReadonly();
    loading = this._loading.asReadonly();

    getAll(courseId: number) {
        this._loading.set(true);
        const url = `${this.urlBase}/courses/${courseId}/planned-exercises`;
        this.httpClient
            .get<RawPlannedExercise[]>(url)
            .pipe(
                map((rawExercises) => rawExercises.map((raw) => new PlannedExercise(raw))),
                catchError(() => {
                    this.alertService.addErrorAlert('Something went wrong while loading the planned exercises. Please try again.');
                    return of([] as PlannedExercise[]);
                }),
                finalize(() => this._loading.set(false)),
            )
            .subscribe((plannedExercises) => {
                this._plannedExercises.set(plannedExercises);
            });
    }

    create(plannedExercise: PlannedExerciseCreateDTO, courseId: number): Observable<void> {
        this._loading.set(true);
        const url = this.urlBase + `/courses/${courseId}/planned-exercises`;
        return this.httpClient.post<RawPlannedExercise>(url, plannedExercise).pipe(
            map((rawExercise) => {
                const newExercises = [new PlannedExercise(rawExercise)];
                this._plannedExercises.update((oldExercises) => this.mergeOldWithNewExercises(oldExercises, newExercises));
            }),
            map(() => undefined),
            catchError(() => {
                this.alertService.addErrorAlert('Something went wrong while creating the planned exercise. Please try again.');
                return of(undefined);
            }),
            finalize(() => this._loading.set(false)),
        );
    }

    createAll(plannedExercises: PlannedExerciseCreateDTO[], courseId: number): Observable<void> {
        this._loading.set(true);
        const url = this.urlBase + `/courses/${courseId}/planned-exercises/batch`;
        return this.httpClient.post<RawPlannedExercise[]>(url, plannedExercises).pipe(
            map((rawExercises) => {
                const newExercises = rawExercises.map((raw) => new PlannedExercise(raw));
                this._plannedExercises.update((oldExercises) => this.mergeOldWithNewExercises(oldExercises, newExercises));
            }),
            map(() => undefined),
            catchError(() => {
                this.alertService.addErrorAlert('Something went wrong while creating the planned exercise. Please try again.');
                return of(undefined);
            }),
            finalize(() => this._loading.set(false)),
        );
    }

    private mergeOldWithNewExercises(oldExercises: PlannedExercise[], newExercises: PlannedExercise[]): PlannedExercise[] {
        const result: PlannedExercise[] = [];
        let oldIndex = 0;
        let newIndex = 0;
        while (oldIndex < oldExercises.length && newIndex < newExercises.length) {
            const nextNewExercise = newExercises[newIndex];
            const nextOldExercise = oldExercises[oldIndex];
            if (getFirstAvailableDatePropertyOf(nextNewExercise).isBefore(getFirstAvailableDatePropertyOf(nextOldExercise))) {
                result.push(nextNewExercise);
                newIndex++;
            } else {
                result.push(nextOldExercise);
                oldIndex++;
            }
        }
        if (oldIndex < oldExercises.length) result.push(...oldExercises.slice(oldIndex));
        if (newIndex < newExercises.length) result.push(...newExercises.slice(newIndex));
        return result;
    }

    update(plannedExercise: PlannedExercise, courseId: number): Observable<void> {
        this._loading.set(true);
        const url = this.urlBase + `/courses/${courseId}/planned-exercises`;
        return this.httpClient.put<RawPlannedExercise>(url, plannedExercise).pipe(
            map(() => undefined),
            catchError(() => {
                this.alertService.addErrorAlert('Something went wrong while updating the planned exercise. Please try again.');
                return of(undefined);
            }),
            finalize(() => this._loading.set(false)),
        );
    }

    delete(plannedExerciseId: number, courseId: number) {
        this._loading.set(true);
        const url = `${this.urlBase}/courses/${courseId}/planned-exercises/${plannedExerciseId}`;
        this.httpClient
            .delete<void>(url)
            .pipe(
                tap(() => {
                    this._plannedExercises.update((exercises) => exercises.filter((exercise) => exercise.id !== plannedExerciseId));
                }),
                catchError(() => {
                    this.alertService.addErrorAlert('Something went wrong while deleting the planned exercise. Please try again.');
                    return of(undefined);
                }),
                finalize(() => this._loading.set(false)),
            )
            .subscribe();
    }
}
