import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ExerciseUnit } from 'app/lecture/shared/entities/lecture-unit/exerciseUnit.model';
import { map } from 'rxjs/operators';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/services/lectureUnit.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';

type EntityResponseType = HttpResponse<ExerciseUnit>;
type EntityArrayResponseType = HttpResponse<ExerciseUnit[]>;

@Injectable({
    providedIn: 'root',
})
export class ExerciseUnitService {
    private httpClient = inject(HttpClient);
    private lectureUnitService = inject(LectureUnitService);

    private resourceURL = 'api/lecture';

    create(exerciseUnit: ExerciseUnit, lectureId: number): Observable<EntityResponseType> {
        if (exerciseUnit.exercise) {
            exerciseUnit.exercise.categories = ExerciseService.stringifyExerciseCategories(exerciseUnit.exercise);
        }

        return this.httpClient
            .post<ExerciseUnit>(`${this.resourceURL}/lectures/${lectureId}/exercise-units`, exerciseUnit, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.lectureUnitService.convertLectureUnitResponseDatesFromServer(res)));
    }

    findAllByLectureId(lectureId: number): Observable<EntityArrayResponseType> {
        return this.httpClient
            .get<ExerciseUnit[]>(`${this.resourceURL}/lectures/${lectureId}/exercise-units`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.lectureUnitService.convertLectureUnitResponseArrayDatesFromServer(res)));
    }
}
