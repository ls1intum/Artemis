import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class PasswordResetInitService {
    constructor(private http: HttpClient) {}

    save(mail: string): Observable<{}> {
        return this.http.post(SERVER_API_URL + 'api/public/account/reset-password/init', mail);
    }
}
