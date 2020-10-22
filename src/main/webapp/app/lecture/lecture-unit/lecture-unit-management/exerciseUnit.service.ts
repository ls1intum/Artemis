import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { Observable } from 'rxjs';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';
import * as moment from 'moment';
import { map } from 'rxjs/operators';

type EntityResponseType = HttpResponse<ExerciseUnit>;
type EntityArrayResponseType = HttpResponse<ExerciseUnit[]>;

@Injectable({
    providedIn: 'root',
})
export class ExerciseUnitService {
    private resourceURL = SERVER_API_URL + 'api';

    constructor(private httpClient: HttpClient) {}

    /**
     * Creates a new ExerciseUnit on the server using a POST request.
     * @param exerciseUnit - the ExerciseUnit to create
     * @param lectureId - the id of the lecture to connect the ExerciseUnit to
     */
    create(exerciseUnit: ExerciseUnit, lectureId: number): Observable<EntityResponseType> {
        return this.httpClient
            .post<ExerciseUnit>(`${this.resourceURL}/lectures/${lectureId}/exercise-units`, exerciseUnit, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    private convertDateFromClient(exerciseUnit: ExerciseUnit): ExerciseUnit {
        return Object.assign({}, exerciseUnit, {
            releaseDate: exerciseUnit.releaseDate && moment(exerciseUnit.releaseDate).isValid() ? exerciseUnit.releaseDate.toJSON() : undefined,
        });
    }

    private convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.releaseDate = res.body.releaseDate ? moment(res.body.releaseDate) : undefined;
        }
        return res;
    }

    private convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((exerciseUnit: ExerciseUnit) => {
                exerciseUnit.releaseDate = exerciseUnit.releaseDate ? moment(exerciseUnit.releaseDate) : undefined;
            });
        }
        return res;
    }
}
