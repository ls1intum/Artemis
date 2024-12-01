import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class PasswordResetFinishService {
    private http = inject(HttpClient);

    save(key: string, newPassword: string): Observable<object> {
        return this.http.post('api/public/account/reset-password/finish', { key, newPassword });
    }
}
