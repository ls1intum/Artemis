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
     * Sends a GET request to retrieve the data for a graph based on the graphType in the last *span* days and the given period
     */
    getChartData(span: SpanType, periodIndex: number, graphType: Graphs): Observable<number[]> {
        const params = new HttpParams()
            .set('span', '' + span)
            .set('periodIndex', '' + periodIndex)
            .set('graphType', '' + graphType);
        return this.http.get<number[]>(`${this.resourceUrl}data`, { params });
    }

    /**
     * Sends a GET request to retrieve the data for a graph based on the graphType in the last *span* days, the given period and the courseId
     */
    getChartDataForCourse(span: SpanType, periodIndex: number, graphType: Graphs, courseId: number): Observable<number[]> {
        const params = new HttpParams()
            .set('span', '' + span)
            .set('periodIndex', '' + periodIndex)
            .set('graphType', '' + graphType)
            .set('courseId', '' + courseId);
        return this.http.get<number[]>(`${this.resourceUrl}data-for-course`, { params });
    }
}
