import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class EmailNotificationSettingsService {
    private resourceUrl = 'api/communication/email-notification-settings';

    constructor(private http: HttpClient) {}

    getAll(): Observable<{ [key: string]: boolean }> {
        // Adjust the endpoint if you have a different one for all settings
        return this.http.get<{ [key: string]: boolean }>(`${this.resourceUrl}/all`);
    }

    update(type: string, enabled: boolean): Observable<any> {
        return this.http.put(`${this.resourceUrl}/${type}`, { enabled });
    }
}
