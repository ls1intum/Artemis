import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';
import { SpanType } from 'app/entities/statistics.model';

@Injectable({ providedIn: 'root' })
export class StatisticsService {
    private resourceUrl = SERVER_API_URL + 'api/management/statistics/';

    constructor(private http: HttpClient) {}

    /**
     * Sends a GET request to retrieve the amount of submissions made in the last *span* days
     */
    getTotalSubmissions(span: SpanType, periodIndex: number): Observable<number[]> {
        const params = new HttpParams().set('span', '' + span).set('periodIndex', '' + periodIndex);
        return this.http.get<number[]>(`${this.resourceUrl}submissions`, { params });
    }
}
