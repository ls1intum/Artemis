import { Service, inject } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SKIP_HTTP_ERROR_ALERT } from 'app/core/interceptor/errorhandler.interceptor';

/** Which of the two addresses of an email change request a confirmation link belongs to. */
export type ConfirmedEmailAddress = 'CURRENT' | 'NEW';

/** Result of confirming one address of an email change request. Must match the server-side response. */
export interface ConfirmEmailChangeResponse {
    /** True when both addresses are confirmed and the email address of the account was changed. */
    completed: boolean;
    /** The address that was confirmed by this request. */
    address: ConfirmedEmailAddress;
}

/**
 * Service for confirming an email change from the link in a confirmation email.
 * The endpoint is public, because the link is opened from a mail client, possibly without a session.
 */
@Service()
export class ConfirmEmailChangeService {
    private http = inject(HttpClient);

    /**
     * Confirms one address of a pending email change request.
     * The page shows failures itself, so no global error alert is raised.
     *
     * @param keyId - The id of the key from the confirmation email link
     * @param keySecret - The secret of the key from the confirmation email link
     * @returns Observable with the result, or an error for an invalid, expired or used key
     */
    confirmEmailChange(keyId: string, keySecret: string): Observable<ConfirmEmailChangeResponse> {
        return this.http.post<ConfirmEmailChangeResponse>(
            'api/core/public/account/confirm-email-change',
            { keyId, keySecret },
            { context: new HttpContext().set(SKIP_HTTP_ERROR_ALERT, true) },
        );
    }
}
