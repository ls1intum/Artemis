import type { AccountService } from 'app/core/auth/account.service';
import { Subscription } from 'rxjs';

/** Observe identity changes without clearing caches for refreshed sessions of the same user. */
export function onUserChange(accountService: Pick<AccountService, 'userIdentity' | 'getAuthenticationState'>, resetState: () => void): Subscription {
    let currentUserId = accountService.userIdentity()?.id;
    return accountService.getAuthenticationState().subscribe((user) => {
        if (currentUserId !== user?.id) {
            currentUserId = user?.id;
            resetState();
        }
    });
}
