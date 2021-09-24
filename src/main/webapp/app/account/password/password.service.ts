import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class PasswordService {
    constructor(private http: HttpClient) {}

    /**
     * Sets a new password for the current user. Receives an HTTP 400 if the old password is incorrect.
     *
     * @param newPassword The new password
     * @param currentPassword The old password
     */
    save(newPassword: string, currentPassword: string): Observable<{}> {
        return this.http.post(SERVER_API_URL + 'api/account/change-password', { currentPassword, newPassword });
    }
}
