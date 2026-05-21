import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { User } from 'app/core/user/user.model';

/**
 * Service for registering new user accounts.
 * Handles the HTTP communication with the registration endpoint.
 */
@Injectable({ providedIn: 'root' })
export class RegisterService {
    private http = inject(HttpClient);

    /**
     * Registers a new user account on the server.
     * The registration will fail if:
     * - The password doesn't meet length requirements
     * - The username is already taken
     * - The email address is already registered
     * - Registration is disabled or blocked by admin
     *
     * @param userData - The user data including name, email, login, password, and language preference
     * @returns Observable that completes on success or errors with details about the failure
     */
    registerUser(userData: User): Observable<void> {
        return this.http.post<void>('api/core/public/register', userData);
    }
}
