import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { Metrics, ThreadDump } from './metrics.model';

@Injectable({ providedIn: 'root' })
export class MetricsService {
    private http = inject(HttpClient);

    getMetrics(): Observable<Metrics> {
        return this.http.get<Metrics>('management/jhimetrics');
    }

    threadDump(): Observable<ThreadDump> {
        return this.http.get<ThreadDump>('management/threaddump');
    }
}
