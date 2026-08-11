import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Observable, of, throwError } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { CredentialRevocationSettingsComponent } from 'app/account/user/settings/credential-revocation-settings/credential-revocation-settings.component';
import { CredentialRevocationService } from 'app/account/user/settings/credential-revocation-settings/credential-revocation.service';

describe('CredentialRevocationSettingsComponent', () => {
    let component: CredentialRevocationSettingsComponent;
    let fixture: ComponentFixture<CredentialRevocationSettingsComponent>;
    let credentialRevocationService: CredentialRevocationService;
    let alertService: AlertService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CredentialRevocationSettingsComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideHttpClient(), provideHttpClientTesting()],
        })
            .overrideTemplate(CredentialRevocationSettingsComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(CredentialRevocationSettingsComponent);
        component = fixture.componentInstance;
        credentialRevocationService = TestBed.inject(CredentialRevocationService);
        alertService = TestBed.inject(AlertService);
    });

    it('should select nothing by default', () => {
        // Each option costs the user something real, so the page must not arrive with any of them armed.
        expect(component.revokePasskeys()).toBe(false);
        expect(component.revokeSshKeys()).toBe(false);
        expect(component.revokeVcsAccessTokens()).toBe(false);
        expect(component.hasSelection()).toBe(false);
    });

    it.each([{ option: 'revokePasskeys' as const }, { option: 'revokeSshKeys' as const }, { option: 'revokeVcsAccessTokens' as const }])(
        'should offer the action once $option is selected',
        ({ option }) => {
            component[option].set(true);
            expect(component.hasSelection()).toBe(true);
        },
    );

    it('should send exactly the selected credential types', () => {
        const revokeSpy = vi.spyOn(credentialRevocationService, 'revokeCredentials').mockReturnValue(of(undefined));
        component.revokeSshKeys.set(true);

        component.revokeSelectedCredentials();

        expect(revokeSpy).toHaveBeenCalledExactlyOnceWith({ passkeys: false, sshKeys: true, vcsAccessTokens: false });
    });

    it('should clear the selection after a successful revocation', () => {
        vi.spyOn(credentialRevocationService, 'revokeCredentials').mockReturnValue(of(undefined));
        const successSpy = vi.spyOn(alertService, 'success');
        component.revokePasskeys.set(true);
        component.revokeSshKeys.set(true);
        component.revokeVcsAccessTokens.set(true);

        component.revokeSelectedCredentials();

        // Leaving the boxes ticked would let a second click fire the same destructive request against nothing.
        expect(component.revokePasskeys()).toBe(false);
        expect(component.revokeSshKeys()).toBe(false);
        expect(component.revokeVcsAccessTokens()).toBe(false);
        expect(component.isRevoking()).toBe(false);
        expect(successSpy).toHaveBeenCalledWith('artemisApp.userSettings.credentialRevocation.success');
    });

    it('should close the confirmation dialog on success', async () => {
        vi.spyOn(credentialRevocationService, 'revokeCredentials').mockReturnValue(of(undefined));
        component.revokePasskeys.set(true);
        const dialogError = firstValueFromNext(component.dialogError$);

        component.revokeSelectedCredentials();

        // The delete dialog closes on an empty error and stays open showing a non-empty one.
        await expect(dialogError).resolves.toBe('');
    });

    it('should keep the selection and report the error when revocation fails', async () => {
        // 400 is what this endpoint actually returns on a rejected request; onError deliberately stays silent on 500.
        const error = new HttpErrorResponse({ status: 400, statusText: 'Bad Request' });
        vi.spyOn(credentialRevocationService, 'revokeCredentials').mockReturnValue(throwError(() => error));
        const errorSpy = vi.spyOn(alertService, 'error').mockImplementation(() => undefined!);
        component.revokeVcsAccessTokens.set(true);
        const dialogError = firstValueFromNext(component.dialogError$);

        component.revokeSelectedCredentials();

        await expect(dialogError).resolves.toBe(error.message);
        // Nothing was revoked, so the user's choice has to survive for them to retry.
        expect(component.revokeVcsAccessTokens()).toBe(true);
        expect(component.isRevoking()).toBe(false);
        expect(errorSpy).toHaveBeenCalledExactlyOnceWith('error.http.400');
    });

    function firstValueFromNext<T>(source: Observable<T>): Promise<T> {
        return new Promise<T>((resolve) => {
            const subscription = source.subscribe((value) => {
                resolve(value);
                subscription.unsubscribe();
            });
        });
    }
});
