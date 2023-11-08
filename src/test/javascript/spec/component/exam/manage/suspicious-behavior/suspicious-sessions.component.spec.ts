import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SuspiciousSessionsComponent } from 'app/exam/manage/suspicious-behavior/suspicious-sessions/suspicious-sessions.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { StudentExam } from 'app/entities/student-exam.model';
import { SuspiciousExamSessions, SuspiciousSessionReason } from 'app/entities/exam-session.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTestModule } from '../../../../test.module';

describe('SuspiciousSessionsComponent', () => {
    let component: SuspiciousSessionsComponent;
    let fixture: ComponentFixture<SuspiciousSessionsComponent>;
    const studentExam = { id: 1, exam: { id: 1, course: { id: 1 } } } as StudentExam;
    const suspiciousSessions1 = {
        examSessions: [
            {
                suspiciousReasons: [SuspiciousSessionReason.SAME_STUDENT_EXAM_DIFFERENT_IP_ADDRESSES, SuspiciousSessionReason.SAME_STUDENT_EXAM_DIFFERENT_BROWSER_FINGERPRINTS],
            },
        ],
    } as SuspiciousExamSessions;

    const suspiciousSessions2 = {
        examSessions: [
            {
                suspiciousReasons: [],
            },
        ],
    } as SuspiciousExamSessions;

    const suspiciousSessions3 = {
        examSessions: [
            {
                suspiciousReasons: [SuspiciousSessionReason.IP_ADDRESS_OUTSIDE_OF_RANGE],
            },
        ],
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [SuspiciousSessionsComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe)],
        });
        fixture = TestBed.createComponent(SuspiciousSessionsComponent);
        component = fixture.componentInstance;
        component.suspiciousSessions = suspiciousSessions1;
    });

    it('should contain correct link to student exam in table cell', () => {
        expect(component.getStudentExamLink(studentExam)).toBe('/course-management/1/exams/1/student-exams/1');
    });

    it('should correctly determine suspicious reasons', () => {
        component.suspiciousSessions = suspiciousSessions1;
        component.ngOnInit();
        expect(component.suspiciousFingerprint).toBeTrue();
        expect(component.suspiciousIpAddress).toBeTrue();

        component.suspiciousSessions = suspiciousSessions2;
        component.ngOnInit();
        expect(component.suspiciousFingerprint).toBeFalse();
        expect(component.suspiciousIpAddress).toBeFalse();
        component.suspiciousSessions = suspiciousSessions3;
        component.ngOnInit();
        expect(component.suspiciousFingerprint).toBeFalse();
        expect(component.suspiciousIpAddress).toBeTrue();
    });
});
