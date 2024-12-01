import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class PasswordService {
    private http = inject(HttpClient);

    /**
     * Sets a new password for the current user. Receives an HTTP 400 if the old password is incorrect.
     *
     * @param newPassword The new password
     * @param currentPassword The old password
     */
    save(newPassword: string, currentPassword: string): Observable<any> {
        return this.http.post('api/account/change-password', { currentPassword, newPassword });
    }
}
