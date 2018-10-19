import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { DATE_FORMAT } from 'app/shared/constants/input.constants';
import { map } from 'rxjs/operators';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IExerciseResult } from 'app/shared/model/exercise-result.model';

type EntityResponseType = HttpResponse<IExerciseResult>;
type EntityArrayResponseType = HttpResponse<IExerciseResult[]>;

@Injectable({ providedIn: 'root' })
export class ExerciseResultService {
    public resourceUrl = SERVER_API_URL + 'api/exercise-results';

    constructor(private http: HttpClient) {}

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

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    private convertDateFromClient(exerciseResult: IExerciseResult): IExerciseResult {
        const copy: IExerciseResult = Object.assign({}, exerciseResult, {
            completionDate:
                exerciseResult.completionDate != null && exerciseResult.completionDate.isValid()
                    ? exerciseResult.completionDate.toJSON()
                    : null
        });
        return copy;
    }

    private convertDateFromServer(res: EntityResponseType): EntityResponseType {
        res.body.completionDate = res.body.completionDate != null ? moment(res.body.completionDate) : null;
        return res;
    }

    private convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        res.body.forEach((exerciseResult: IExerciseResult) => {
            exerciseResult.completionDate = exerciseResult.completionDate != null ? moment(exerciseResult.completionDate) : null;
        });
        return res;
    }
}
