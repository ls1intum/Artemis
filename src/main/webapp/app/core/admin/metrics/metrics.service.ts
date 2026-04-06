import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { Metrics, NodeInfo, ThreadDump } from './metrics.model';

@Injectable({ providedIn: 'root' })
export class MetricsService {
    private http = inject(HttpClient);

    getMetrics(nodeId?: string): Observable<Metrics> {
        const url = nodeId && nodeId !== 'all' ? `management/artemismetrics/${nodeId}` : 'management/artemismetrics';
        return this.http.get<Metrics>(url);
    }

    getAvailableNodes(): Observable<NodeInfo[]> {
        return this.http.get<NodeInfo[]>('management/artemismetrics/nodes');
    }

    threadDump(): Observable<ThreadDump> {
        return this.http.get<ThreadDump>('management/threaddump');
    }
}
