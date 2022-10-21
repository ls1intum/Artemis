import { TestBed, tick, fakeAsync } from '@angular/core/testing';

import { AuditsService } from 'app/admin/audits/audits.service';
import { Audit } from 'app/admin/audits/audit.model';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

describe('Audits Service', () => {
    let service: AuditsService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });

        service = TestBed.inject(AuditsService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('Service methods', () => {
        it('should call correct URL', () => {
            service.query({}).subscribe(() => {});

            const req = httpMock.expectOne({ method: 'GET' });
            const resourceUrl = SERVER_API_URL + 'management/audits';
            expect(req.request.url).toEqual(resourceUrl);
        });

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
