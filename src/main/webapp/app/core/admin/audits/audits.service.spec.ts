/**
 * Vitest tests for AuditsService.
 */
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { AuditsService } from 'app/core/admin/audits/audits.service';
import { Audit } from 'app/core/admin/audits/audit.model';

describe('AuditsService', () => {
    setupTestBed({ zoneless: true });

    let service: AuditsService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(AuditsService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should return audits', () => {
        const audit = new Audit({ remoteAddress: '127.0.0.1', sessionId: '123' }, 'user', '20140101', 'AUTHENTICATION_SUCCESS');

        service.query({}).subscribe((received) => {
            expect(received.body![0]).toEqual(audit);
        });

        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.url).toBe('api/core/admin/audits');
        req.flush([audit]);
    });

    it('should propagate not found response', () => {
        service.query({}).subscribe({
            error: (error: any) => {
                expect(error.status).toBe(404);
            },
        });

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush('Invalid request parameters', {
            status: 404,
            statusText: 'Bad Request',
        });
    });
});
