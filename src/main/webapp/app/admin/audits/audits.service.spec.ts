/**
 * Vitest tests for AuditsService.
 */
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { AuditsService } from 'app/admin/audits/audits.service';
import { Audit } from 'app/admin/audits/audit.model';
import { AuditLogType } from 'app/admin/audits/audit-log-type.model';

describe('AuditsService', () => {
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
        expect(req.request.url).toBe('api/admin/audits');
        req.flush([audit]);
    });

    it('should send the searched login as the principal, next to the other parameters', () => {
        service
            .query({ logType: AuditLogType.SECURITY, principal: 'student1', fromDate: '2026-10-24', toDate: '2026-11-25', page: 0, size: 20, sort: ['auditEventDate,desc'] })
            .subscribe();

        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.params.get('principal')).toBe('student1');
        expect(req.request.params.get('logType')).toBe('SECURITY');
        expect(req.request.params.get('fromDate')).toBe('2026-10-24');
        expect(req.request.params.get('toDate')).toBe('2026-11-25');
        req.flush([]);
    });

    it('should not send a principal when none is searched', () => {
        service.query({ logType: AuditLogType.GENERAL }).subscribe();

        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.params.has('principal')).toBe(false);
        req.flush([]);
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
