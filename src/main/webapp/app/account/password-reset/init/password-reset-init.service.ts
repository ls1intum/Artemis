import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class PasswordResetInitService {
    constructor(private http: HttpClient) {}

    save(mail: string): Observable<object> {
        return this.http.post('api/public/account/reset-password/init', mail);
    }
}
