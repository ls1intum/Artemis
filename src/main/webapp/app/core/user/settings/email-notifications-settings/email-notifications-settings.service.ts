import { Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class EmailNotificationSettingsService {
    private resourceUrl = 'api/communication/email-notification-settings';

    private httpClient = inject(HttpClient);

    getAll(): Observable<{ [key: string]: boolean }> {
        return this.httpClient.get<{ [key: string]: boolean }>(`${this.resourceUrl}`);
    }

    update(type: string, enabled: boolean): Observable<any> {
        return this.httpClient.put(`${this.resourceUrl}/${type}`, { enabled });
    }
}
