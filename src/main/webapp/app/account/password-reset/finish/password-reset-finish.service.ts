import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class PasswordResetFinishService {
    constructor(private http: HttpClient) {}

    save(key: string, newPassword: string): Observable<any> {
        return this.http.post('api/account/reset-password/finish', { key, newPassword });
    }
}
