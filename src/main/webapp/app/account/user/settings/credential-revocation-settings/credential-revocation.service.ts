import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CredentialRevocationChoice } from 'app/account/password/password.service';

/**
 * Revokes the credentials of the current user without changing their password.
 * <p>
 * Separate from {@link PasswordService} because the two are not variants of the same action: changing a password is only
 * open to internal users, while revoking credentials has to work for everyone, external users included.
 */
@Injectable({ providedIn: 'root' })
export class CredentialRevocationService {
    private readonly http = inject(HttpClient);

    /**
     * Revokes the selected credential types of the current user.
     *
     * @param choice which credential types to revoke; the server rejects a choice that selects none
     * @returns Observable that completes on success
     */
    revokeCredentials(choice: CredentialRevocationChoice): Observable<void> {
        return this.http.post<void>('api/account/revoke-credentials', choice);
    }
}
