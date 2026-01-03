import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Service for initiating password reset requests.
 * Sends a password reset email to the user if the email/username exists.
 */
@Injectable({ providedIn: 'root' })
export class PasswordResetInitService {
    private http = inject(HttpClient);

    /**
     * Requests a password reset for the given email address or username.
     * The server will send a password reset link to the user's email if the account exists.
     * For security, the server returns success even if the email doesn't exist.
     *
     * @param emailOrUsername - The user's email address or username
     * @returns Observable that completes on success, or errors for external users
     */
    requestPasswordReset(emailOrUsername: string): Observable<object> {
        return this.http.post('api/core/public/account/reset-password/init', emailOrUsername);
    }
}
