import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class PasswordResetInitService {
    private http = inject(HttpClient);

    save(mail: string): Observable<object> {
        return this.http.post('api/public/account/reset-password/init', mail);
    }
}
