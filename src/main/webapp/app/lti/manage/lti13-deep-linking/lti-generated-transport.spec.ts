import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LtiApi } from 'app/openapi/api/lti-api';

describe('LTI generated deep linking transport', () => {
    let api: LtiApi;
    let http: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        api = TestBed.inject(LtiApi);
        http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => http.verify());

    it('sends repeated content ids and returns the target URI', () => {
        api.lti13DeepLinking(123, 'LECTURE', 'a token', 'registration', [1, 2]).subscribe((response) => {
            expect(response.targetLinkUri).toBe('https://platform.example/link');
        });
        const request = http.expectOne((req) => req.url.startsWith('/api/lti/lti13/courses/123/deep-linking'));
        expect(request.request.method).toBe('POST');
        const params = new URL(request.request.url, 'http://localhost').searchParams;
        expect(params.get('resourceType')).toBe('LECTURE');
        expect(params.getAll('contentIds')).toEqual(['1', '2']);
        expect(params.get('ltiIdToken')).toBe('a token');
        expect(params.get('clientRegistrationId')).toBe('registration');
        request.flush({ targetLinkUri: 'https://platform.example/link' });
    });

    it('omits content ids for competency links and propagates errors', () => {
        api.lti13DeepLinking(123, 'COMPETENCY', '', '').subscribe({
            next: () => {
                throw new Error('expected request to fail');
            },
            error: (error) => expect(error.status).toBe(403),
        });
        const request = http.expectOne((req) => req.url.startsWith('/api/lti/lti13/courses/123/deep-linking'));
        expect(new URL(request.request.url, 'http://localhost').searchParams.has('contentIds')).toBe(false);
        request.flush(null, { status: 403, statusText: 'Forbidden' });
    });
});
