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

    private convertDateFromClient(lectureUnit: LectureUnit): LectureUnit {
        return Object.assign({}, lectureUnit, {
            releaseDate: lectureUnit.releaseDate && moment(lectureUnit.releaseDate).isValid() ? lectureUnit.releaseDate.toJSON() : undefined,
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
            res.body.forEach((lectureUnit: LectureUnit) => {
                lectureUnit.releaseDate = lectureUnit.releaseDate ? moment(lectureUnit.releaseDate) : undefined;
            });
        }
        return res;
    }
}
