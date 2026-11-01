import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PersonalAccessTokenApiService } from 'app/account/user/settings/agent-access-settings/personal-access-token-api.service';
import { CreatedPersonalAccessTokenDTO, PersonalAccessTokenDTO } from 'app/account/user/settings/agent-access-settings/dto/personal-access-token.dto';

const token: PersonalAccessTokenDTO = {
    id: 7,
    name: 'my agent',
    tokenPrefix: 'art_pat_AbCd',
    scopes: ['competencies:read'],
    createdDate: '2026-10-30T10:00:00Z',
    expiryDate: '2026-11-30T10:00:00Z',
};

describe('PersonalAccessTokenApiService', () => {
    let service: PersonalAccessTokenApiService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [PersonalAccessTokenApiService, provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(PersonalAccessTokenApiService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('loads the tokens of the current user', async () => {
        const promise = service.getTokens();
        const req = httpMock.expectOne('api/account/personal-access-tokens');
        expect(req.request.method).toBe('GET');
        req.flush([token]);
        expect(await promise).toEqual([token]);
    });

    it('loads the available scopes', async () => {
        const promise = service.getScopes();
        const req = httpMock.expectOne('api/account/personal-access-token-scopes');
        expect(req.request.method).toBe('GET');
        req.flush(['courses:read', 'competencies:read']);
        expect(await promise).toEqual(['courses:read', 'competencies:read']);
    });

    it('creates a token and returns its secret', async () => {
        const created: CreatedPersonalAccessTokenDTO = { token, secret: 'art_pat_secret' };
        const promise = service.createToken({ name: 'my agent', scopes: ['competencies:read'], expiresInDays: 30 });
        const req = httpMock.expectOne('api/account/personal-access-tokens');
        expect(req.request.method).toBe('POST');
        expect(req.request.body).toEqual({ name: 'my agent', scopes: ['competencies:read'], expiresInDays: 30 });
        req.flush(created);
        expect(await promise).toEqual(created);
    });

    it('revokes a token', async () => {
        const promise = service.revokeToken(7);
        const req = httpMock.expectOne('api/account/personal-access-tokens/7');
        expect(req.request.method).toBe('DELETE');
        req.flush(null);
        await promise;
    });
});
