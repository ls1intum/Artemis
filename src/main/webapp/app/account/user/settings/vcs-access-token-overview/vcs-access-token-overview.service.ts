import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { VcsAccessTokenOverview, VcsAccessTokenType } from 'app/account/user/settings/vcs-access-token-overview/vcs-access-token-overview.model';

@Injectable({ providedIn: 'root' })
export class VcsAccessTokenOverviewService {
    private readonly http = inject(HttpClient);
    private readonly resourceUrl = 'api/programming/vcs-access-tokens';

    /**
     * Loads all VCS access tokens the current user owns (participation and repository-scoped), for the token overview. The token secret is never returned.
     */
    getTokens(): Observable<VcsAccessTokenOverview[]> {
        return this.http.get<VcsAccessTokenOverview[]>(this.resourceUrl);
    }

    /**
     * Revokes (deletes) a single token the current user owns. The next clone-dialog visit transparently re-mints a fresh token.
     *
     * @param tokenType whether the token is a participation or a repository-scoped token
     * @param id        the id of the token to revoke
     */
    revokeToken(tokenType: VcsAccessTokenType, id: number): Observable<void> {
        return this.http.delete<void>(`${this.resourceUrl}/${id}`, { params: { tokenType } });
    }
}
