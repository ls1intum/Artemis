import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Service for completing password reset requests.
 * Validates the reset key and sets the new password.
 */
@Injectable({ providedIn: 'root' })
export class PasswordResetFinishService {
    private http = inject(HttpClient);

    /**
     * Completes the password reset by setting a new password.
     * The reset key from the email link is validated before the password is changed.
     *
     * @param resetKey - The unique key from the password reset email
     * @param newPassword - The new password to set for the account
     * @returns Observable that completes on success, or errors if the key is invalid/expired
     */
    completePasswordReset(resetKey: string, newPassword: string): Observable<object> {
        return this.http.post('api/core/public/account/reset-password/finish', { key: resetKey, newPassword });
    }
}
