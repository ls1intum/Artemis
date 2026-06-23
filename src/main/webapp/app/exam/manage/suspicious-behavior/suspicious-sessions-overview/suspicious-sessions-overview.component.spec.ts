import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { SuspiciousSessionsOverviewComponent } from 'app/exam/manage/suspicious-behavior/suspicious-sessions-overview/suspicious-sessions-overview.component';
import { SuspiciousExamSessions, SuspiciousSessionReason } from 'app/exam/shared/entities/exam-session.model';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { SuspiciousSessionsComponent } from 'app/exam/manage/suspicious-behavior/suspicious-sessions/suspicious-sessions.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('SuspiciousSessionsOverviewComponent', () => {
    setupTestBed({ zoneless: true });

    const stubStudentExam = { id: 1, exam: { id: 1, course: { id: 1 } }, user: { login: 'tester' } } as any;

    const suspiciousSessions = {
        examSessions: [
            {
                id: 1,
                ipAddress: '192.168.0.0',
                suspiciousReasons: [SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_IP_ADDRESS, SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_BROWSER_FINGERPRINT],
                studentExam: stubStudentExam,
            },
            {
                id: 2,
                suspiciousReasons: [SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_IP_ADDRESS, SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_BROWSER_FINGERPRINT],
                ipAddress: '192.168.0.0',
                studentExam: stubStudentExam,
            },
        ],
    } as SuspiciousExamSessions;

    const suspiciousSessions2 = {
        examSessions: [
            {
                id: 1,
                ipAddress: '127.0.0.1',
                browserFingerprintHash: 'abc',
                suspiciousReasons: [SuspiciousSessionReason.SAME_STUDENT_EXAM_DIFFERENT_IP_ADDRESSES, SuspiciousSessionReason.SAME_STUDENT_EXAM_DIFFERENT_BROWSER_FINGERPRINTS],
                studentExam: stubStudentExam,
            },
            {
                id: 2,
                ipAddress: '127.0.0.2',
                browserFingerprintHash: 'def',
                suspiciousReasons: [SuspiciousSessionReason.SAME_STUDENT_EXAM_DIFFERENT_IP_ADDRESSES, SuspiciousSessionReason.SAME_STUDENT_EXAM_DIFFERENT_BROWSER_FINGERPRINTS],
                studentExam: stubStudentExam,
            },
        ],
    } as SuspiciousExamSessions;

    const suspiciousSessions3 = {
        examSessions: [
            {
                id: 1,
                ipAddress: '127.0.0.1',
                browserFingerprintHash: 'abc',
                suspiciousReasons: [SuspiciousSessionReason.IP_ADDRESS_OUTSIDE_OF_RANGE],
                studentExam: stubStudentExam,
            },
        ],
    } as SuspiciousExamSessions;
    let component: SuspiciousSessionsOverviewComponent;
    let fixture: ComponentFixture<SuspiciousSessionsOverviewComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [SuspiciousSessionsOverviewComponent, MockComponent(SuspiciousSessionsComponent), MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({}) } } },
            ],
        });
        history.pushState({ suspiciousSessions: [suspiciousSessions, suspiciousSessions2, suspiciousSessions3] }, '');

        fixture = TestBed.createComponent(SuspiciousSessionsOverviewComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should retrieve suspicious sessions onInit', async () => {
        component.ngOnInit();
        expect(component.suspiciousSessions()).toEqual([suspiciousSessions, suspiciousSessions2, suspiciousSessions3]);
    });
});
