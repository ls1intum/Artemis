import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { SuspiciousSessionsOverviewComponent } from 'app/exam/manage/suspicious-behavior/suspicious-sessions-overview/suspicious-sessions-overview.component';
import { SuspiciousExamSessions, SuspiciousSessionReason } from 'app/entities/exam-session.model';
import { ArtemisTestModule } from '../../../../test.module';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { SuspiciousSessionsComponent } from 'app/exam/manage/suspicious-behavior/suspicious-sessions/suspicious-sessions.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('SuspiciousSessionsOverviewComponent', () => {
    const suspiciousSessions = {
        examSessions: [
            {
                id: 1,
                ipAddress: '192.168.0.0',
                suspiciousReasons: [SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_IP_ADDRESS, SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_BROWSER_FINGERPRINT],
            },
            {
                id: 2,
                suspiciousReasons: [SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_IP_ADDRESS, SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_BROWSER_FINGERPRINT],
                ipAddress: '192.168.0.0',
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
            },
            {
                id: 2,
                ipAddress: '127.0.0.2',
                browserFingerprintHash: 'def',
                suspiciousReasons: [SuspiciousSessionReason.SAME_STUDENT_EXAM_DIFFERENT_IP_ADDRESSES, SuspiciousSessionReason.SAME_STUDENT_EXAM_DIFFERENT_BROWSER_FINGERPRINTS],
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
            },
        ],
    } as SuspiciousExamSessions;
    let component: SuspiciousSessionsOverviewComponent;
    let fixture: ComponentFixture<SuspiciousSessionsOverviewComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [SuspiciousSessionsOverviewComponent, MockComponent(SuspiciousSessionsComponent), MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe)],
        });
        history.pushState({ suspiciousSessions: [suspiciousSessions, suspiciousSessions2, suspiciousSessions3] }, '');

        fixture = TestBed.createComponent(SuspiciousSessionsOverviewComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should retrieve suspicious sessions onInit', fakeAsync(() => {
        component.ngOnInit();
        expect(component.suspiciousSessions).toEqual([suspiciousSessions, suspiciousSessions2, suspiciousSessions3]);
    }));
});
