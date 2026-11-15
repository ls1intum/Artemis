import { Service, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, map, of, timeout } from 'rxjs';
import { User } from 'app/account/user/user.model';
import { EMAIL_ALLOWED_TIMEOUT_MS, EMAIL_ALLOWED_URL, EmailAllowedResponse, emailAllowedRequestContext } from 'app/account/shared/email-allowed';

/**
 * Service for registering new user accounts.
 * Handles the HTTP communication with the registration endpoint.
 */
@Service()
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

    /**
     * Asks the server whether an email address is allowed by the restriction that the administrators configured, so that the form can say so while the address is typed.
     * The server makes the same decision again when the form is sent.
     *
     * @param email - The address as it was entered
     * @returns Observable of whether the address is allowed. It is {@code true} if the server cannot answer in time (for example because of a limit on requests), so that the form is not blocked.
     */
    isEmailAllowed(email: string): Observable<boolean> {
        return this.http.post<EmailAllowedResponse>(EMAIL_ALLOWED_URL, { email }, { context: emailAllowedRequestContext() }).pipe(
            timeout(EMAIL_ALLOWED_TIMEOUT_MS),
            map((response) => response.allowed),
            catchError(() => of(true)),
        );
    }
}
