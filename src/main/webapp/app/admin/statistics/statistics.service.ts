import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';
import { Graphs, SpanType } from 'app/entities/statistics.model';

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

    /**
     * Sends a GET request to retrieve the amount of active users made in the last *span* days
     */
    getActiveUsers(span: SpanType, periodIndex: number): Observable<number[]> {
        const params = new HttpParams().set('span', '' + span).set('periodIndex', '' + periodIndex);
        return this.http.get<number[]>(`${this.resourceUrl}active-users`, { params });
    }

    /**
     * Sends a GET request to retrieve the amount of released exercises in the last *span* days
     */
    getReleasedExercises(span: SpanType, periodIndex: number): Observable<number[]> {
        const params = new HttpParams().set('span', '' + span).set('periodIndex', '' + periodIndex);
        return this.http.get<number[]>(`${this.resourceUrl}released-exercises`, { params });
    }

    /**
     * Sends a GET request to retrieve the data for a graph based on the graphType in the last *span* days
     */
    getChartData(span: SpanType, periodIndex: number, graphType: Graphs): Observable<number[]> {
        const params = new HttpParams()
            .set('span', '' + span)
            .set('periodIndex', '' + periodIndex)
            .set('graphType', '' + graphType);
        return this.http.get<number[]>(`${this.resourceUrl}data`, { params });
    }
}
