import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SERVER_API_URL } from 'app/environments/environment';

@Injectable({ providedIn: 'root' })
export class PasswordResetFinishService {
    constructor(private http: HttpClient) {}

    save(key: string, newPassword: string): Observable<any> {
        return this.http.post(SERVER_API_URL + 'api/account/reset-password/finish', { key, newPassword });
    }
}
