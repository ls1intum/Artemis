import { TestBed, fakeAsync, tick } from '@angular/core/testing';

import { AuditsService } from 'app/core/admin/audits/audits.service';
import { Audit } from 'app/core/admin/audits/audit.model';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

describe('Audits Service', () => {
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

    describe('Service methods', () => {
        it('should return Audits', fakeAsync(() => {
            const audit = new Audit({ remoteAddress: '127.0.0.1', sessionId: '123' }, 'user', '20140101', 'AUTHENTICATION_SUCCESS');

            service.query({}).subscribe((received) => {
                expect(received.body![0]).toEqual(audit);
            });

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush([audit]);
            tick();
        }));

        it('should propagate not found response', fakeAsync(() => {
            service.query({}).subscribe({
                error: (_error: any) => {
                    expect(_error.status).toBe(404);
                },
            });

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush('Invalid request parameters', {
                status: 404,
                statusText: 'Bad Request',
            });
            tick();
        }));
    });
});
