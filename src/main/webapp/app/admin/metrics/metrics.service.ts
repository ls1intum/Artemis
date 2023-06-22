import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { Metrics, ThreadDump } from './metrics.model';

@Injectable({ providedIn: 'root' })
export class MetricsService {
    constructor(private http: HttpClient) {}

    getMetrics(): Observable<Metrics> {
        return this.http.get<Metrics>('management/jhimetrics');
    }

    threadDump(): Observable<ThreadDump> {
        return this.http.get<ThreadDump>('management/threaddump');
    }
}
