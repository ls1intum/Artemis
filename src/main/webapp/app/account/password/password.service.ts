import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Which of the user's other credentials the server should revoke alongside the password change.
 * Each of these is enough on its own to keep using the account, so a suspected leak warrants revoking them, while a
 * routine rotation does not.
 */
export interface CredentialRevocationChoice {
    passkeys: boolean;
    sshKeys: boolean;
    vcsAccessTokens: boolean;
}

/**
 * Service for managing password changes for authenticated users.
 * Communicates with the server to update user passwords.
 */
@Injectable({ providedIn: 'root' })
export class PasswordService {
    private http = inject(HttpClient);

    /**
     * Changes the password for the currently authenticated user.
     * The current password is required for verification before the change is applied.
     *
     * @param newPassword - The new password to set
     * @param currentPassword - The user's current password for verification
     * @param revokeCredentials - Which other credentials to revoke; omitted means none are revoked
     * @returns Observable that completes on success, or errors with HTTP 400 if current password is incorrect
     */
    changePassword(newPassword: string, currentPassword: string, revokeCredentials?: CredentialRevocationChoice): Observable<void> {
        return this.http.post<void>('api/account/change-password', { currentPassword, newPassword, revokeCredentials });
    }
}
