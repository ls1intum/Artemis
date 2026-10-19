import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CredentialRevocationService } from 'app/account/user/settings/credential-revocation-settings/credential-revocation.service';

describe('CredentialRevocationService', () => {
    let service: CredentialRevocationService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(CredentialRevocationService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    it('should post the choice to the revocation endpoint', () => {
        const choice = { passkeys: true, sshKeys: false, vcsAccessTokens: true };
        let completed = false;

        service.revokeCredentials(choice).subscribe(() => (completed = true));

        const request = httpMock.expectOne('api/account/revoke-credentials');
        expect(request.request.method).toBe('POST');
        // Sent as the body rather than as query parameters, and unwrapped: the server binds it straight to
        // CredentialRevocationChoiceDTO.
        expect(request.request.body).toEqual(choice);

        request.flush(null);
        expect(completed).toBeTruthy();
        httpMock.verify();
    });

    it('should surface a rejected choice to the caller', () => {
        // The server answers 400 when a request selects nothing, and the page relies on that reaching its error handler.
        let status: number | undefined;

        service.revokeCredentials({ passkeys: false, sshKeys: false, vcsAccessTokens: false }).subscribe({
            error: (error) => (status = error.status),
        });

        httpMock.expectOne('api/account/revoke-credentials').flush(null, { status: 400, statusText: 'Bad Request' });

        expect(status).toBe(400);
        httpMock.verify();
    });
});
