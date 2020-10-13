import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import * as moment from 'moment';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared/util/request-util';
import { IExerciseResult } from 'app/shared/model/exercise-result.model';

type EntityResponseType = HttpResponse<IExerciseResult>;
type EntityArrayResponseType = HttpResponse<IExerciseResult[]>;

@Injectable({ providedIn: 'root' })
export class ExerciseResultService {
    public resourceUrl = SERVER_API_URL + 'api/exercise-results';

    constructor(protected http: HttpClient) {}

    create(exerciseResult: IExerciseResult): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(exerciseResult);
        return this.http
            .post<IExerciseResult>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    update(exerciseResult: IExerciseResult): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(exerciseResult);
        return this.http
            .put<IExerciseResult>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<IExerciseResult>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<IExerciseResult[]>(this.resourceUrl, { params: options, observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }

    delete(id: number): Observable<HttpResponse<{}>> {
        return this.http.delete(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    protected convertDateFromClient(exerciseResult: IExerciseResult): IExerciseResult {
        const copy: IExerciseResult = Object.assign({}, exerciseResult, {
            completionDate: exerciseResult.completionDate && exerciseResult.completionDate.isValid() ? exerciseResult.completionDate.toJSON() : undefined,
        });
        return copy;
    }

    protected convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.completionDate = res.body.completionDate ? moment(res.body.completionDate) : undefined;
        }
        return res;
    }

    protected convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((exerciseResult: IExerciseResult) => {
                exerciseResult.completionDate = exerciseResult.completionDate ? moment(exerciseResult.completionDate) : undefined;
            });
        }
        return res;
    }
}
