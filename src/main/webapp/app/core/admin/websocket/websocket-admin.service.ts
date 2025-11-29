import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { WebsocketNode } from 'app/core/admin/websocket/websocket-node.model';

@Injectable({ providedIn: 'root' })
export class WebsocketAdminService {
    private readonly http = inject(HttpClient);
    private readonly resourceUrl = 'api/core/admin/websocket';

    getNodes(): Observable<WebsocketNode[]> {
        return this.http.get<WebsocketNode[]>(`${this.resourceUrl}/nodes`);
    }

    triggerReconnect(targetNodeId?: string): Observable<void> {
        const params = targetNodeId ? new HttpParams().set('targetNodeId', targetNodeId) : undefined;
        return this.http.post<void>(`${this.resourceUrl}/reconnect`, null, { params });
    }
}
