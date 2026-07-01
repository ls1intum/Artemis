import { afterEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { MockProvider } from 'ng-mocks';
import { ExternalLoginComponent } from 'app/core/auth/external-login/external-login.component';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/foundation/service/alert.service';

describe('ExternalLoginComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ExternalLoginComponent>;
    let component: ExternalLoginComponent;
    let accountService: AccountService;
    let alertService: AlertService;
    let redirectSpy: ReturnType<typeof vi.spyOn>;

    const CALLBACK = 'vscode://aet-tum.iris-thaumantias/external-login-callback';

    function setup(queryParams: Record<string, string>): void {
        TestBed.configureTestingModule({
            imports: [ExternalLoginComponent],
            providers: [
                MockProvider(AccountService),
                MockProvider(AlertService),
                { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } } },
            ],
        }).overrideTemplate(ExternalLoginComponent, '');

        fixture = TestBed.createComponent(ExternalLoginComponent);
        component = fixture.componentInstance;
        accountService = TestBed.inject(AccountService);
        alertService = TestBed.inject(AlertService);
        // window.location navigation is not exercised in jsdom; spy on the seam instead.
        redirectSpy = vi.spyOn(component as any, 'redirect').mockImplementation(() => {});
    }

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should issue a code and redirect to the callback with code and state on success', () => {
        setup({ code_challenge: 'the-challenge', callback: CALLBACK, state: 'the-state' });
        const issueSpy = vi.spyOn(accountService, 'issueExternalLoginCode').mockReturnValue(of({ code: 'one-time-code' }));

        component.ngOnInit();

        expect(issueSpy).toHaveBeenCalledWith({ codeChallenge: 'the-challenge', callback: CALLBACK });
        expect(redirectSpy).toHaveBeenCalledWith(`${CALLBACK}?code=one-time-code&state=the-state`);
        expect(component['status']()).toBe('redirecting');
    });

    it('should append code and state when the callback already has a query', () => {
        const callbackWithQuery = `${CALLBACK}?foo=bar`;
        setup({ code_challenge: 'c', callback: callbackWithQuery, state: 's' });
        vi.spyOn(accountService, 'issueExternalLoginCode').mockReturnValue(of({ code: 'x' }));

        component.ngOnInit();

        expect(redirectSpy).toHaveBeenCalledWith(`${callbackWithQuery}&code=x&state=s`);
    });

    it('should overwrite any pre-existing code/state on the callback (no duplicate params)', () => {
        setup({ code_challenge: 'c', callback: `${CALLBACK}?code=stale&state=stale`, state: 'fresh-state' });
        vi.spyOn(accountService, 'issueExternalLoginCode').mockReturnValue(of({ code: 'fresh-code' }));

        component.ngOnInit();

        expect(redirectSpy).toHaveBeenCalledWith(`${CALLBACK}?code=fresh-code&state=fresh-state`);
    });

    it('should show an error and not redirect when required params are missing', () => {
        setup({ callback: CALLBACK });
        const issueSpy = vi.spyOn(accountService, 'issueExternalLoginCode');
        const errorSpy = vi.spyOn(alertService, 'error').mockReturnValue({} as any);

        component.ngOnInit();

        expect(issueSpy).not.toHaveBeenCalled();
        expect(errorSpy).toHaveBeenCalledWith('artemisApp.externalLogin.error.missingParams');
        expect(redirectSpy).not.toHaveBeenCalled();
        expect(component['status']()).toBe('error');
    });

    it('should show an error when state is missing', () => {
        setup({ code_challenge: 'c', callback: CALLBACK });
        const issueSpy = vi.spyOn(accountService, 'issueExternalLoginCode');
        const errorSpy = vi.spyOn(alertService, 'error').mockReturnValue({} as any);

        component.ngOnInit();

        expect(issueSpy).not.toHaveBeenCalled();
        expect(errorSpy).toHaveBeenCalledWith('artemisApp.externalLogin.error.missingParams');
        expect(redirectSpy).not.toHaveBeenCalled();
    });

    it('should show an error and not redirect when issuing the code fails', () => {
        setup({ code_challenge: 'c', callback: CALLBACK, state: 's' });
        vi.spyOn(accountService, 'issueExternalLoginCode').mockReturnValue(throwError(() => new Error('400')));
        const errorSpy = vi.spyOn(alertService, 'error').mockReturnValue({} as any);

        component.ngOnInit();

        expect(errorSpy).toHaveBeenCalledWith('artemisApp.externalLogin.error.codeIssuanceFailed');
        expect(redirectSpy).not.toHaveBeenCalled();
        expect(component['status']()).toBe('error');
    });
});
