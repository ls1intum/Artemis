import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { Observable } from 'rxjs';

/**
 * Normally the use of null in the client is discouraged but in this case it is fine as the server will always send all
 * the properties but will give them the value null (a property will never be undefined here).
 */
export class ParticipantScoreDTO {
    public id: number | null;
    public userId: number | null;
    public userName: string | null;
    public teamId: number | null;
    public teamName: string | null;
    public exerciseId: number | null;
    public exerciseTitle: string | null;
    public lastResultId: number | null;
    public lastResultScore: number | null;
    public lastRatedResultId: number | null;
    public lastRatedResultScore: number | null;
}

export type SortDirection = 'asc' | 'desc';
export type SortProperty = keyof ParticipantScoreDTO;
export type SortingParameter = {
    sortProperty: SortProperty;
    sortDirection: SortDirection;
};

@Injectable({ providedIn: 'root' })
export class ParticipantScoresService {
    public resourceUrl = SERVER_API_URL + 'api';

    constructor(private http: HttpClient) {}

    findAllOfCoursePaged(courseId: number, sortingParameters: SortingParameter[], page: number, size: number): Observable<HttpResponse<ParticipantScoreDTO[]>> {
        let params = new HttpParams();
        for (const sortParameter of sortingParameters) {
            params = params.append('sort', `${sortParameter.sortProperty},${sortParameter.sortDirection}`);
        }
        params = params.set('page', page + '');
        params = params.set('size', size + '');
        return this.http.get<ParticipantScoreDTO[]>(`${this.resourceUrl}/courses/${courseId}/participant-scores`, {
            observe: 'response',
            params,
        });
    }

    findAllOfCourse(courseId: number): Observable<HttpResponse<ParticipantScoreDTO[]>> {
        let params = new HttpParams();
        params = params.set('getUnpaged', 'true');
        return this.http.get<ParticipantScoreDTO[]>(`${this.resourceUrl}/courses/${courseId}/participant-scores`, {
            observe: 'response',
            params,
        });
    }
}
