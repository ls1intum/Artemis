import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { OnlineUnit } from 'app/entities/lecture-unit/onlineUnit.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

type EntityResponseType = HttpResponse<OnlineUnit>;

@Injectable({
    providedIn: 'root',
})
export class OnlineUnitService {
    private resourceURL = SERVER_API_URL + 'api';

    constructor(private httpClient: HttpClient, private lectureUnitService: LectureUnitService) {}

    create(onlineUnit: OnlineUnit, lectureId: number): Observable<EntityResponseType> {
        return this.httpClient
            .post<OnlineUnit>(`${this.resourceURL}/lectures/${lectureId}/online-units`, onlineUnit, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.lectureUnitService.convertDateFromServerResponse(res)));
    }

    findById(onlineUnitId: number, lectureId: number) {
        return this.httpClient
            .get<OnlineUnit>(`${this.resourceURL}/lectures/${lectureId}/online-units/${onlineUnitId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.lectureUnitService.convertDateFromServerResponse(res)));
    }

    update(onlineUnit: OnlineUnit, lectureId: number): Observable<EntityResponseType> {
        return this.httpClient
            .put<OnlineUnit>(`${this.resourceURL}/lectures/${lectureId}/online-units`, onlineUnit, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.lectureUnitService.convertDateFromServerResponse(res)));
    }
}
