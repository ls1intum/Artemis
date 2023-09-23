import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';

import { SuspiciousSessionsService } from 'app/exam/manage/suspicious-behavior/suspicious-sessions.service';
import { SuspiciousExamSessions, SuspiciousSessionReason } from 'app/entities/exam-session.model';

describe('SuspiciousSessionsService', () => {
    let service: SuspiciousSessionsService;
    let httpMock: HttpTestingController;
    const suspiciousSessions = {
        examSessions: [
            {
                id: 1,
                ipAddress: '192.168.0.0',
                browserFingerprintHash: 'abc',
                suspiciousReasons: [SuspiciousSessionReason.SAME_IP_ADDRESS, SuspiciousSessionReason, SuspiciousSessionReason.SAME_BROWSER_FINGERPRINT],
            },
            {
                id: 2,
                suspiciousReasons: [SuspiciousSessionReason, SuspiciousSessionReason.SAME_IP_ADDRESS, SuspiciousSessionReason.SAME_BROWSER_FINGERPRINT],
                ipAddress: '192.168.0.0',
                browserFingerprintHash: 'abc',
            },
        ],
    } as SuspiciousExamSessions;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        service = TestBed.inject(SuspiciousSessionsService);
        httpMock = TestBed.inject(HttpTestingController);
    });
    it('should make GET request to retrieve suspicious submissions', fakeAsync(() => {
        service.getSuspiciousSessions(1, 2).subscribe((resp) => expect(resp).toEqual(suspiciousSessions));
        const req = httpMock.expectOne({ method: 'GET', url: 'api/courses/1/exams/2/suspicious-sessions' });
        req.flush(suspiciousSessions);
        tick();
    }));
});
