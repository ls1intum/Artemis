import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';

@Injectable({ providedIn: 'root' })
export class StatisticsService {
    private resourceUrl = SERVER_API_URL + 'api/';

    constructor(private http: HttpClient) {}

    /**
     * Sends a GET request to retrieve the amount of logged in users in the last *span* days
     */
    getloggedUsers(span: number): Observable<number> {
        const params = new HttpParams().set('span', '' + span);
        return this.http.get<number>(`${this.resourceUrl}management/statistics`, { params });
    }
}
