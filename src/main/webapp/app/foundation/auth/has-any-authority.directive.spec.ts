import { Component, computed, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { beforeEach, describe, expect, it } from 'vitest';
import { HasAnyAuthorityDirective } from 'app/foundation/auth/has-any-authority.directive';
import { AccountService } from 'app/core/auth/account.service';
import { Authority } from 'app/foundation/constants/authority.constants';

/**
 * Minimal signal-backed AccountService stub mirroring the real service's contract: hasAnyAuthorityDirect()
 * reads the userIdentity()/authenticated() signals, so the directive's effect tracks them and re-renders on
 * login/logout. Used to verify the directive is driven purely by signals (no manual subscription).
 */
class SignalAccountServiceStub {
    userIdentity = signal<{ authorities: string[] } | undefined>(undefined);
    authenticated = computed(() => !!this.userIdentity());

    hasAnyAuthorityDirect(authorities: readonly Authority[]): boolean {
        const identity = this.userIdentity();
        if (!this.authenticated() || !identity?.authorities) {
            return false;
        }
        return authorities.some((authority) => identity.authorities.includes(authority));
    }
}

@Component({
    template: '<div *jhiHasAnyAuthority="requiredAuthority()">secret content</div>',
    imports: [HasAnyAuthorityDirective],
})
class HostComponent {
    requiredAuthority = signal<string | string[]>(Authority.ADMIN);
}

describe('HasAnyAuthorityDirective', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<HostComponent>;
    let account: SignalAccountServiceStub;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [HostComponent],
            providers: [{ provide: AccountService, useClass: SignalAccountServiceStub }],
        }).compileComponents();
        account = TestBed.inject(AccountService) as unknown as SignalAccountServiceStub;
    });

    function content(): string {
        return fixture.nativeElement.textContent ?? '';
    }

    it('should not throw NG0950 when instantiated logged out and should hide the content', () => {
        // Regression guard: reading the required input must happen inside the effect (after inputs are set),
        // never during construction. A constructor subscription to the (BehaviorSubject-backed) auth state
        // would read the required input synchronously during construction and throw NG0950.
        expect(() => {
            fixture = TestBed.createComponent(HostComponent);
            fixture.detectChanges();
        }).not.toThrow();
        expect(content()).not.toContain('secret content');
    });

    it('should render the content when the user has the required authority', () => {
        account.userIdentity.set({ authorities: [Authority.ADMIN] });
        fixture = TestBed.createComponent(HostComponent);
        fixture.detectChanges();
        expect(content()).toContain('secret content');
    });

    it('should show the content on login and hide it on logout (signal-driven, no manual subscription)', () => {
        fixture = TestBed.createComponent(HostComponent);
        fixture.detectChanges();
        expect(content()).not.toContain('secret content');

        // login with a matching authority -> the effect re-runs because hasAnyAuthorityDirect() reads userIdentity()
        account.userIdentity.set({ authorities: [Authority.ADMIN] });
        fixture.detectChanges();
        expect(content()).toContain('secret content');

        // logout -> hidden again
        account.userIdentity.set(undefined);
        fixture.detectChanges();
        expect(content()).not.toContain('secret content');
    });

    it('should re-evaluate when the required authority input changes', () => {
        account.userIdentity.set({ authorities: [Authority.ADMIN] });
        fixture = TestBed.createComponent(HostComponent);
        fixture.detectChanges();
        expect(content()).toContain('secret content');

        // require an authority the user does NOT have -> content disappears
        fixture.componentInstance.requiredAuthority.set(Authority.STUDENT);
        fixture.detectChanges();
        expect(content()).not.toContain('secret content');
    });
});
