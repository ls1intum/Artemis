import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/** Which credentials the user chose to revoke alongside the reset. */
export interface CredentialRevocationChoice {
    passkeys: boolean;
    sshKeys: boolean;
    vcsAccessTokens: boolean;
}

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
     * @param email - The email to which the reset password email was send
     * @param resetKey - The secret for the key from the password reset email
     * @param newPassword - The new password to set for the account
     * @param revokeCredentials - which other credentials to revoke alongside the reset. Omitting it makes the server
     *                            revoke all of them, which is the safe default for a flow that only proves mailbox access.
     * @returns Observable that completes on success, or errors if the key is invalid/expired
     */
    completePasswordReset(email: string, resetKey: string, newPassword: string, revokeCredentials?: CredentialRevocationChoice): Observable<object> {
        return this.http.post('api/core/public/account/reset-password/finish', { email: email, resetKey: resetKey, newPassword, revokeCredentials });
    }
}
