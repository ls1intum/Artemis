import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { OnlineUnit } from 'app/entities/lecture-unit/onlineUnit.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { OnlineResourceDTO } from 'app/lecture/lecture-unit/lecture-unit-management/online-resource-dto.model';

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
            .pipe(map((res: EntityResponseType) => this.lectureUnitService.convertLectureUnitResponseDatesFromServer(res)));
    }

    findById(onlineUnitId: number, lectureId: number) {
        return this.httpClient
            .get<OnlineUnit>(`${this.resourceURL}/lectures/${lectureId}/online-units/${onlineUnitId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.lectureUnitService.convertLectureUnitResponseDatesFromServer(res)));
    }

    update(onlineUnit: OnlineUnit, lectureId: number): Observable<EntityResponseType> {
        return this.httpClient
            .put<OnlineUnit>(`${this.resourceURL}/lectures/${lectureId}/online-units`, onlineUnit, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.lectureUnitService.convertLectureUnitResponseDatesFromServer(res)));
    }

    /**
     * Get the online resource (meta title and description) of the specified link
     * @param link The link from which to fetch the website's meta information
     */
    getOnlineResource(link: string): Observable<HttpResponse<OnlineResourceDTO>> {
        let params = new HttpParams();
        params = params.set('link', String(link));
        return this.httpClient.get<OnlineResourceDTO>(`${this.resourceURL}/lectures/online-units/fetch-online-resource`, {
            observe: 'response',
            params,
        });
    }
}
