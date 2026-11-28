import { describe, expect, it, vi } from 'vitest';
import { signal } from '@angular/core';
import { Subject } from 'rxjs';
import { User } from 'app/account/user/user.model';
import { onUserChange } from './user-change.util';

describe('onUserChange', () => {
    it('resets user-scoped state only when the authenticated user id changes', () => {
        const authentication = new Subject<User | undefined>();
        const reset = vi.fn();
        const subscription = onUserChange({ userIdentity: signal<User | undefined>({ id: 1 } as User), getAuthenticationState: () => authentication }, reset);
        authentication.next({ id: 1 } as User);
        expect(reset).not.toHaveBeenCalled();
        authentication.next({ id: 2 } as User);
        authentication.next({ id: 2 } as User);
        authentication.next(undefined);
        authentication.next(undefined);
        expect(reset).toHaveBeenCalledTimes(2);
        subscription.unsubscribe();
        authentication.next({ id: 3 } as User);
        expect(reset).toHaveBeenCalledTimes(2);
    });
});
