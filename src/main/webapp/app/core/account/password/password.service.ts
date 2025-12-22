import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Service for managing password changes for authenticated users.
 * Communicates with the backend to update user passwords.
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
     * @returns Observable that completes on success, or errors with HTTP 400 if current password is incorrect
     */
    changePassword(newPassword: string, currentPassword: string): Observable<void> {
        return this.http.post<void>('api/core/account/change-password', { currentPassword, newPassword });
    }
}
