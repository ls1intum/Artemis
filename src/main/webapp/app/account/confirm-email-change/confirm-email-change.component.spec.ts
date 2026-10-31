/**
 * Vitest tests for ConfirmEmailChangeComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { Subject, of, throwError } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { ConfirmEmailChangeComponent } from 'app/account/confirm-email-change/confirm-email-change.component';
import { ConfirmEmailChangeResponse, ConfirmEmailChangeService } from 'app/account/confirm-email-change/confirm-email-change.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ConfirmEmailChangeComponent', () => {
    let fixture: ComponentFixture<ConfirmEmailChangeComponent>;
    let service: ConfirmEmailChangeService;
    let accountService: AccountService;
    let confirmSpy: ReturnType<typeof vi.spyOn>;

    function setUp(queryParams: Record<string, string>) {
        TestBed.configureTestingModule({
            imports: [ConfirmEmailChangeComponent],
            providers: [
                provideRouter([]),
                { provide: ActivatedRoute, useValue: new MockActivatedRoute(queryParams) },
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ConfirmEmailChangeService, useValue: { confirmEmailChange: vi.fn() } },
            ],
        });
        service = TestBed.inject(ConfirmEmailChangeService);
        accountService = TestBed.inject(AccountService);
        confirmSpy = vi.spyOn(service, 'confirmEmailChange');
        fixture = TestBed.createComponent(ConfirmEmailChangeComponent);
        fixture.detectChanges();
    }

    function byTestId(testId: string) {
        return fixture.debugElement.query(By.css(`[data-testid="${testId}"]`));
    }

    function clickConfirm() {
        byTestId('confirm-email-change-button').query(By.css('button')).nativeElement.click();
        fixture.detectChanges();
    }

    function respondWith(response: ConfirmEmailChangeResponse) {
        confirmSpy.mockReturnValue(of(response));
    }

    function failWith(status: number, error?: object) {
        confirmSpy.mockReturnValue(throwError(() => ({ status, error })));
    }

    beforeEach(() => {
        TestBed.resetTestingModule();
    });

    describe('with a complete link', () => {
        beforeEach(() => setUp({ keyId: 'id1', keySecret: 'secret1' }));

        it('should not send a request when the page loads', () => {
            expect(confirmSpy).not.toHaveBeenCalled();
            expect(fixture.componentInstance.state()).toBe('idle');
            expect(byTestId('confirm-email-change-button')).not.toBeNull();
            expect(byTestId('confirm-email-change-key-missing')).toBeNull();
        });

        it('should post the key from the link when the button is clicked', () => {
            respondWith({ completed: false, address: 'CURRENT' });

            clickConfirm();

            expect(confirmSpy).toHaveBeenCalledExactlyOnceWith('id1', 'secret1');
        });

        it('should show that the email was changed when both addresses are confirmed', () => {
            respondWith({ completed: true, address: 'NEW' });
            const identitySpy = vi.spyOn(accountService, 'identity');
            vi.spyOn(accountService, 'isAuthenticated').mockReturnValue(true);

            clickConfirm();

            expect(byTestId('confirm-email-change-completed')).not.toBeNull();
            expect(byTestId('confirm-email-change-button')).toBeNull();
            // a signed-in user gets the new email address
            expect(identitySpy).toHaveBeenCalledWith(true);
        });

        it('should not reload the identity of a visitor who is not signed in', () => {
            respondWith({ completed: true, address: 'CURRENT' });
            const identitySpy = vi.spyOn(accountService, 'identity');
            vi.spyOn(accountService, 'isAuthenticated').mockReturnValue(false);

            clickConfirm();

            expect(byTestId('confirm-email-change-completed')).not.toBeNull();
            expect(identitySpy).not.toHaveBeenCalled();
        });

        it('should show that the other address is still missing', () => {
            respondWith({ completed: false, address: 'CURRENT' });

            clickConfirm();

            expect(byTestId('confirm-email-change-waiting')).not.toBeNull();
            expect(byTestId('confirm-email-change-completed')).toBeNull();
            expect(byTestId('confirm-email-change-button')).toBeNull();
        });

        it('should show that the link is invalid or expired', () => {
            failWith(400, { errorKey: 'invalidEmailChangeKey' });

            clickConfirm();

            expect(byTestId('confirm-email-change-invalid')).not.toBeNull();
            expect(byTestId('confirm-email-change-button')).toBeNull();
        });

        it('should show that the address is already in use', () => {
            failWith(400, { errorKey: 'emailAlreadyUsed' });

            clickConfirm();

            expect(byTestId('confirm-email-change-email-already-used')).not.toBeNull();
            expect(byTestId('confirm-email-change-button')).toBeNull();
        });

        it('should show a generic error and allow another attempt on other failures', () => {
            failWith(429);

            clickConfirm();

            expect(byTestId('confirm-email-change-error')).not.toBeNull();
            expect(byTestId('confirm-email-change-button')).not.toBeNull();

            respondWith({ completed: false, address: 'NEW' });
            clickConfirm();

            expect(confirmSpy).toHaveBeenCalledTimes(2);
            expect(byTestId('confirm-email-change-error')).toBeNull();
            expect(byTestId('confirm-email-change-waiting')).not.toBeNull();
        });

        it('should send only one request while one is running', () => {
            confirmSpy.mockReturnValue(new Subject<ConfirmEmailChangeResponse>());

            clickConfirm();
            fixture.componentInstance.confirm();

            expect(confirmSpy).toHaveBeenCalledOnce();
            expect(fixture.componentInstance.state()).toBe('running');
        });
    });

    describe('with an incomplete link', () => {
        it('should show an error and no button when the secret is missing', () => {
            setUp({ keyId: 'id1' });

            expect(byTestId('confirm-email-change-key-missing')).not.toBeNull();
            expect(byTestId('confirm-email-change-button')).toBeNull();
            fixture.componentInstance.confirm();
            expect(confirmSpy).not.toHaveBeenCalled();
        });
    });
});
