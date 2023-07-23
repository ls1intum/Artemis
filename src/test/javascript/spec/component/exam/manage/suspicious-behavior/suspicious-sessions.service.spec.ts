import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';

import { SuspiciousSessionsService } from 'app/exam/manage/suspicious-behavior/suspicious-sessions.service';

describe('SuspiciousSessionsService', () => {
    let service: SuspiciousSessionsService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        service = TestBed.inject(SuspiciousSessionsService);
        httpMock = TestBed.inject(HttpTestingController);
    });
    it('should make GET request to retrieve suspicious submissions', fakeAsync(() => {
        const suspiciousSessions = [{ examSessions: [{ id: 1 }, { id: 2 }] }, { examSessions: [{ id: 3 }, { id: 4 }] }];
        service.getSuspiciousSessions(1, 2).subscribe((resp) => expect(resp).toEqual(suspiciousSessions));
        const req = httpMock.expectOne({ method: 'GET', url: 'api/courses/1/exams/2/suspicious-sessions' });
        req.flush(suspiciousSessions);
        tick();
    }));
});
