import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Service responsible for activating user accounts after registration.
 * Communicates with the server to validate activation keys sent via email.
 */
@Injectable({ providedIn: 'root' })
export class ActivateService {
    private http = inject(HttpClient);

    /**
     * Activates a user account using the provided activation key.
     * The key is sent to users via email after registration.
     *
     * @param activationKey - The unique key from the activation email link
     * @returns Observable that completes on successful activation or errors on failure
     */
    activate(activationKey: string): Observable<object> {
        return this.http.get('api/core/public/activate', {
            params: new HttpParams().set('key', activationKey),
        });
    }
}
