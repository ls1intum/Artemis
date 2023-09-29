import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';

import { SuspiciousSessionsService } from 'app/exam/manage/suspicious-behavior/suspicious-sessions.service';
import { SuspiciousExamSessions, SuspiciousSessionReason, SuspiciousSessionsAnalysisOptions } from 'app/entities/exam-session.model';

describe('SuspiciousSessionsService', () => {
    let service: SuspiciousSessionsService;
    let httpMock: HttpTestingController;
    const suspiciousSessions = {
        examSessions: [
            {
                id: 1,
                ipAddress: '192.168.0.0',
                browserFingerprintHash: 'abc',
                suspiciousReasons: [
                    SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_IP_ADDRESS,
                    SuspiciousSessionReason,
                    SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_BROWSER_FINGERPRINT,
                ],
            },
            {
                id: 2,
                suspiciousReasons: [
                    SuspiciousSessionReason,
                    SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_IP_ADDRESS,
                    SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_BROWSER_FINGERPRINT,
                ],
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

    it('should make GET request to retrieve suspicious sessions', fakeAsync(() => {
        const options = new SuspiciousSessionsAnalysisOptions(true, true, true, true, false);
        service.getSuspiciousSessions(1, 2, options).subscribe((resp) => expect(resp).toEqual(suspiciousSessions));
        const req = httpMock.expectOne({
            method: 'GET',
            url: 'api/courses/1/exams/2/suspicious-sessions?differentStudentExamsSameIPAddress=true&differentStudentExamsSameBrowserFingerprint=true&sameStudentExamDifferentIPAddresses=true&sameStudentExamDifferentBrowserFingerprints=true&ipOutsideOfRange=false',
        });
        req.flush(suspiciousSessions);
        tick();
    }));

    it('should make GET request to retrieve suspicious sessions with subnet', fakeAsync(() => {
        const options = new SuspiciousSessionsAnalysisOptions(true, true, true, true, true, '127.0.0.1/28');
        service.getSuspiciousSessions(1, 2, options).subscribe((resp) => expect(resp).toEqual(suspiciousSessions));
        const req = httpMock.expectOne({
            method: 'GET',
            url: 'api/courses/1/exams/2/suspicious-sessions?differentStudentExamsSameIPAddress=true&differentStudentExamsSameBrowserFingerprint=true&sameStudentExamDifferentIPAddresses=true&sameStudentExamDifferentBrowserFingerprints=true&ipOutsideOfRange=true&ipSubnet=127.0.0.1/28',
        });
        req.flush(suspiciousSessions);
        tick();
    }));
});
