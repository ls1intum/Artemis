/**
 * Vitest tests for ConfirmEmailChangeService.
 */
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ConfirmEmailChangeResponse, ConfirmEmailChangeService } from 'app/account/confirm-email-change/confirm-email-change.service';
import { SKIP_HTTP_ERROR_ALERT } from 'app/core/interceptor/errorhandler.interceptor';

describe('ConfirmEmailChangeService', () => {
    let service: ConfirmEmailChangeService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(ConfirmEmailChangeService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should post the key to the public confirm endpoint', () => {
        let result: ConfirmEmailChangeResponse | undefined;

        service.confirmEmailChange('key-id', 'key-secret').subscribe((response) => (result = response));

        const req = httpMock.expectOne({ method: 'POST', url: 'api/core/public/account/confirm-email-change' });
        expect(req.request.body).toEqual({ keyId: 'key-id', keySecret: 'key-secret' });
        // the page shows failures itself
        expect(req.request.context.get(SKIP_HTTP_ERROR_ALERT)).toBe(true);
        req.flush({ completed: true, address: 'NEW' });
        expect(result).toEqual({ completed: true, address: 'NEW' });
    });

    it('should return an error for an invalid key', () => {
        let status: number | undefined;

        service.confirmEmailChange('key-id', 'wrong').subscribe({ error: (error) => (status = error.status) });

        httpMock
            .expectOne({ method: 'POST', url: 'api/core/public/account/confirm-email-change' })
            .flush({ errorKey: 'invalidEmailChangeKey' }, { status: 400, statusText: 'Bad Request' });
        expect(status).toBe(400);
    });
});
