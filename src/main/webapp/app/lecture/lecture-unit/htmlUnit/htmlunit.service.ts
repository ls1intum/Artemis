import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { HTMLUnit } from 'app/entities/lecture-unit/htmlUnit.model';
import * as moment from 'moment';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

type EntityResponseType = HttpResponse<HTMLUnit>;
type EntityArrayResponseType = HttpResponse<HTMLUnit[]>;

@Injectable({
    providedIn: 'root',
})
export class HTMLUnitService {
    private resourceURL = SERVER_API_URL + 'api/html-units';

    constructor(private httpClient: HttpClient) {}

    /**
     * Creates a new HTMLUnit on the server using a POST request.
     * @param htmlUnit - the HTMLUnit to create
     */
    create(htmlUnit: HTMLUnit): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(htmlUnit);
        return this.httpClient
            .post<HTMLUnit>(this.resourceURL, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * Convert all dates of a client-HTMLUnit to the server timezone
     * @param htmlUnit - the HTMLUnit from client whose date is adjusted
     */
    private convertDateFromClient(htmlUnit: HTMLUnit): HTMLUnit {
        return Object.assign({}, htmlUnit, {
            releaseDate: htmlUnit.releaseDate && moment(htmlUnit.releaseDate).isValid() ? moment(htmlUnit.releaseDate).toJSON() : undefined,
        });
    }

    /**
     * Replaces dates in http-response including an HTMLUnit with the corresponding client time
     * @param res - the response from server including one HTMLUnit
     */
    private convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.releaseDate = res.body.releaseDate ? moment(res.body.releaseDate) : undefined;
        }
        return res;
    }

    /**
     * Replaces dates in http-response including an array of HTMLUnits with the corresponding client time
     * @param res - the response from server including an array of HTMLUnits
     */
    private convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((htmlUnit: HTMLUnit) => {
                htmlUnit.releaseDate = htmlUnit.releaseDate ? moment(htmlUnit.releaseDate) : undefined;
            });
        }
        return res;
    }
}
