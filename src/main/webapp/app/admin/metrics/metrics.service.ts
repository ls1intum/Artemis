import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';

@Injectable({ providedIn: 'root' })
export class JhiMetricsService {
    constructor(private http: HttpClient) {}

    /**
     * Send GET request to retrieve all metrics
     */
    getMetrics(): Observable<any> {
        return this.http.get(SERVER_API_URL + 'management/jhimetrics');
    }

    /**
     * Send GET request to retrieve all thread dumps
     */
    threadDump(): Observable<any> {
        return this.http.get(SERVER_API_URL + 'management/threaddump');
    }

    /**
     * Send GET request to retrieve user metrics (number of active users)
     */
    getUserMetrics(): Observable<any> {
        return this.http.get(SERVER_API_URL + 'api/management/usermetrics');
    }
}
