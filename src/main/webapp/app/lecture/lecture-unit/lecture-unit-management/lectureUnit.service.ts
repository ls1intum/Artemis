import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import * as moment from 'moment';
import { Injectable } from '@angular/core';

type EntityResponseType = HttpResponse<LectureUnit>;
type EntityArrayResponseType = HttpResponse<LectureUnit[]>;

@Injectable({
    providedIn: 'root',
})
export class LectureUnitService {
    private resourceURL = SERVER_API_URL + 'api';

    constructor(private httpClient: HttpClient) {}

    updateOrder(lectureId: number, lectureUnits: LectureUnit[]): Observable<HttpResponse<LectureUnit[]>> {
        return this.httpClient
            .put<LectureUnit[]>(`${this.resourceURL}/lectures/${lectureId}/lecture-units-order`, lectureUnits, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }

    delete(lectureUnitId: number, lectureId: number) {
        return this.httpClient.delete(`${this.resourceURL}/lectures/${lectureId}/lecture-units/${lectureUnitId}`, { observe: 'response' });
    }

    convertDateFromClient<T extends LectureUnit>(lectureUnit: T): T {
        return Object.assign({}, lectureUnit, {
            releaseDate: lectureUnit.releaseDate && moment(lectureUnit.releaseDate).isValid() ? lectureUnit.releaseDate.toJSON() : undefined,
        });
    }

    convertDateFromServer<T extends LectureUnit>(res: HttpResponse<T>): HttpResponse<T> {
        if (res.body) {
            res.body.releaseDate = res.body.releaseDate ? moment(res.body.releaseDate) : undefined;
        }
        return res;
    }

    convertDateArrayFromServer<T extends LectureUnit>(res: HttpResponse<T[]>): HttpResponse<T[]> {
        if (res.body) {
            res.body.forEach((lectureUnit: LectureUnit) => {
                lectureUnit.releaseDate = lectureUnit.releaseDate ? moment(lectureUnit.releaseDate) : undefined;
            });
        }
        return res;
    }
}
