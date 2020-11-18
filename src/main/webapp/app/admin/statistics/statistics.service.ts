import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';

@Injectable({ providedIn: 'root' })
export class StatisticsService {
    private resourceUrl = SERVER_API_URL + 'api/';

    constructor(private http: HttpClient) {}

    /**
     * Sends a GET request to retrieve the amount of logged in users in the last *span* days
     */
    getloggedUsers(span: number): Observable<HttpResponse<number>> {
        // @ts-ignore
        return this.http.get<number>(`${this.resourceUrl}` + 'management/statistics', span, { observe: 'response' });
    }
}
