import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { BehaviorSubject, Observable } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/account/user/user.model';
import { Authority } from 'app/foundation/constants/authority.constants';
import { HasAnyAuthorityDirective } from 'app/foundation/auth/has-any-authority.directive';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

@Component({
    template: `<div *jhiHasAnyAuthority="[Authority.ADMIN]" id="guarded">secret</div>`,
    imports: [HasAnyAuthorityDirective],
})
class HostComponent {
    protected readonly Authority = Authority;
}

/**
 * Minimal AccountService stand-in that faithfully reproduces the runtime behaviour relevant to the directive:
 *  - `getAuthenticationState()` returns a `BehaviorSubject` that synchronously replays its current value on subscribe.
 *  - `hasAnyAuthorityDirect()` mirrors the real implementation, including the `authorities.length` dereference that
 *    throws a `TypeError` when `authorities` is `undefined` for an already-authenticated user.
 */
class MockAccountService {
    readonly authenticationState: BehaviorSubject<User | undefined>;

    constructor(user: User | undefined) {
        this.authenticationState = new BehaviorSubject<User | undefined>(user);
    }

    getAuthenticationState(): Observable<User | undefined> {
        return this.authenticationState.asObservable();
    }

    private currentUser(): User | undefined {
        return this.authenticationState.getValue();
    }

    authenticated(): boolean {
        return !!this.currentUser();
    }

    userIdentity(): User | undefined {
        return this.currentUser();
    }

    hasAnyAuthority(authorities: readonly Authority[]): Promise<boolean> {
        return Promise.resolve(this.hasAnyAuthorityDirect(authorities));
    }

    // Faithful copy of AccountService.hasAnyAuthorityDirect so the spec exercises the exact code path that crashed.
    hasAnyAuthorityDirect(authorities: readonly Authority[]): boolean {
        const user = this.currentUser();
        if (!user || !user.authorities) {
            return false;
        }
        for (let i = 0; i < authorities.length; i++) {
            if (user.authorities.includes(authorities[i])) {
                return true;
            }
        }
        return false;
    }
}

function userWithAuthorities(authorities: string[]): User {
    return { id: 99, login: 'admin', authorities } as User;
}

describe('HasAnyAuthorityDirective', () => {
    setupTestBed({ zoneless: true });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    async function configure(user: User | undefined): Promise<MockAccountService> {
        const mock = new MockAccountService(user);
        await TestBed.configureTestingModule({
            imports: [HostComponent],
            providers: [{ provide: AccountService, useValue: mock }],
        }).compileComponents();
        return mock;
    }

    it('does not throw when created while already authenticated', async () => {
        // Authentication state already holds a user BEFORE the host (and thus the directive) is created.
        // The directive's constructor subscription replays this value synchronously, calling updateView() before
        // the input effect has populated `authorities`. With `authorities` initialised to [] this is harmless;
        // without that initialisation, hasAnyAuthorityDirect(undefined) dereferences `undefined.length` and throws.
        await configure(userWithAuthorities([Authority.ADMIN]));

        expect(() => {
            const fixture = TestBed.createComponent(HostComponent);
            fixture.detectChanges();
        }).not.toThrow();
    });

    it('renders the element when the user has the authority', async () => {
        await configure(userWithAuthorities([Authority.ADMIN]));

        const fixture = TestBed.createComponent(HostComponent);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('#guarded')).toBeTruthy();
    });

    it('hides the element when the user lacks the authority', async () => {
        await configure(userWithAuthorities([Authority.STUDENT]));

        const fixture = TestBed.createComponent(HostComponent);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('#guarded')).toBeNull();
    });
});
