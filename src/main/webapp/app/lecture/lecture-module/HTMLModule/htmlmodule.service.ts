import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { HTMLModule } from 'app/entities/lecture-module/HTMLModule';
import * as moment from 'moment';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

type EntityResponseType = HttpResponse<HTMLModule>;
type EntityArrayResponseType = HttpResponse<HTMLModule[]>;

@Injectable({
    providedIn: 'root',
})
export class HTMLModuleService {
    private resourceURL = SERVER_API_URL + 'api/html-modules';

    constructor(private httpClient: HttpClient) {}

    /**
     * Creates a new HTMLModule on the server using a POST request.
     * @param htmlModule - the HTMLModule to create
     */
    create(htmlModule: HTMLModule): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(htmlModule);
        return this.httpClient
            .post<HTMLModule>(this.resourceURL, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * Convert all dates of a client-HTMLModule to the server timezone
     * @param htmlModule - the HTMLModule from client whose date is adjusted
     */
    private convertDateFromClient(htmlModule: HTMLModule): HTMLModule {
        return Object.assign({}, htmlModule, {
            releaseDate: htmlModule.releaseDate && moment(htmlModule.releaseDate).isValid() ? moment(htmlModule.releaseDate).toJSON() : undefined,
        });
    }

    /**
     * Replaces dates in http-response including an HTMLModule with the corresponding client time
     * @param res - the response from server including one HTMLModule
     */
    private convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.releaseDate = res.body.releaseDate ? moment(res.body.releaseDate) : undefined;
        }
        return res;
    }

    /**
     * Replaces dates in http-response including an array of HTMLModules with the corresponding client time
     * @param res - the response from server including an array of HTMLModules
     */
    private convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((htmlModule: HTMLModule) => {
                htmlModule.releaseDate = htmlModule.releaseDate ? moment(htmlModule.releaseDate) : undefined;
            });
        }
        return res;
    }
}
