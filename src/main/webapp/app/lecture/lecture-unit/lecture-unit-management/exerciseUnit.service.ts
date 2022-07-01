import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';
import { map } from 'rxjs/operators';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

type EntityResponseType = HttpResponse<ExerciseUnit>;
type EntityArrayResponseType = HttpResponse<ExerciseUnit[]>;

@Injectable({
    providedIn: 'root',
})
export class ExerciseUnitService {
    private resourceURL = SERVER_API_URL + 'api';

    constructor(private httpClient: HttpClient, private lectureUnitService: LectureUnitService) {}

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
