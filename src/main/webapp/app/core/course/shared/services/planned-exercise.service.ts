import { Injectable, signal } from '@angular/core';
//import { AlertService } from 'app/shared/service/alert.service';
//import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import dayjs, { Dayjs } from 'dayjs/esm';

export interface PlannedExercise {
    id: number;
    title: string;
    releaseDate?: Dayjs;
    startDate?: Dayjs;
    dueDate?: Dayjs;
    assessmentDueDate?: Dayjs;
}

export class PlannedExerciseCreateDTO {
    constructor(
        public title: string,
        public releaseDate?: Dayjs,
        public startDate?: Dayjs,
        public dueDate?: Dayjs,
        public assessmentDueDate?: Dayjs,
    ) {}
}

const plannedExercises: PlannedExercise[] = [
    {
        id: 1,
        title: 'Exercise 1',
        releaseDate: dayjs('2025-10-01T09:00:00'),
        startDate: dayjs('2025-10-01T10:00:00'),
        dueDate: dayjs('2025-10-08T23:59:00'),
        assessmentDueDate: dayjs('2025-10-10T12:00:00'),
    },
    {
        id: 2,
        title: 'Exercise 2',
        releaseDate: dayjs('2025-10-02T09:00:00'),
        startDate: dayjs('2025-10-02T10:00:00'),
        dueDate: dayjs('2025-10-09T23:59:00'),
        assessmentDueDate: dayjs('2025-10-11T12:00:00'),
    },
    {
        id: 3,
        title: 'Exercise 3',
        releaseDate: dayjs('2025-10-03T09:00:00'),
        startDate: dayjs('2025-10-03T10:00:00'),
        dueDate: dayjs('2025-10-10T23:59:00'),
        assessmentDueDate: dayjs('2025-10-12T12:00:00'),
    },
    {
        id: 4,
        title: 'Exercise 4',
        releaseDate: dayjs('2025-10-04T09:00:00'),
        startDate: dayjs('2025-10-04T10:00:00'),
        dueDate: dayjs('2025-10-11T23:59:00'),
        assessmentDueDate: dayjs('2025-10-13T12:00:00'),
    },
    {
        id: 5,
        title: 'Exercise 5',
        releaseDate: dayjs('2025-10-05T09:00:00'),
        startDate: dayjs('2025-10-05T10:00:00'),
        dueDate: dayjs('2025-10-12T23:59:00'),
        assessmentDueDate: dayjs('2025-10-14T12:00:00'),
    },
];

@Injectable({
    providedIn: 'root',
})
export class PlannedExerciseService {
    //private readonly apiUrl = 'api/planned-exercises';

    //private httpClient = inject(HttpClient);
    //private alertService = inject(AlertService);
    private _plannedExercises = signal<PlannedExercise[]>([]);
    private _loading = signal(false);
    private idCount = 0;

    plannedExercises = this._plannedExercises.asReadonly();
    loading = this._loading.asReadonly();

    load() {
        this._loading.set(true);
        setTimeout(() => {
            this._plannedExercises.set(plannedExercises);
            this._loading.set(false);
        }, 1000);
    }

    create(plannedExercise: PlannedExerciseCreateDTO | PlannedExerciseCreateDTO[]): Observable<void> {
        this._loading.set(true);
        return new Observable<void>((subscriber) => {
            setTimeout(() => {
                const dtos = Array.isArray(plannedExercise) ? plannedExercise : [plannedExercise];
                const newExercises: PlannedExercise[] = dtos.map((dto, index) => ({
                    id: this.idCount,
                    title: dto.title,
                    releaseDate: dto.releaseDate,
                    startDate: dto.startDate,
                    dueDate: dto.dueDate,
                    assessmentDueDate: dto.assessmentDueDate,
                }));
                this.idCount++;
                this._plannedExercises.update((exercises) => [...exercises, ...newExercises]);
                this._loading.set(false);
                subscriber.next();
                subscriber.complete();
            }, 1000);
        });
    }

    update(plannedExercise: PlannedExercise): Observable<void> {
        this._loading.set(true);
        return new Observable<void>((subscriber) => {
            setTimeout(() => {
                this._loading.set(false);
                subscriber.next();
                subscriber.complete();
            }, 1000);
        });
    }

    delete(plannedExerciseId: number) {
        this._loading.set(true);
        setTimeout(() => {
            this._plannedExercises.update((exercises) => exercises.filter((exercise) => exercise.id !== plannedExerciseId));
            this._loading.set(false);
        }, 1000);
    }
}
