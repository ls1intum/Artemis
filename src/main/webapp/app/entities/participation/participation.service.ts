import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { DATE_FORMAT } from 'app/shared/constants/input.constants';
import { map } from 'rxjs/operators';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IParticipation } from 'app/shared/model/participation.model';

type EntityResponseType = HttpResponse<IParticipation>;
type EntityArrayResponseType = HttpResponse<IParticipation[]>;

@Injectable({ providedIn: 'root' })
export class ParticipationService {
    public resourceUrl = SERVER_API_URL + 'api/participations';

    constructor(private http: HttpClient) {}

    create(participation: IParticipation): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(participation);
        return this.http
            .post<IParticipation>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    update(participation: IParticipation): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(participation);
        return this.http
            .put<IParticipation>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<IParticipation>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<IParticipation[]>(this.resourceUrl, { params: options, observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    private convertDateFromClient(participation: IParticipation): IParticipation {
        const copy: IParticipation = Object.assign({}, participation, {
            initializationDate:
                participation.initializationDate != null && participation.initializationDate.isValid()
                    ? participation.initializationDate.toJSON()
                    : null
        });
        return copy;
    }

    private convertDateFromServer(res: EntityResponseType): EntityResponseType {
        res.body.initializationDate = res.body.initializationDate != null ? moment(res.body.initializationDate) : null;
        return res;
    }

    private convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        res.body.forEach((participation: IParticipation) => {
            participation.initializationDate = participation.initializationDate != null ? moment(participation.initializationDate) : null;
        });
        return res;
    }
}
